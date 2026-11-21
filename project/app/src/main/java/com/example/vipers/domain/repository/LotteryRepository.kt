/*
 * LotteryRepository.kt
 * What the app can do with lotteries and their waiting-list entries (Repository pattern).
 * Known issues: only the draw and the replacement draw run as transactions and are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Result of an atomic lottery update attempt (US 02.04.06). */
sealed interface UpdateLotteryResult {
    data object Success : UpdateLotteryResult
    data class ReadOnly(val reason: String) : UpdateLotteryResult
    data class Error(val message: String) : UpdateLotteryResult
}

/** Result of an atomic waiting list join operation. */
sealed interface JoinWaitingListResult {
    data object Success : JoinWaitingListResult
    data object WaitlistFull : JoinWaitingListResult
    data object AlreadyJoined : JoinWaitingListResult
    data class Error(val message: String) : JoinWaitingListResult
}

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
     * Loads the lotteries the app should draw at time [now] (US 02.05.01b): see
     * [Lottery.isDueForDraw]. The app checks this whenever it's opened.
     */
    suspend fun getLotteriesToDraw(now: Long = System.currentTimeMillis()): List<Lottery>

    /**
     * Closes registration for [lotteryId] at [now] so the owner can draw early (US 02.05.01b). In
     * one transaction, moves registrationEnd to [now] if registration is still open, so nobody can
     * join after this. Returns true if registration is closed afterwards, or false if the lottery
     * is missing or can't be drawn now (see [Lottery.canOwnerDraw]).
     */
    suspend fun closeRegistration(lotteryId: String, now: Long = System.currentTimeMillis()): Boolean

    /**
     * Atomically updates editable fields of an existing [lottery] in a transaction (US 02.04.06).
     *
     * In one transaction, reads the latest stored lottery, enforces caller ownership ([callerId]),
     * checks edit eligibility (not drawn, removed, or closed), validates proposed fields against
     * the current waiting-list count (preventing capacity from being set below current entrants),
     * and updates only editable fields while preserving all unedited fields.
     *
     * @param lottery Proposed lottery fields to update.
     * @param callerId The device ID of the authenticated user performing the edit.
     * @param now Current timestamp in milliseconds used for edit eligibility and date boundaries.
     * @param zone Timezone used to evaluate date boundaries.
     * @return [UpdateLotteryResult.Success] on success, [UpdateLotteryResult.ReadOnly] when the lottery
     *         can no longer be edited, or [UpdateLotteryResult.Error] with a user-facing error message.
     */
    suspend fun updateLottery(
        lottery: Lottery,
        callerId: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): UpdateLotteryResult

    /**
     * Atomically adds an entrant to the lottery waiting list.
     * Enforces that the lottery has not been drawn, registration is open, waitlist capacity is not exceeded,
     * prevents duplicate entries, and increments entriesVersion.
     */
    suspend fun joinWaitingList(entry: LotteryEntry, now: Long = System.currentTimeMillis()): JoinWaitingListResult

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

    /**
     * Declines [userId]'s win in [lotteryId] (US 01.04.03): in one transaction, sets the entry to
     * [EntryStatus.DECLINED] only if it is still [EntryStatus.WON], and changes no other field.
     * Returns true if it changed the entry, or false if the entry is missing or has any other
     * status (e.g. already DECLINED or RESERVED), so two quick taps can't decline twice.
     */
    suspend fun declineTransaction(lotteryId: String, userId: String): Boolean

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

    /**
     * Every entry in [lotteryId], sent again each time an entry is added, changed or removed, so a
     * screen can show the waiting list live (US 02.09.01). The default sends the list once, so test
     * doubles that don't need live updates still work; the Firestore and fake versions stay live.
     */
    fun observeEntries(lotteryId: String): Flow<List<LotteryEntry>> = flow {
        emit(getEntries(lotteryId))
    }
}
