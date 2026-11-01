/*
 * FirestoreReservationRepository.kt
 * The real ReservationRepository: saves and loads reservations in the Firestore "reservations"
 * collection (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.ReservationRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores reservations in Firestore. */
class FirestoreReservationRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ReservationRepository {

    private val collection = db.collection("reservations")

    override suspend fun addReservation(reservation: Reservation): String {
        val doc = collection.document()
        doc.set(reservation.copy(id = doc.id).toMap()).await()
        return doc.id
    }

    override suspend fun getReservation(id: String): Reservation? {
        val snapshot = collection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toReservation() else null
    }

    override suspend fun getReservationsForUser(userId: String): List<Reservation> =
        collection
            .whereEqualTo("userId", userId)
            .get().await()
            .documents.map { it.toReservation() }

    override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
        collection
            .whereEqualTo("ownerId", ownerId)
            .get().await()
            .documents.map { it.toReservation() }
}

/** Turns a reservation into the fields saved in its Firestore document. */
fun Reservation.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "reservationNumber" to reservationNumber,
    "userId" to userId,
    "ownerId" to ownerId,
    "campsiteId" to campsiteId,
    "campsiteName" to campsiteName,
    "lotteryEntryId" to lotteryEntryId,
    "checkIn" to checkIn,
    "checkOut" to checkOut,
    "groupSize" to groupSize,
    "totalPriceCents" to totalPriceCents,
    "rulesAcceptedAt" to rulesAcceptedAt,
    "status" to status.name,
    "statusChangedAt" to statusChangedAt,
    "rejectionReason" to rejectionReason,
)

/** Builds a reservation from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toReservation(): Reservation = Reservation(
    id = id,
    reservationNumber = getString("reservationNumber") ?: "",
    userId = getString("userId") ?: "",
    ownerId = getString("ownerId") ?: "",
    campsiteId = getString("campsiteId") ?: "",
    campsiteName = getString("campsiteName") ?: "",
    lotteryEntryId = getString("lotteryEntryId") ?: "",
    checkIn = getString("checkIn") ?: "",
    checkOut = getString("checkOut") ?: "",
    groupSize = getInt("groupSize") ?: 1,
    totalPriceCents = getLong("totalPriceCents") ?: 0,
    rulesAcceptedAt = getLong("rulesAcceptedAt") ?: 0,
    status = getString("status")?.let { ReservationStatus.valueOf(it) } ?: ReservationStatus.CONFIRMED,
    statusChangedAt = getLong("statusChangedAt") ?: 0,
    rejectionReason = getString("rejectionReason"),
)
