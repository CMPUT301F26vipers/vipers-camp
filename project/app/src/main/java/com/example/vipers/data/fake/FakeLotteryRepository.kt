/*
 * FakeLotteryRepository.kt
 * An in-memory LotteryRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.DrawTransactionResult
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps lotteries, entries, and manifests in maps instead of Firestore. */
class FakeLotteryRepository(
    initialLotteries: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {

    private val mutex = Mutex()
    private val lotteries = initialLotteries.associateBy { it.id }.toMutableMap()
    private val entries = initialEntries.associateBy { it.id }.toMutableMap()
    private val manifestChunks = mutableMapOf<String, MutableList<DrawManifestChunk>>()
    private var nextId = 1

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
        lotteries[id] = lottery.copy(id = id)
        id
    }

    override suspend fun getLottery(id: String): Lottery? = mutex.withLock {
        lotteries[id]
    }

    override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> = mutex.withLock {
        lotteries.values.filter { campsiteId in it.campsiteIds && !it.removed }
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
    }

    override suspend fun seedEntry(entry: LotteryEntry): Unit = mutex.withLock {
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        entries[id] = entry.copy(id = id)
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

        val updatedWaitingUserIds = lottery.waitingUserIds.filter { it !in winnerSet && it !in loserSet }
        lotteries[lotteryId] = lottery.copy(waitingUserIds = updatedWaitingUserIds)
    }
}
