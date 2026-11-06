/*
 * LotteryDrawer.kt
 * Runs the random draw for a lottery, following the Project Part 2 CRC card specifications.
 * Randomly picks the set number of winners from WAITING entries, writes chunked manifests,
 * commits the atomic Phase 1 draw transaction, reconciles entry statuses, and dispatches
 * idempotent notifications: the winners' through NotificationService (US 01.03.01), the losers'
 * directly until US 01.03.02 moves them there.
 * Known issues: Draw transaction not tested against real Firestore yet. Nothing in the app starts
 * a draw yet; DrawAndNotifyWorker (US 02.05.01b) will. The replacement draw after a decline
 * (US 01.04.03) isn't built.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.DrawTransactionResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.service.DrawResult
import com.example.vipers.domain.service.NotificationService
import java.util.Random
import java.util.UUID

/** Outcome of a lottery draw or recovery operation. */
sealed class LotteryDrawResult {
    /** Draw or recovery completed successfully with [winnerCount] winners from [totalCount] participants. */
    data class Success(val winnerCount: Int, val totalCount: Int) : LotteryDrawResult()

    /** Waiting list was empty; lottery was closed with 0 winners and no notifications. */
    object EmptyList : LotteryDrawResult()

    /** Draw failed or could not proceed due to [message]. */
    data class Error(val message: String) : LotteryDrawResult()
}

/**
 * Controller/service responsible for orchestrating the lottery draw and recovery.
 *
 * @param lotteryRepository Repository to access lotteries, entries, and manifests.
 * @param notificationRepository Repository to deliver idempotent inbox notifications.
 * @param campsiteRepository Repository to resolve campsite names.
 * @param random Injected [Random] instance for reproducible, testable draws.
 * @param notificationService Sends the winners' "You won" notices (US 01.03.01); by default one
 *   built from [notificationRepository] and [campsiteRepository].
 */
class LotteryDrawer(
    private val lotteryRepository: LotteryRepository,
    private val notificationRepository: NotificationRepository,
    private val campsiteRepository: CampsiteRepository,
    private val random: Random = Random(),
    private val notificationService: NotificationService =
        NotificationService(notificationRepository, campsiteRepository),
) {

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
        // The winners' notices come from NotificationService (US 01.03.01).
        notificationService.sendDrawResults(
            DrawResult(committedLottery, manifestWinners.toList(), manifestLosers.toList()),
            now,
        )

        // The losers' notices move to NotificationService with US 01.03.02.
        val (notifCampsiteId, campsiteSummary) = resolveCampsiteNotificationInfo(committedLottery)
        for (loserId in manifestLosers) {
            val notif = Notification(
                id = "draw_${lotteryId}_$loserId",
                recipientId = loserId,
                senderId = "system",
                campsiteId = notifCampsiteId,
                lotteryId = lotteryId,
                type = NotificationType.NOT_SELECTED,
                title = "Not selected this time",
                message = campsiteSummary,
                createdAt = committedLottery.drawnAt ?: now,
            )
            notificationRepository.saveNotification(notif)
        }

        return LotteryDrawResult.Success(winnerCount = manifestWinners.size, totalCount = totalParticipants)
    }

    private suspend fun resolveCampsiteNotificationInfo(lottery: Lottery): Pair<String?, String> {
        if (lottery.campsiteIds.size == 1) {
            val cid = lottery.campsiteIds[0]
            val name = campsiteRepository.getCampsite(cid)?.name ?: "Campsite"
            return Pair(cid, name)
        }
        val summary = if (!lottery.nightsDescription.isNullOrBlank()) {
            lottery.nightsDescription
        } else {
            val names = lottery.campsiteIds.mapNotNull { campsiteRepository.getCampsite(it)?.name }
            if (names.isEmpty()) "Campsites" else names.joinToString(", ")
        }
        return Pair(null, summary)
    }
}
