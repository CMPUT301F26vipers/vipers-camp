/*
 * FirestoreLotteryRepository.kt
 * The real LotteryRepository: lotteries in the Firestore "lotteries" collection and their
 * waiting-list entries in "lotteryEntries" (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 * Draw transaction not tested against real Firestore yet.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.validateLottery
import com.example.vipers.domain.repository.DeclineTransactionResult
import com.example.vipers.domain.repository.DrawTransactionResult
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.UpdateLotteryResult
import com.example.vipers.domain.service.NotificationService
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Stores lotteries and their entries in Firestore. */
class FirestoreLotteryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : LotteryRepository {

    private val lotteries = db.collection("lotteries")
    private val entries = db.collection("lotteryEntries")
    private val notifications = db.collection("notifications")
    private val campsites = db.collection("campsites")
    private val availability = db.collection("availability")

    override suspend fun addLottery(lottery: Lottery): String {
        val doc = lotteries.document()
        val normalized = lottery.copy(
            id = doc.id,
            campsiteIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct(),
        )
        doc.set(normalized.toMap()).await()
        return doc.id
    }

    override suspend fun getLottery(id: String): Lottery? {
        val snapshot = lotteries.document(id).get().await()
        return if (snapshot.exists()) snapshot.toLottery() else null
    }

    override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> =
        lotteries
            .whereArrayContains("campsiteIds", campsiteId)
            .whereEqualTo("removed", false)
            .get().await()
            .documents.map { it.toLottery() }

    override suspend fun getLotteriesToDraw(now: Long): List<Lottery> =
        // only asks for undrawn lotteries and checks the dates here, so Firestore doesn't need a
        // composite index for drawnAt and registrationEnd together
        lotteries
            .whereEqualTo("drawnAt", null)
            .get().await()
            .documents.map { it.toLottery() }
            .filter { it.isDueForDraw(now) }

    override suspend fun closeRegistration(lotteryId: String, now: Long): Boolean {
        val lotteryDoc = lotteries.document(lotteryId)
        return db.runTransaction { tx ->
            val snap = tx.get(lotteryDoc)
            if (!snap.exists()) return@runTransaction false
            val lottery = snap.toLottery()
            if (!lottery.canOwnerDraw(now)) return@runTransaction false
            if (now < lottery.registrationEnd) {
                tx.update(lotteryDoc, "registrationEnd", now)
            }
            true
        }.await()
    }

