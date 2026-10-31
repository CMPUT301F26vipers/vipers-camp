/*
 * FakeLotteryRepository.kt
 * An in-memory LotteryRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository

/** Keeps lotteries and entries in maps instead of Firestore. */
class FakeLotteryRepository(
    initialLotteries: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {

    private val lotteries = initialLotteries.associateBy { it.id }.toMutableMap()
    private val entries = initialEntries.associateBy { it.id }.toMutableMap()
    private var nextId = 1

    override suspend fun addLottery(lottery: Lottery): String {
        val id = "lottery-${nextId++}"
        lotteries[id] = lottery.copy(id = id)
        return id
    }

    override suspend fun getLottery(id: String): Lottery? = lotteries[id]

    override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> =
        lotteries.values.filter { campsiteId in it.campsiteIds && !it.removed }

    override suspend fun saveEntry(entry: LotteryEntry) {
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        entries[id] = entry.copy(id = id)
    }

    override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? =
        entries[LotteryEntry.idFor(lotteryId, userId)]

    override suspend fun getEntries(lotteryId: String): List<LotteryEntry> =
        entries.values.filter { it.lotteryId == lotteryId }
}
