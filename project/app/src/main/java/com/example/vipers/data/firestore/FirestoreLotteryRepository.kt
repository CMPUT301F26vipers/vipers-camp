/*
 * FirestoreLotteryRepository.kt
 * The real LotteryRepository: lotteries in the Firestore "lotteries" collection and their
 * waiting-list entries in "lotteryEntries" (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores lotteries and their entries in Firestore. */
class FirestoreLotteryRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : LotteryRepository {

    private val lotteries = db.collection("lotteries")
    private val entries = db.collection("lotteryEntries")

    override suspend fun addLottery(lottery: Lottery): String {
        val doc = lotteries.document()
        doc.set(lottery.copy(id = doc.id).toMap()).await()
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

    override suspend fun saveEntry(entry: LotteryEntry) {
        val id = LotteryEntry.idFor(entry.lotteryId, entry.userId)
        entries.document(id).set(entry.copy(id = id).toMap()).await()
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
}

/** Turns a lottery into the fields saved in its Firestore document. */
fun Lottery.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerId" to ownerId,
    "campsiteIds" to campsiteIds,
    "nightsOffered" to nightsOffered,
    "registrationStart" to registrationStart,
    "registrationEnd" to registrationEnd,
    "bookingStart" to bookingStart,
    "bookingEnd" to bookingEnd,
    "winnersCount" to winnersCount,
    "waitlistCapacity" to waitlistCapacity,
    "drawnAt" to drawnAt,
    "removed" to removed,
)

/** Builds a lottery from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toLottery(): Lottery = Lottery(
    id = id,
    ownerId = getString("ownerId") ?: "",
    campsiteIds = getStringList("campsiteIds"),
    nightsOffered = getStringList("nightsOffered"),
    registrationStart = getLong("registrationStart") ?: 0,
    registrationEnd = getLong("registrationEnd") ?: 0,
    bookingStart = getLong("bookingStart") ?: 0,
    bookingEnd = getLong("bookingEnd") ?: 0,
    winnersCount = getInt("winnersCount") ?: 1,
    waitlistCapacity = getInt("waitlistCapacity"),
    drawnAt = getLong("drawnAt"),
    removed = getBoolean("removed") ?: false,
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