    override suspend fun updateLottery(
        lottery: Lottery,
        callerId: String,
        now: Long,
        zone: ZoneId,
    ): UpdateLotteryResult {
        val lotteryRef = lotteries.document(lottery.id)
        return try {
            db.runTransaction { tx ->
                val snapshot = tx.get(lotteryRef)
                if (!snapshot.exists()) {
                    return@runTransaction UpdateLotteryResult.Error("Lottery not found")
                }
                val stored = snapshot.toLottery()

                if (callerId.isBlank() || stored.ownerId != callerId) {
                    return@runTransaction UpdateLotteryResult.Error("You do not own this lottery")
                }

                val blockedReason = stored.editBlockedReason(now)
                if (blockedReason != null) {
                    return@runTransaction UpdateLotteryResult.ReadOnly(blockedReason)
                }

                val submittedIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
                val normalizedLottery = lottery.copy(campsiteIds = submittedIds)
                val currentWaitingCount = stored.waitingUserIds.size

                // Perform all campsite and availability reads before validation/write so Firestore
                // retries if any referenced document changes concurrently.
                for (cid in submittedIds) {
                    val campsiteSnap = tx.get(campsites.document(cid))
                    if (!campsiteSnap.exists()) {
                        return@runTransaction UpdateLotteryResult.Error(
                            "One or more selected campsites are no longer available or you do not own them.",
                        )
                    }
                    val campsite = campsiteSnap.toCampsite()
                    if (campsite.removed || campsite.ownerId != stored.ownerId) {
                        return@runTransaction UpdateLotteryResult.Error(
                            "One or more selected campsites are no longer available or you do not own them.",
                        )
                    }
                }

                val storedIds = stored.campsiteIds.filter { it.isNotBlank() }.toSet()
                val storedNights = stored.nightsOffered.toSet()
                val hasAddedNight = normalizedLottery.nightsOffered.any { it !in storedNights }
                val idsNeedingCalendars = submittedIds.filter { cid -> cid !in storedIds || hasAddedNight }
                val calendarsByCampsiteId = if (idsNeedingCalendars.isNotEmpty()) {
                    idsNeedingCalendars.associateWith { cid ->
                        val calSnap = tx.get(availability.document(cid))
                        AvailabilityCalendar(
                            campsiteId = cid,
                            openNights = calSnap.getStringList("openNights"),
                            heldNights = calSnap.getStringList("heldNights"),
                        )
                    }
                } else {
                    null
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
                    return@runTransaction UpdateLotteryResult.Error(validationErrors.first())
                }

                tx.update(lotteryRef, normalizedLottery.toEditableFields())
                UpdateLotteryResult.Success
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateLotteryResult.Error("Couldn't save. Check your connection and try again.")
        }
    }

    override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult {
        val lotteryRef = lotteries.document(entry.lotteryId)
        val entryRef = entries.document(LotteryEntry.idFor(entry.lotteryId, entry.userId))

        // Preload/initialize waitingUserIds if a legacy document in Firestore didn't have it
        val preloadedSnapshot = lotteryRef.get().await()
        if (preloadedSnapshot.exists() && preloadedSnapshot.get("waitingUserIds") == null) {
            val existingWaitingIds = entries
                .whereEqualTo("lotteryId", entry.lotteryId)
                .whereEqualTo("status", EntryStatus.WAITING.name)
                .get().await()
                .documents.mapNotNull { it.getString("userId") }
            lotteryRef.update("waitingUserIds", existingWaitingIds).await()
        }

        return db.runTransaction { transaction ->
            val lotterySnap = transaction.get(lotteryRef)
            if (!lotterySnap.exists()) {
                return@runTransaction JoinWaitingListResult.Error("No lottery with ID ${entry.lotteryId}")
            }
            val lottery = lotterySnap.toLottery()

            if (lottery.removed || !lottery.canEntrantJoin(now)) {
                return@runTransaction JoinWaitingListResult.Error("Registration is closed")
            }

            if (lottery.drawnAt != null) {
                return@runTransaction JoinWaitingListResult.Error("Lottery already drawn")
            }

            val entrySnap = transaction.get(entryRef)
            if (entrySnap.exists()) {
                val status = entrySnap.getString("status")
                return@runTransaction if (status == EntryStatus.WAITING.name) {
                    JoinWaitingListResult.AlreadyJoined
                } else {
                    JoinWaitingListResult.Error("Cannot join with existing $status entry")
                }
            }

            val currentWaitingUserIds = lotterySnap.getStringList("waitingUserIds")
            if (entry.userId in currentWaitingUserIds) {
                return@runTransaction JoinWaitingListResult.AlreadyJoined
            }

            if (lottery.waitlistCapacity != null && currentWaitingUserIds.size >= lottery.waitlistCapacity) {
                return@runTransaction JoinWaitingListResult.WaitlistFull
            }

            val updatedWaitingUserIds = (currentWaitingUserIds + entry.userId).distinct()
            transaction.update(
                lotteryRef,
                mapOf(
                    "waitingUserIds" to updatedWaitingUserIds,
                    "entriesVersion" to (lottery.entriesVersion + 1)
                )
            )
            transaction.set(entryRef, entry.copy(id = entryRef.id, status = EntryStatus.WAITING).toMap())

            JoinWaitingListResult.Success
        }.await()
    }

    override suspend fun leaveWaitingList(lotteryId: String, userId: String) {
        val lotteryDoc = lotteries.document(lotteryId)
        val entryId = LotteryEntry.idFor(lotteryId, userId)
        val entryDoc = entries.document(entryId)

        db.runTransaction { tx ->
            val lotterySnap = tx.get(lotteryDoc)
            if (!lotterySnap.exists()) throw NoSuchElementException("Lottery not found")
            val lottery = lotterySnap.toLottery()
            if (lottery.drawnAt != null) throw IllegalStateException("Lottery already drawn")

            val entrySnap = tx.get(entryDoc)
            if (!entrySnap.exists()) throw NoSuchElementException("Entry not found")
            if (entrySnap.getString("status") != EntryStatus.WAITING.name) {
                throw IllegalStateException("Entry is not WAITING")
            }

            val currentWaitingUserIds = lotterySnap.getStringList("waitingUserIds")
            val updatedWaitingUserIds = currentWaitingUserIds.filter { it != userId }

            tx.delete(entryDoc)
            tx.update(
                lotteryDoc,
                mapOf(
                    "waitingUserIds" to updatedWaitingUserIds,
                    "entriesVersion" to (lottery.entriesVersion + 1)
                )
            )
        }.await()
    }

    override suspend fun updateEntry(entry: LotteryEntry) {
        if (entry.status == EntryStatus.WAITING) {
            throw IllegalArgumentException("Cannot update status to WAITING via updateEntry; use joinWaitingList")
        }
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        val entryDoc = entries.document(id)

        db.runTransaction { tx ->
            val snap = tx.get(entryDoc)
            if (!snap.exists()) throw NoSuchElementException("Entry not found")
            val currentStatus = snap.getString("status")
            if (currentStatus == EntryStatus.WAITING.name) {
                throw IllegalStateException("Cannot transition entry from WAITING via updateEntry; use leaveWaitingList or draw reconciliation")
            }
            tx.set(entryDoc, entry.copy(id = id).toMap())
        }.await()
    }

    override suspend fun getEntriesForUser(userId: String): List<LotteryEntry> =
        entries.whereEqualTo("userId", userId).get().await().documents.map { it.toLotteryEntry() }

    override suspend fun removeEntry(lotteryId: String, userId: String): Boolean {
        val lotteryDoc = lotteries.document(lotteryId)
        val entryDoc = entries.document(LotteryEntry.idFor(lotteryId, userId))

        return db.runTransaction { tx ->
            val entrySnap = tx.get(entryDoc)
            val status = entrySnap.getString("status")
            if (!entrySnap.exists() || status == EntryStatus.REMOVED.name) {
                return@runTransaction false
            }
            val lotterySnap = tx.get(lotteryDoc)
            tx.update(entryDoc, "status", EntryStatus.REMOVED.name)
            if (status == EntryStatus.WAITING.name && lotterySnap.exists()) {
                val lottery = lotterySnap.toLottery()
                tx.update(
                    lotteryDoc,
                    mapOf(
                        "waitingUserIds" to lottery.waitingUserIds.filter { it != userId },
                        "entriesVersion" to (lottery.entriesVersion + 1),
                    )
                )
            }
            true
        }.await()
    }

    override suspend fun declineTransaction(
        lotteryId: String,
        userId: String,
        candidateUserId: String?,
        replacementNotice: Notification?,
        now: Long,
    ): DeclineTransactionResult {
        if (candidateUserId == null) {
            require(replacementNotice == null) {
                "replacementNotice must be null when no replacement candidate is specified"
            }
        } else if (replacementNotice != null) {
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

        val lotteryDoc = lotteries.document(lotteryId)
        val decliningEntryDoc = entries.document(LotteryEntry.idFor(lotteryId, userId))
        val candidateDoc = candidateUserId?.let { entries.document(LotteryEntry.idFor(lotteryId, it)) }
        val noticeDoc = replacementNotice?.let { notifications.document(it.id) }

        return db.runTransaction { tx ->
            // All reads first (Firestore requirement)
            val lotterySnap = tx.get(lotteryDoc)
            if (!lotterySnap.exists()) {
                return@runTransaction DeclineTransactionResult.NotDeclined
            }
            val lottery = lotterySnap.toLottery()

            val decliningSnap = tx.get(decliningEntryDoc)
            if (!decliningSnap.exists()) {
                return@runTransaction DeclineTransactionResult.NotDeclined
            }
            val decliningEntry = decliningSnap.toLotteryEntry()
            if (decliningEntry.lotteryId != lotteryId || !decliningEntry.canDecline(lottery, now)) {
                return@runTransaction DeclineTransactionResult.NotDeclined
            }

            if (candidateUserId != null) {
                if (candidateUserId == userId || candidateDoc == null) {
                    return@runTransaction DeclineTransactionResult.CandidateStale
                }
                val candidateSnap = tx.get(candidateDoc)
                if (!candidateSnap.exists()) {
                    return@runTransaction DeclineTransactionResult.CandidateStale
                }
                val candidateEntry = candidateSnap.toLotteryEntry()
                if (candidateEntry.lotteryId != lotteryId || candidateEntry.status != EntryStatus.LOST) {
                    return@runTransaction DeclineTransactionResult.CandidateStale
                }
            }

            val existingNoticeSnap = noticeDoc?.let { tx.get(it) }

            // Writes commit together; only the necessary fields change
            tx.update(decliningEntryDoc, "status", EntryStatus.DECLINED.name)
            if (candidateDoc != null) {
                tx.update(candidateDoc, "status", EntryStatus.WON.name)
            }
            if (replacementNotice != null && noticeDoc != null && existingNoticeSnap != null) {
                tx.set(noticeDoc, replacementNotice.keepingStateOf(existingNoticeSnap).toMap())
            }

            DeclineTransactionResult.Declined(promotedUserId = candidateUserId)
        }.await()
    }

    override suspend fun seedEntry(entry: LotteryEntry) {
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        val entryRef = entries.document(id)
        val lotteryRef = lotteries.document(entry.lotteryId)

        db.runTransaction { transaction ->
            val lotterySnap = transaction.get(lotteryRef)
            transaction.set(entryRef, entry.copy(id = id).toMap())

            if (lotterySnap.exists()) {
                val currentWaitingUserIds = lotterySnap.getStringList("waitingUserIds")
                val newWaitingUserIds = if (entry.status == EntryStatus.WAITING) {
                    (currentWaitingUserIds + entry.userId).distinct()
                } else {
                    currentWaitingUserIds.filter { it != entry.userId }
                }
                if (newWaitingUserIds != currentWaitingUserIds) {
                    transaction.update(lotteryRef, "waitingUserIds", newWaitingUserIds)
                }
            }
            null
        }.await()
    }

    override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? {
        val snapshot = entries.document(LotteryEntry.idFor(lotteryId, userId)).get().await()
        return if (snapshot.exists()) snapshot.toLotteryEntry() else null
    }

    override suspend fun getEntries(lotteryId: String): List<LotteryEntry> =
        entries
            .whereEqualTo("lotteryId", lotteryId)
            .get().await()
            .documents.map { it.toLotteryEntry() }

    override suspend fun stageManifestChunks(lotteryId: String, chunks: List<DrawManifestChunk>) {
        if (chunks.isEmpty()) return
        val manifestCollection = lotteries.document(lotteryId).collection("drawManifest")
        chunks.chunked(400).forEach { batchList ->
            val batch = db.batch()
            for (chunk in batchList) {
                val docId = "${chunk.drawId}_chunk_${chunk.chunkIndex}"
                batch.set(manifestCollection.document(docId), chunk.toMap())
            }
            batch.commit().await()
        }
    }

    override suspend fun loadManifestChunks(lotteryId: String, drawId: String): List<DrawManifestChunk> =
        lotteries
            .document(lotteryId)
            .collection("drawManifest")
            .whereEqualTo("drawId", drawId)
            .get().await()
            .documents.map { it.toDrawManifestChunk() }
            .sortedBy { it.chunkIndex }

    override suspend fun runDrawTransaction(
        lotteryId: String,
        candidateDrawId: String,
        expectedVersion: Long,
        chunkCount: Int,
        participantCount: Int,
        winnerCount: Int,
        drawnAt: Long,
        now: Long,
    ): DrawTransactionResult {
        val lotteryDoc = lotteries.document(lotteryId)
        return db.runTransaction { tx ->
            val snap = tx.get(lotteryDoc)
            if (!snap.exists()) return@runTransaction DrawTransactionResult.Error("Lottery not found")
            val current = snap.toLottery()
            if (current.drawnAt != null) return@runTransaction DrawTransactionResult.AlreadyDrawn(current)
            if (current.entriesVersion != expectedVersion) return@runTransaction DrawTransactionResult.VersionMismatch
            if (now <= current.registrationEnd) return@runTransaction DrawTransactionResult.Error("Registration is still open")

            tx.update(
                lotteryDoc,
                mapOf(
                    "drawnAt" to drawnAt,
                    "committedDrawId" to candidateDrawId,
                    "manifestChunkCount" to chunkCount,
                    "participantCount" to participantCount,
                    "winnerCount" to winnerCount,
                )
            )

            val updated = current.copy(
                drawnAt = drawnAt,
                committedDrawId = candidateDrawId,
                manifestChunkCount = chunkCount,
                participantCount = participantCount,
                winnerCount = winnerCount,
            )
            DrawTransactionResult.Committed(updated)
        }.await()
    }

    override suspend fun reconcileEntryStatuses(lotteryId: String, committedDrawId: String) {
        val lottery = getLottery(lotteryId) ?: throw NoSuchElementException("Lottery not found")
        if (lottery.drawnAt == null) throw IllegalStateException("Lottery has not been drawn")
        if (lottery.committedDrawId != committedDrawId) throw IllegalStateException("Mismatched draw ID")

        if (lottery.participantCount == 0) {
            if (lottery.winnerCount != 0) {
                throw IllegalStateException("Empty draw cannot have non-zero winner count: ${lottery.winnerCount}")
            }
            if (lottery.manifestChunkCount != 0) {
                throw IllegalStateException("Empty draw cannot have manifest chunks: ${lottery.manifestChunkCount}")
            }
            val chunks = loadManifestChunks(lotteryId, committedDrawId)
            if (chunks.isNotEmpty()) {
                throw IllegalStateException("Empty draw cannot have manifest chunks in database")
            }
            return
        }

        if (lottery.manifestChunkCount <= 0) {
            throw IllegalStateException("Non-empty draw must have manifest chunks: ${lottery.manifestChunkCount}")
        }

        val chunks = loadManifestChunks(lotteryId, committedDrawId)
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

        val currentEntries = getEntries(lotteryId)
        val updates = mutableListOf<Pair<String, EntryStatus>>()
        for (entry in currentEntries) {
            if (entry.status == EntryStatus.WAITING) {
                if (entry.userId in winnerSet) {
                    updates.add(entry.id to EntryStatus.WON)
                } else if (entry.userId in loserSet) {
                    updates.add(entry.id to EntryStatus.LOST)
                }
            }
        }

        if (updates.isNotEmpty()) {
            updates.chunked(400).forEach { batchList ->
                val batch = db.batch()
                for ((entryId, newStatus) in batchList) {
                    batch.update(entries.document(entryId), "status", newStatus.name)
                }
                batch.commit().await()
            }
        }

        val winnerOrLoserUserIds = winnerSet + loserSet
        val currentWaiting = lottery.waitingUserIds
        val newWaiting = currentWaiting.filter { it !in winnerOrLoserUserIds }
        if (newWaiting != currentWaiting) {
            lotteries.document(lotteryId).update("waitingUserIds", newWaiting).await()
        }
    }

    /** Listens to [lotteryId]'s entries with a Firestore snapshot listener; stops when collection ends. */
    override fun observeEntries(lotteryId: String): Flow<List<LotteryEntry>> = callbackFlow {
        val registration = entries
            .whereEqualTo("lotteryId", lotteryId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.toLotteryEntry() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }
}

/** Turns a lottery into the fields saved in its Firestore document. */
fun Lottery.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerId" to ownerId,
    "campsiteIds" to campsiteIds,
    "nightsOffered" to nightsOffered,
    "nightsDescription" to nightsDescription,
    "registrationStart" to registrationStart,
    "registrationEnd" to registrationEnd,
    "bookingStart" to bookingStart,
    "bookingEnd" to bookingEnd,
    "winnersCount" to winnersCount,
    "waitlistCapacity" to waitlistCapacity,
    "drawnAt" to drawnAt,
    "removed" to removed,
    "waitingUserIds" to waitingUserIds,
    "entriesVersion" to entriesVersion,
    "committedDrawId" to committedDrawId,
    "manifestChunkCount" to manifestChunkCount,
    "participantCount" to participantCount,
    "winnerCount" to winnerCount,
)

/**
 * The fields an owner can edit in a lottery (US 02.04.02, 02.04.06).
 * Leaving out id, ownerId, drawnAt, removed, waitingUserIds, entriesVersion,
 * and draw metadata preserves their saved values in Firestore.
 */
fun Lottery.toEditableFields(): Map<String, Any?> = mapOf(
    "campsiteIds" to campsiteIds.filter { it.isNotBlank() }.distinct(),
    "nightsOffered" to nightsOffered,
    "nightsDescription" to nightsDescription,
    "registrationStart" to registrationStart,
    "registrationEnd" to registrationEnd,
    "bookingStart" to bookingStart,
    "bookingEnd" to bookingEnd,
    "winnersCount" to winnersCount,
    "waitlistCapacity" to waitlistCapacity,
)

/** Builds a lottery from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toLottery(): Lottery = Lottery(
    id = id,
    ownerId = getString("ownerId") ?: "",
    campsiteIds = getStringList("campsiteIds"),
    nightsOffered = getStringList("nightsOffered"),
    nightsDescription = getString("nightsDescription"),
    registrationStart = getLong("registrationStart") ?: 0,
    registrationEnd = getLong("registrationEnd") ?: 0,
    bookingStart = getLong("bookingStart") ?: 0,
    bookingEnd = getLong("bookingEnd") ?: 0,
    winnersCount = getInt("winnersCount") ?: 1,
    waitlistCapacity = getInt("waitlistCapacity"),
    drawnAt = getLong("drawnAt"),
    removed = getBoolean("removed") ?: false,
    waitingUserIds = getStringList("waitingUserIds"),
    entriesVersion = getLong("entriesVersion") ?: 0L,
    committedDrawId = getString("committedDrawId"),
    manifestChunkCount = getInt("manifestChunkCount") ?: 0,
    participantCount = getInt("participantCount") ?: 0,
    winnerCount = getInt("winnerCount") ?: 0,
)

/** Turns a manifest chunk into the fields saved in its Firestore document. */
fun DrawManifestChunk.toMap(): Map<String, Any?> = mapOf(
    "drawId" to drawId,
    "lotteryId" to lotteryId,
    "chunkIndex" to chunkIndex,
    "winnerUserIds" to winnerUserIds,
    "loserUserIds" to loserUserIds,
)

/** Builds a manifest chunk from its Firestore document. */
fun DocumentSnapshot.toDrawManifestChunk(): DrawManifestChunk = DrawManifestChunk(
    drawId = getString("drawId") ?: "",
    lotteryId = getString("lotteryId") ?: "",
    chunkIndex = getInt("chunkIndex") ?: 0,
    winnerUserIds = getStringList("winnerUserIds"),
    loserUserIds = getStringList("loserUserIds"),
)

/** Turns an entry into the fields saved in its Firestore document. */
fun LotteryEntry.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "lotteryId" to lotteryId,
    "userId" to userId,
    "groupSize" to groupSize,
    "joinedAt" to joinedAt,
    "status" to status.name,
)

/** Builds an entry from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toLotteryEntry(): LotteryEntry = LotteryEntry(
    id = id,
    lotteryId = getString("lotteryId") ?: "",
    userId = getString("userId") ?: "",
    groupSize = getInt("groupSize") ?: 1,
    joinedAt = getLong("joinedAt") ?: 0,
    status = getString("status")?.let { EntryStatus.valueOf(it) } ?: EntryStatus.WAITING,
)
