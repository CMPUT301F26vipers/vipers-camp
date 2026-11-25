/*
 * FakeLotteryRepository.kt
 * An in-memory LotteryRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.validateLottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.DeclineTransactionResult
import com.example.vipers.domain.repository.DrawTransactionResult
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.UpdateLotteryResult
import com.example.vipers.domain.service.NotificationService
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps lotteries, entries, and manifests in maps instead of Firestore. */
class FakeLotteryRepository(
    initialLotteries: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
    var notificationRepository: NotificationRepository? = null,
    var campsiteRepository: CampsiteRepository? = null,
) : LotteryRepository {

    private val mutex = Mutex()
    private val lotteries = initialLotteries.associateBy { it.id }.toMutableMap()
    private val entries = initialEntries.associateBy { it.id }.toMutableMap()
    private val manifestChunks = mutableMapOf<String, MutableList<DrawManifestChunk>>()
    private var nextId = 1

    /** Every entry, sent again after each change so [observeEntries] behaves like a Firestore listener. */
    private val allEntries = MutableStateFlow(entries.values.toList())

    init {
        for (entry in initialEntries) {
            if (entry.status == EntryStatus.WAITING) {
                val lottery = lotteries[entry.lotteryId]
                if (lottery != null && entry.userId !in lottery.waitingUserIds) {
                    lotteries[entry.lotteryId] = lottery.copy(
                        waitingUserIds = (lottery.waitingUserIds + entry.userId).distinct()
                    )
                }
            }
        }
    }

    override suspend fun addLottery(lottery: Lottery): String = mutex.withLock {
        val id = "lottery-${nextId++}"
        lotteries[id] = lottery.copy(
            id = id,
            campsiteIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct(),
        )
        publish()
        id
    }

    override suspend fun getLottery(id: String): Lottery? = mutex.withLock {
        lotteries[id]
    }

    override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> = mutex.withLock {
        lotteries.values.filter { campsiteId in it.campsiteIds && !it.removed }
    }

    override suspend fun updateLottery(
        lottery: Lottery,
        callerId: String,
        now: Long,
        zone: ZoneId,
    ): UpdateLotteryResult = mutex.withLock {
        val stored = lotteries[lottery.id]
            ?: return@withLock UpdateLotteryResult.Error("Lottery not found")

        if (callerId.isBlank() || stored.ownerId != callerId) {
            return@withLock UpdateLotteryResult.Error("You do not own this lottery")
        }

        val blockedReason = stored.editBlockedReason(now)
        if (blockedReason != null) {
            return@withLock UpdateLotteryResult.ReadOnly(blockedReason)
        }

        val submittedIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
        val normalizedLottery = lottery.copy(campsiteIds = submittedIds)
        val currentWaitingCount = stored.waitingUserIds.size

        val calendarsByCampsiteId: Map<String, AvailabilityCalendar>? = campsiteRepository?.let { campsites ->
            for (cid in submittedIds) {
                val campsite = campsites.getCampsite(cid)
                if (campsite == null || campsite.removed || campsite.ownerId != stored.ownerId) {
                    return@withLock UpdateLotteryResult.Error(
                        "One or more selected campsites are no longer available or you do not own them.",
                    )
                }
            }

            val storedIds = stored.campsiteIds.filter { it.isNotBlank() }.toSet()
            val storedNights = stored.nightsOffered.toSet()
            val hasAddedNight = normalizedLottery.nightsOffered.any { it !in storedNights }
            val idsNeedingCalendars = submittedIds.filter { cid -> cid !in storedIds || hasAddedNight }
            if (idsNeedingCalendars.isNotEmpty()) {
                idsNeedingCalendars.associateWith { cid -> campsites.getAvailability(cid) }
            } else {
                null
            }
        }

        val validationErrors = validateLottery(
            lottery = normalizedLottery,
            now = now,
            storedLottery = stored,
            currentWaitingCount = currentWaitingCount,
            zone = zone,
            calendarsByCampsiteId = calendarsByCampsiteId,
        )
        if (validationErrors.isNotEmpty()) {
            return@withLock UpdateLotteryResult.Error(validationErrors.first())
        }

        val updated = stored.copy(
            campsiteIds = normalizedLottery.campsiteIds,
            nightsOffered = normalizedLottery.nightsOffered,
            nightsDescription = normalizedLottery.nightsDescription,
            registrationStart = normalizedLottery.registrationStart,
            registrationEnd = normalizedLottery.registrationEnd,
            bookingStart = normalizedLottery.bookingStart,
            bookingEnd = normalizedLottery.bookingEnd,
            winnersCount = normalizedLottery.winnersCount,
            waitlistCapacity = normalizedLottery.waitlistCapacity,
        )
        lotteries[lottery.id] = updated
        publish()
        UpdateLotteryResult.Success
    }

    override suspend fun getLotteriesToDraw(now: Long): List<Lottery> = mutex.withLock {
        lotteries.values.filter { it.isDueForDraw(now) }
    }

    override suspend fun closeRegistration(lotteryId: String, now: Long): Boolean = mutex.withLock {
        val lottery = lotteries[lotteryId] ?: return@withLock false
        if (!lottery.canOwnerDraw(now)) return@withLock false
        if (now < lottery.registrationEnd) {
            lotteries[lotteryId] = lottery.copy(registrationEnd = now)
        }
        true
    }

    override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult = mutex.withLock {
        val lottery = lotteries[entry.lotteryId]
            ?: return@withLock JoinWaitingListResult.Error("No lottery with ID ${entry.lotteryId}")

        if (lottery.removed || !lottery.canEntrantJoin(now)) {
            return@withLock JoinWaitingListResult.Error("Registration is closed")
        }

        if (lottery.drawnAt != null) {
            return@withLock JoinWaitingListResult.Error("Lottery already drawn")
        }

        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        val existing = entries[id]
        if (existing != null) {
            return@withLock if (existing.status == EntryStatus.WAITING) {
                JoinWaitingListResult.AlreadyJoined
            } else {
                JoinWaitingListResult.Error("Cannot join with existing ${existing.status} entry")
            }
        }

        if (entry.userId in lottery.waitingUserIds) {
            return@withLock JoinWaitingListResult.AlreadyJoined
        }

        if (lottery.waitlistCapacity != null && lottery.waitingUserIds.size >= lottery.waitlistCapacity) {
            return@withLock JoinWaitingListResult.WaitlistFull
        }

        val saved = entry.copy(id = id, status = EntryStatus.WAITING)
        entries[id] = saved
        publish()
        lotteries[entry.lotteryId] = lottery.copy(
            entriesVersion = lottery.entriesVersion + 1,
            waitingUserIds = (lottery.waitingUserIds + entry.userId).distinct()
        )
        JoinWaitingListResult.Success
    }

    override suspend fun leaveWaitingList(lotteryId: String, userId: String): Unit = mutex.withLock {
        val lottery = lotteries[lotteryId] ?: throw NoSuchElementException("Lottery not found")
        if (lottery.drawnAt != null) throw IllegalStateException("Lottery already drawn")

        val id = LotteryEntry.idFor(lotteryId, userId)
        val entry = entries[id] ?: throw NoSuchElementException("Entry not found")
        if (entry.status != EntryStatus.WAITING) throw IllegalStateException("Entry is not WAITING")

        entries.remove(id)
        publish()
        lotteries[lotteryId] = lottery.copy(
            entriesVersion = lottery.entriesVersion + 1,
            waitingUserIds = lottery.waitingUserIds.filter { it != userId }
        )
    }

    override suspend fun updateEntry(entry: LotteryEntry): Unit = mutex.withLock {
        if (entry.status == EntryStatus.WAITING) {
            throw IllegalArgumentException("Cannot update status to WAITING via updateEntry; use joinWaitingList")
        }
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        val existing = entries[id] ?: throw NoSuchElementException("Entry not found")
        if (existing.status == EntryStatus.WAITING) {
            throw IllegalStateException("Cannot transition entry from WAITING via updateEntry; use leaveWaitingList or draw reconciliation")
        }
        entries[id] = entry.copy(id = id)
        publish()
    }

    override suspend fun getEntriesForUser(userId: String): List<LotteryEntry> = mutex.withLock {
        entries.values.filter { it.userId == userId }
    }

    override suspend fun removeLottery(id: String): Unit = mutex.withLock {
        val lottery = lotteries[id] ?: return@withLock
        lotteries[id] = lottery.copy(removed = true)
    }

    override suspend fun removeEntry(lotteryId: String, userId: String): Boolean = mutex.withLock {
        val id = LotteryEntry.idFor(lotteryId, userId)
        val entry = entries[id]
        if (entry == null || entry.status == EntryStatus.REMOVED) return@withLock false
        entries[id] = entry.copy(status = EntryStatus.REMOVED)
        val lottery = lotteries[lotteryId]
        if (entry.status == EntryStatus.WAITING && lottery != null) {
            lotteries[lotteryId] = lottery.copy(
                entriesVersion = lottery.entriesVersion + 1,
                waitingUserIds = lottery.waitingUserIds.filter { it != userId },
            )
        }
        publish()
        true
    }

    override suspend fun declineTransaction(
        lotteryId: String,
        userId: String,
        candidateUserId: String?,
        replacementNotice: Notification?,
        now: Long,
    ): DeclineTransactionResult = mutex.withLock {
        if (candidateUserId == null) {
            require(replacementNotice == null) {
                "replacementNotice must be null when no replacement candidate is specified"
            }
        } else if (replacementNotice != null) {
            require(notificationRepository != null) {
                "notificationRepository must be configured when replacementNotice is non-null"
            }
            require(replacementNotice.id == NotificationService.replacementNoticeId(lotteryId, candidateUserId)) {
                "replacementNotice id must match replacement_<lotteryId>_<candidateUserId>"
            }
            require(replacementNotice.lotteryId == lotteryId) {
                "replacementNotice lotteryId must match $lotteryId"
            }
            require(replacementNotice.recipientId == candidateUserId) {
                "replacementNotice recipientId must match $candidateUserId"
            }
            require(replacementNotice.type == NotificationType.WON) {
                "replacementNotice type must be WON"
            }
        }

        val lottery = lotteries[lotteryId] ?: return@withLock DeclineTransactionResult.NotDeclined
        val id = LotteryEntry.idFor(lotteryId, userId)
        val entry = entries[id] ?: return@withLock DeclineTransactionResult.NotDeclined
        if (entry.lotteryId != lotteryId || !entry.canDecline(lottery, now)) {
            return@withLock DeclineTransactionResult.NotDeclined
        }

        if (candidateUserId != null) {
            if (candidateUserId == userId) return@withLock DeclineTransactionResult.CandidateStale
            val candidate = entries[LotteryEntry.idFor(lotteryId, candidateUserId)]
            if (candidate == null || candidate.lotteryId != lotteryId || candidate.status != EntryStatus.LOST) {
                return@withLock DeclineTransactionResult.CandidateStale
            }
            // Persist notice before mutating entries so a notification write failure leaves WON/LOST intact.
            if (replacementNotice != null) {
                notificationRepository!!.addAll(listOf(replacementNotice))
            }
            entries[id] = entry.copy(status = EntryStatus.DECLINED)
            entries[candidate.id] = candidate.copy(status = EntryStatus.WON)
            publish()
            return@withLock DeclineTransactionResult.Declined(promotedUserId = candidate.userId)
        }

        entries[id] = entry.copy(status = EntryStatus.DECLINED)
        publish()
        DeclineTransactionResult.Declined(promotedUserId = null)
    }

    override suspend fun seedEntry(entry: LotteryEntry): Unit = mutex.withLock {
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        entries[id] = entry.copy(id = id)
        publish()
        val lottery = lotteries[entry.lotteryId]
        if (lottery != null) {
            val updatedWaitingIds = if (entry.status == EntryStatus.WAITING) {
                (lottery.waitingUserIds + entry.userId).distinct()
            } else {
                lottery.waitingUserIds.filter { it != entry.userId }
            }
            lotteries[entry.lotteryId] = lottery.copy(waitingUserIds = updatedWaitingIds)
        }
    }

    override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? = mutex.withLock {
        entries[LotteryEntry.idFor(lotteryId, userId)]
    }

    override suspend fun getEntries(lotteryId: String): List<LotteryEntry> = mutex.withLock {
        entries.values.filter { it.lotteryId == lotteryId }
    }

    override suspend fun stageManifestChunks(lotteryId: String, chunks: List<DrawManifestChunk>): Unit = mutex.withLock {
        if (chunks.isEmpty()) return@withLock
        val key = "${lotteryId}_${chunks[0].drawId}"
        val list = manifestChunks.getOrPut(key) { mutableListOf() }
        list.addAll(chunks)
    }

    override suspend fun loadManifestChunks(lotteryId: String, drawId: String): List<DrawManifestChunk> = mutex.withLock {
        manifestChunks["${lotteryId}_$drawId"]?.toList() ?: emptyList()
    }

    override suspend fun runDrawTransaction(
        lotteryId: String,
        candidateDrawId: String,
        expectedVersion: Long,
        chunkCount: Int,
        participantCount: Int,
        winnerCount: Int,
        drawnAt: Long,
        now: Long,
    ): DrawTransactionResult = mutex.withLock {
        val lottery = lotteries[lotteryId] ?: return@withLock DrawTransactionResult.Error("Lottery not found")
        if (lottery.drawnAt != null) return@withLock DrawTransactionResult.AlreadyDrawn(lottery)
        if (lottery.entriesVersion != expectedVersion) return@withLock DrawTransactionResult.VersionMismatch
        if (now <= lottery.registrationEnd) return@withLock DrawTransactionResult.Error("Registration is still open")

        val updated = lottery.copy(
            drawnAt = drawnAt,
            committedDrawId = candidateDrawId,
            manifestChunkCount = chunkCount,
            participantCount = participantCount,
            winnerCount = winnerCount,
        )
        lotteries[lotteryId] = updated
        DrawTransactionResult.Committed(updated)
    }

    override suspend fun reconcileEntryStatuses(lotteryId: String, committedDrawId: String): Unit = mutex.withLock {
        val lottery = lotteries[lotteryId] ?: throw NoSuchElementException("Lottery not found")
        if (lottery.drawnAt == null) throw IllegalStateException("Lottery has not been drawn")
        if (lottery.committedDrawId != committedDrawId) throw IllegalStateException("Mismatched draw ID")

        val chunks = manifestChunks["${lotteryId}_$committedDrawId"] ?: emptyList()

        if (lottery.participantCount == 0) {
            if (lottery.winnerCount != 0) {
                throw IllegalStateException("Empty draw cannot have non-zero winner count: ${lottery.winnerCount}")
            }
            if (lottery.manifestChunkCount != 0) {
                throw IllegalStateException("Empty draw cannot have manifest chunks: ${lottery.manifestChunkCount}")
            }
            if (chunks.isNotEmpty()) {
                throw IllegalStateException("Empty draw cannot have manifest chunks in database")
            }
            return@withLock
        }

        if (lottery.manifestChunkCount <= 0) {
            throw IllegalStateException("Non-empty draw must have manifest chunks: ${lottery.manifestChunkCount}")
        }

        if (chunks.isEmpty()) {
            throw IllegalStateException("No manifest chunks found for committed draw $committedDrawId")
        }
        if (chunks.size != lottery.manifestChunkCount) {
            throw IllegalStateException("Manifest chunk count mismatch: expected ${lottery.manifestChunkCount}, got ${chunks.size}")
        }

        for (chunk in chunks) {
            if (chunk.drawId != committedDrawId) {
                throw IllegalStateException("Manifest chunk has mismatched draw ID: expected $committedDrawId, got ${chunk.drawId}")
            }
            if (chunk.lotteryId != lotteryId) {
                throw IllegalStateException("Manifest chunk has mismatched lottery ID: expected $lotteryId, got ${chunk.lotteryId}")
            }
        }

        val allWinners = mutableListOf<String>()
        val allLosers = mutableListOf<String>()
        for (chunk in chunks) {
            allWinners.addAll(chunk.winnerUserIds)
            allLosers.addAll(chunk.loserUserIds)
        }
        val winnerSet = allWinners.toSet()
        val loserSet = allLosers.toSet()

        if (winnerSet.size != allWinners.size) {
            throw IllegalStateException("Duplicate winner user IDs in manifest")
        }
        if (loserSet.size != allLosers.size) {
            throw IllegalStateException("Duplicate loser user IDs in manifest")
        }
        if (winnerSet.intersect(loserSet).isNotEmpty()) {
            throw IllegalStateException("Winner and loser sets must be disjoint")
        }
        if (winnerSet.size != lottery.winnerCount) {
            throw IllegalStateException("Manifest winner count mismatch: expected ${lottery.winnerCount}, got ${winnerSet.size}")
        }
        if (winnerSet.size + loserSet.size != lottery.participantCount) {
            throw IllegalStateException("Manifest participant count mismatch: expected ${lottery.participantCount}, got ${winnerSet.size + loserSet.size}")
        }

        val currentEntries = entries.values.filter { it.lotteryId == lotteryId }
        for (entry in currentEntries) {
            if (entry.status == EntryStatus.WAITING) {
                if (entry.userId in winnerSet) {
                    entries[entry.id] = entry.copy(status = EntryStatus.WON)
                } else if (entry.userId in loserSet) {
                    entries[entry.id] = entry.copy(status = EntryStatus.LOST)
                }
            }
        }
        publish()

        val updatedWaitingUserIds = lottery.waitingUserIds.filter { it !in winnerSet && it !in loserSet }
        lotteries[lotteryId] = lottery.copy(waitingUserIds = updatedWaitingUserIds)
    }

    override fun observeEntries(lotteryId: String): Flow<List<LotteryEntry>> =
        allEntries.map { all -> all.filter { it.lotteryId == lotteryId } }

    /** Sends the current entries to [observeEntries] listeners; call after every change to [entries]. */
    private fun publish() {
        allEntries.value = entries.values.toList()
    }
}
