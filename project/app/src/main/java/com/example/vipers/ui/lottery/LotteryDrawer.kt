/*
 * LotteryDrawer.kt
 * Runs the random draw for a lottery, following the Project Part 2 CRC card specifications.
 * Randomly picks the set number of winners from WAITING entries, writes chunked manifests,
 * commits the atomic Phase 1 draw transaction, reconciles entry statuses, and has
 * NotificationService send the idempotent "You won" (US 01.03.01) and "Not selected" (US 01.03.02)
 * notices. Also handles the atomic replacement draw and recoverable replacement "You won" notice
 * when a winner declines (US 01.04.03, T-12).
 * DrawAndNotifyWorker starts the draw once registration ends, and the owner's Draw now button
 * starts it through drawNow, which can close registration early (US 02.05.01b).
 * Known issues: Draw transaction not tested against real Firestore yet.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.DrawTransactionResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.service.DrawResult
import com.example.vipers.domain.service.NotificationService
import java.util.Random
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Outcome of a lottery draw or recovery operation. */
sealed class LotteryDrawResult {
    /** Draw or recovery completed successfully with [winnerCount] winners from [totalCount] participants. */
    data class Success(val winnerCount: Int, val totalCount: Int) : LotteryDrawResult()

    /** Waiting list was empty; lottery was closed with 0 winners and no notifications. */
    object EmptyList : LotteryDrawResult()

    /** Draw failed or could not proceed due to [message]. */
    data class Error(val message: String) : LotteryDrawResult()
}

/** Outcome of declining a win and drawing a replacement winner (US 01.04.03, T-12). */
sealed class ReplacementDrawResult {
    /**
     * The win was declined atomically; [promotedUserId] became WON (or null when no LOST entrant
     * was eligible). [noticeDelivered] is true if there was no recipient or notice delivery was
     * handled (including opted-out recipients).
     */
    data class Declined(
        val promotedUserId: String?,
        val noticeDelivered: Boolean = true,
    ) : ReplacementDrawResult()

    /** The entry was no longer eligible to decline, so neither entry changed. */
    object NotDeclined : ReplacementDrawResult()
}

/**
 * Controller/service responsible for orchestrating the lottery draw and recovery.
 *
 * @param lotteryRepository Repository to access lotteries, entries, and manifests.
 * @param notificationRepository Repository to deliver idempotent inbox notifications.
 * @param campsiteRepository Repository to resolve campsite names.
 * @param profileRepository Repository that tells which entrants turned notifications off
 *   (US 01.10.01).
 * @param random Injected [Random] instance for reproducible, testable draws.
 * @param notificationService Sends the winners' "You won" (US 01.03.01) and the losers' "Not
 *   selected" (US 01.03.02) notices; by default one built from [notificationRepository],
 *   [campsiteRepository] and [profileRepository].
 */
