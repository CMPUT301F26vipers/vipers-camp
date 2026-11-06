/*
 * LotteryRepository.kt
 * What the app can do with lotteries and their waiting-list entries (Repository pattern).
 * Known issues: only the draw and the replacement draw run as transactions and are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Result of an atomic waiting list join operation. */
sealed interface JoinWaitingListResult {
    data object Success : JoinWaitingListResult
    data object WaitlistFull : JoinWaitingListResult
    data object AlreadyJoined : JoinWaitingListResult
    data class Error(val message: String) : JoinWaitingListResult
}

/** Saves and loads [Lottery]s and their [LotteryEntry]s. */
interface LotteryRepository {

    /** Saves a new [lottery] and returns the ID it was given. */
    suspend fun addLottery(lottery: Lottery): String

    /** Loads the lottery with this [id], or returns null if there is none. */
    suspend fun getLottery(id: String): Lottery?

    /** Loads the lotteries that cover [campsiteId] and haven't been removed. */
    suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery>

    /** Creates or updates [entry]. Its ID is "lotteryId_userId", so a user has one entry per lottery. */
    suspend fun saveEntry(entry: LotteryEntry)

    /** Loads [userId]'s entry in [lotteryId], or returns null if they haven't joined. */
    suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry?

    /** Loads every entry in [lotteryId]. */
    suspend fun getEntries(lotteryId: String): List<LotteryEntry>

    /**
     * Atomically adds [entry] to the lottery's waiting list in a transaction, enforcing
     * [Lottery.waitlistCapacity] and preventing duplicate entries.
     */
    suspend fun joinWaitingList(entry: LotteryEntry): JoinWaitingListResult

    /**
     * Every entry in [lotteryId], sent again each time an entry is added, changed or removed, so a
     * screen can show the waiting list live (US 02.09.01). The default sends the list once, so test
     * doubles that don't need live updates still work; the Firestore and fake versions stay live.
     */
    fun observeEntries(lotteryId: String): Flow<List<LotteryEntry>> = flow {
        emit(getEntries(lotteryId))
    }
}
