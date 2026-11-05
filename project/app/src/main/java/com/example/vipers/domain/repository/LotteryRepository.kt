/*
 * LotteryRepository.kt
 * What the app can do with lotteries and their waiting-list entries (Repository pattern).
 * Known issues: only the methods needed for the half-way checkpoint. Joining with limits,
 * the draw and the replacement draw run as transactions and are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry

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
}