class LotteryDrawer(
    private val lotteryRepository: LotteryRepository,
    private val notificationRepository: NotificationRepository,
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val random: Random = Random(),
    private val notificationService: NotificationService =
        NotificationService(notificationRepository, campsiteRepository, profileRepository),
) {

    /**
     * Runs the draw for [lotteryId] when the owner taps Draw now (US 02.05.01b). If registration
     * is still open it's closed at [now] first, so nobody can join once the winners are picked.
     */
    suspend fun drawNow(lotteryId: String, now: Long = System.currentTimeMillis()): LotteryDrawResult {
        if (!lotteryRepository.closeRegistration(lotteryId, now)) {
            return LotteryDrawResult.Error("This lottery can't be drawn now")
        }
        // the draw has to run after registration ends, so it runs 1 ms after the new end time
        return drawLottery(lotteryId, now + 1)
    }

    /**
     * Executes the draw for [lotteryId] at timestamp [now].
     * If the lottery is not yet drawn, performs candidate selection, manifest staging, and Phase 1B atomic commit.
     * If the lottery was already drawn (or after Phase 1B succeeds), safely reconciles entry statuses and notifications.
     */
    suspend fun drawLottery(lotteryId: String, now: Long = System.currentTimeMillis()): LotteryDrawResult {
        val initialLottery = lotteryRepository.getLottery(lotteryId)
            ?: return LotteryDrawResult.Error("Lottery not found")

        if (initialLottery.removed) {
            return LotteryDrawResult.Error("Lottery was removed")
        }

        if (now <= initialLottery.registrationEnd) {
            return LotteryDrawResult.Error("Registration is still open")
        }

        // If not yet drawn, attempt Phase 1 (up to 5 retries on version mismatch)
        if (initialLottery.drawnAt == null) {
            var committed = false
            for (attempt in 1..5) {
                val lottery = lotteryRepository.getLottery(lotteryId)
                    ?: return LotteryDrawResult.Error("Lottery not found")

                if (lottery.drawnAt != null) {
                    committed = true
                    break
                }

                val allEntries = lotteryRepository.getEntries(lotteryId)
                val eligibleWaiting = allEntries.filter { it.status == EntryStatus.WAITING }

                if (eligibleWaiting.isEmpty()) {
                    val candidateDrawId = UUID.randomUUID().toString()
                    val res = lotteryRepository.runDrawTransaction(
                        lotteryId = lotteryId,
                        candidateDrawId = candidateDrawId,
                        expectedVersion = lottery.entriesVersion,
                        chunkCount = 0,
                        participantCount = 0,
                        winnerCount = 0,
                        drawnAt = now,
                        now = now,
                    )
                    when (res) {
                        is DrawTransactionResult.Committed -> {
                            committed = true
                            break
                        }
                        is DrawTransactionResult.AlreadyDrawn -> {
                            committed = true
                            break
                        }
                        is DrawTransactionResult.VersionMismatch -> continue
                        is DrawTransactionResult.Error -> return LotteryDrawResult.Error(res.message)
                    }
                } else {
                    val candidateDrawId = UUID.randomUUID().toString()
                    val shuffled = eligibleWaiting.shuffled(random)
                    val k = minOf(lottery.winnersCount, shuffled.size)
                    val winners = shuffled.take(k)
                    val losers = shuffled.drop(k)
                    val winnerIdSet = winners.map { it.userId }.toSet()
                    val allParticipantIds = shuffled.map { it.userId }

                    val chunks = allParticipantIds.chunked(400).mapIndexed { index, chunkUserIds ->
                        DrawManifestChunk(
                            drawId = candidateDrawId,
                            lotteryId = lotteryId,
                            chunkIndex = index,
                            winnerUserIds = chunkUserIds.filter { it in winnerIdSet },
                            loserUserIds = chunkUserIds.filter { it !in winnerIdSet },
                        )
                    }

                    lotteryRepository.stageManifestChunks(lotteryId, chunks)

                    val res = lotteryRepository.runDrawTransaction(
                        lotteryId = lotteryId,
                        candidateDrawId = candidateDrawId,
                        expectedVersion = lottery.entriesVersion,
                        chunkCount = chunks.size,
                        participantCount = allParticipantIds.size,
                        winnerCount = winnerIdSet.size,
                        drawnAt = now,
                        now = now,
                    )

                    when (res) {
                        is DrawTransactionResult.Committed -> {
                            committed = true
                            break
                        }
                        is DrawTransactionResult.AlreadyDrawn -> {
                            committed = true
                            break
                        }
                        is DrawTransactionResult.VersionMismatch -> continue
                        is DrawTransactionResult.Error -> return LotteryDrawResult.Error(res.message)
                    }
                }
            }

            if (!committed) {
                val current = lotteryRepository.getLottery(lotteryId)
                if (current?.drawnAt == null) {
                    return LotteryDrawResult.Error("Exceeded retry limit due to concurrent waiting list changes")
                }
            }
        }

        // Phase 2 & 3: Reconcile statuses and notifications from the authoritative committed manifest
        val committedLottery = lotteryRepository.getLottery(lotteryId)
            ?: return LotteryDrawResult.Error("Lottery not found")

        val drawId = committedLottery.committedDrawId
        if (drawId.isNullOrBlank()) {
            return LotteryDrawResult.Error("Committed lottery has no draw ID")
        }

        // Phase 2: Authoritatively reconcile entry statuses against the committed manifest
        try {
            lotteryRepository.reconcileEntryStatuses(lotteryId, drawId)
        } catch (e: Exception) {
            return LotteryDrawResult.Error(e.message ?: "Reconciliation failed")
        }

        if (committedLottery.participantCount == 0) {
            return LotteryDrawResult.EmptyList
        }

        val chunks = lotteryRepository.loadManifestChunks(lotteryId, drawId)
        val manifestWinners = chunks.flatMap { it.winnerUserIds }.toSet()
        val manifestLosers = chunks.flatMap { it.loserUserIds }.toSet()
        val totalParticipants = manifestWinners.size + manifestLosers.size

        // Phase 3: Deliver notifications idempotently strictly to manifest participants.
        // NotificationService sends the winners' (US 01.03.01) and losers' (US 01.03.02) notices.
        notificationService.sendDrawResults(
            DrawResult(committedLottery, manifestWinners.toList(), manifestLosers.toList()),
            now,
        )

        return LotteryDrawResult.Success(winnerCount = manifestWinners.size, totalCount = totalParticipants)
    }

    /**
     * Atomically declines [decliningUserId]'s win in [lotteryId] at time [now] and promotes one
     * uniformly chosen [EntryStatus.LOST] entrant from the same lottery (US 01.04.03, T-12).
     *
     * Candidate shuffling runs outside the retried transaction using [random]. If a replacement is
     * promoted, delivers their replacement WON notice outside the transaction; if notice delivery
     * fails, the committed decline and promotion remain intact and the persisted recovery fields on
     * the declined entry allow [retryPendingReplacementNotice] to finish delivery later without
     * running another draw.
     */
    suspend fun declineAndDrawReplacement(
        lotteryId: String,
        decliningUserId: String,
        now: Long = System.currentTimeMillis(),
    ): ReplacementDrawResult {
        val candidateUserIds = lotteryRepository.getEntries(lotteryId)
            .filter { it.lotteryId == lotteryId && it.status == EntryStatus.LOST && it.userId != decliningUserId }
            .map { it.userId }
            .distinct()
            .shuffled(random)

        val txResult = lotteryRepository.declineTransaction(
            lotteryId = lotteryId,
            userId = decliningUserId,
            candidateUserIds = candidateUserIds,
            now = now,
        )
        if (!txResult.declined) return ReplacementDrawResult.NotDeclined

        val promotedUserId = txResult.promotedUserId
            ?: return ReplacementDrawResult.Declined(promotedUserId = null, noticeDelivered = true)

        val lottery = txResult.lottery ?: lotteryRepository.getLottery(lotteryId)
        val declinedEntry = txResult.declinedEntry
        if (lottery == null || declinedEntry == null) {
            return ReplacementDrawResult.Declined(promotedUserId = promotedUserId, noticeDelivered = false)
        }

        val delivered = deliverReplacementNotice(lottery, declinedEntry)
        return ReplacementDrawResult.Declined(
            promotedUserId = promotedUserId,
            noticeDelivered = delivered,
        )
    }

    /**
     * Retries delivering a pending replacement win notice recorded on [decliningUserId]'s
     * declined entry in [lotteryId] without drawing another replacement (T-12). Returns true if
     * a pending notice was delivered (or skipped due to opt-out) and marked handled.
     */
    suspend fun retryPendingReplacementNotice(lotteryId: String, decliningUserId: String): Boolean {
        val lottery = lotteryRepository.getLottery(lotteryId)?.takeIf { !it.removed } ?: return false
        val declinedEntry = lotteryRepository.getEntry(lotteryId, decliningUserId) ?: return false
        return retryPendingReplacementNotice(lottery, declinedEntry)
    }

    /**
     * Retries delivering a pending replacement win notice using the already loaded [lottery] and
     * [declinedEntry] without running another draw (T-12).
     */
    suspend fun retryPendingReplacementNotice(lottery: Lottery, declinedEntry: LotteryEntry): Boolean {
        if (
            lottery.removed ||
            declinedEntry.status != EntryStatus.DECLINED ||
            declinedEntry.replacementNotified ||
            declinedEntry.replacementUserId == null ||
            declinedEntry.replacementPromotedAt == null
        ) {
            return false
        }
        return deliverReplacementNotice(lottery, declinedEntry)
    }

    private suspend fun deliverReplacementNotice(lottery: Lottery, declinedEntry: LotteryEntry): Boolean {
        val recipientId = declinedEntry.replacementUserId ?: return true
        val promotedAt = declinedEntry.replacementPromotedAt ?: return false
        return try {
            notificationService.sendReplacementWin(lottery, recipientId, promotedAt)
            lotteryRepository.markReplacementNotified(
                lotteryId = lottery.id,
                decliningUserId = declinedEntry.userId,
                expectedReplacementUserId = recipientId,
                expectedPromotedAt = promotedAt,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
