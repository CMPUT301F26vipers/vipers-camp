/*
 * FakeLotteryRepository.kt
 * An in-memory LotteryRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps lotteries and entries in maps instead of Firestore. */
class FakeLotteryRepository(
    initialLotteries: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {

    private val mutex = Mutex()
    private val lotteries = initialLotteries.associateBy { it.id }.toMutableMap()
    private val entries = initialEntries.associateBy { it.id }.toMutableMap()
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

    override suspend fun saveEntry(entry: LotteryEntry): Unit = mutex.withLock {
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

    override suspend fun joinWaitingList(entry: LotteryEntry): JoinWaitingListResult = mutex.withLock {
        val lottery = lotteries[entry.lotteryId]
            ?: return JoinWaitingListResult.Error("No lottery with ID ${entry.lotteryId}")

        if (lottery.removed || !lottery.canEntrantJoin()) {
            return JoinWaitingListResult.Error("Registration is closed")
        }

        val existing = entries[LotteryEntry.idFor(entry.lotteryId, entry.userId)]
        if (existing != null) {
            return if (existing.status == EntryStatus.WAITING) {
                JoinWaitingListResult.AlreadyJoined
            } else {
                JoinWaitingListResult.Error("Cannot join with existing ${existing.status} entry")
            }
        }

        if (entry.userId in lottery.waitingUserIds) {
            return JoinWaitingListResult.AlreadyJoined
        }

        if (lottery.waitlistCapacity != null && lottery.waitingUserIds.size >= lottery.waitlistCapacity) {
            return JoinWaitingListResult.WaitlistFull
        }

        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        val saved = entry.copy(id = id, status = EntryStatus.WAITING)
        entries[id] = saved
        lotteries[entry.lotteryId] = lottery.copy(
            waitingUserIds = (lottery.waitingUserIds + entry.userId).distinct()
        )
        JoinWaitingListResult.Success
    }

    override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? = mutex.withLock {
        entries[LotteryEntry.idFor(lotteryId, userId)]
    }

    override suspend fun getEntries(lotteryId: String): List<LotteryEntry> = mutex.withLock {
        entries.values.filter { it.lotteryId == lotteryId }
    }
}
