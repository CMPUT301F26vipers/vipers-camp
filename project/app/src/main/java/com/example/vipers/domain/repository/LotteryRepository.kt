/*
 * LotteryRepository.kt
 * What the app can do with lotteries and their waiting-list entries (Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry

/** Result of an atomic draw transaction attempt. */
sealed class DrawTransactionResult {
    /** Transaction successfully committed the winner manifest for this draw. */
    data class Committed(val lottery: Lottery) : DrawTransactionResult()

    /** Lottery was already drawn previously; returns the committed lottery and its manifest ID. */
    data class AlreadyDrawn(val lottery: Lottery) : DrawTransactionResult()

    /** Waiting-list membership version changed during snapshot preparation; caller must re-query. */
    object VersionMismatch : DrawTransactionResult()

    /** Validation or state error (e.g. registration still open or lottery not found). */
    data class Error(val message: String) : DrawTransactionResult()
}

/** Saves and loads [Lottery]s and their [LotteryEntry]s. */
interface LotteryRepository {

    /** Saves a new [lottery] and returns the ID it was given. */
    suspend fun addLottery(lottery: Lottery): String

    /** Loads the lottery with this [id], or returns null if there is none. */
    suspend fun getLottery(id: String): Lottery?

    /** Loads the lotteries that cover [campsiteId] and haven't been removed. */
    suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery>

    /**
     * Atomically adds an entrant to the lottery waiting list.
     * Enforces that the lottery has not been drawn, registration is open, and increments entriesVersion.
     */
    suspend fun joinWaitingList(entry: LotteryEntry, now: Long = System.currentTimeMillis())

    /**
     * Atomically removes an entrant from the waiting list before the draw runs.
     * Enforces that the lottery has not been drawn, entry is WAITING, and increments entriesVersion.
     */
    suspend fun leaveWaitingList(lotteryId: String, userId: String)

    /**
     * Updates an existing entry's lifecycle state (RESERVED, DECLINED, EXPIRED).
     * Rejects transitions from WAITING to any other status, and from any status to WAITING. Does not increment entriesVersion.
     */
    suspend fun updateEntry(entry: LotteryEntry)

    /** Directly seeds an entry without transactional lifecycle validation. For tests and SeedLoader only. */
    suspend fun seedEntry(entry: LotteryEntry)

    /** Loads [userId]'s entry in [lotteryId], or returns null if they haven't joined. */
    suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry?

    /** Loads every entry in [lotteryId]. */
    suspend fun getEntries(lotteryId: String): List<LotteryEntry>

    /** Stages manifest chunks for a candidate draw attempt before committing the parent transaction. */
    suspend fun stageManifestChunks(lotteryId: String, chunks: List<DrawManifestChunk>)

    /** Loads the manifest chunks associated with [drawId] for [lotteryId]. */
    suspend fun loadManifestChunks(lotteryId: String, drawId: String): List<DrawManifestChunk>

    /**
     * Atomically commits the draw manifest to the parent lottery document.
     * Checks that drawnAt is null, entriesVersion matches [expectedVersion], and now > registrationEnd.
     */
    suspend fun runDrawTransaction(
        lotteryId: String,
        candidateDrawId: String,
        expectedVersion: Long,
        chunkCount: Int,
        participantCount: Int,
        winnerCount: Int,
        drawnAt: Long,
        now: Long = System.currentTimeMillis(),
    ): DrawTransactionResult

    /**
     * Authoritatively reconciles WAITING entry statuses against the committed draw manifest.
     * Validates manifest integrity and updates only eligible WAITING participants to WON or LOST.
     * Preserves progressed or terminal statuses (RESERVED, DECLINED, EXPIRED, REMOVED).
     */
    suspend fun reconcileEntryStatuses(lotteryId: String, committedDrawId: String)
}
