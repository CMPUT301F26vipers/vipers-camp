/*
 * FirestoreReservationRepository.kt
 * The real ReservationRepository: saves and loads reservations in the Firestore "reservations"
 * collection (Repository pattern).
 * Supports atomic reservation transactions with conflict checks (T-03) and saves confirmation
 * notifications (US 01.05.01).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.repository.ReserveResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Stores reservations in Firestore. */
class FirestoreReservationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ReservationRepository {

    private val collection = db.collection("reservations")
    private val campsites = db.collection("campsites")
    private val availability = db.collection("availability")
    private val lotteries = db.collection("lotteries")
    private val entries = db.collection("lotteryEntries")
    private val notifications = db.collection("notifications")

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

    override suspend fun createReservation(
        lotteryId: String,
        campsiteId: String,
        userId: String,
        nights: List<String>,
        groupSize: Int,
        now: Long,
    ): ReserveResult {
        if (nights.isEmpty()) return ReserveResult.Error("No nights selected")
        val sortedNights = nights.distinct().sorted()

        val campsiteDoc = campsites.document(campsiteId)
        val availabilityDoc = availability.document(campsiteId)
        val lotteryDoc = lotteries.document(lotteryId)
        val entryId = LotteryEntry.idFor(lotteryId, userId)
        val entryDoc = entries.document(entryId)
        val reservationDoc = collection.document()

        return try {
            db.runTransaction { tx ->
                // All reads first (Firestore requirement)
                val campsiteSnap = tx.get(campsiteDoc)
                val lotterySnap = tx.get(lotteryDoc)
                val entrySnap = tx.get(entryDoc)
                val availabilitySnap = tx.get(availabilityDoc)

                if (!campsiteSnap.exists()) {
                    return@runTransaction ReserveResult.Error("Campsite not found")
                }
                val campsite = campsiteSnap.toCampsite()
                if (campsite.removed) {
                    return@runTransaction ReserveResult.Error("Campsite is no longer available")
                }

                if (!lotterySnap.exists()) {
                    return@runTransaction ReserveResult.Error("Lottery not found")
                }
                val lottery = lotterySnap.toLottery()
                if (lottery.removed) {
                    return@runTransaction ReserveResult.Error("Lottery is no longer available")
                }

                if (campsiteId !in lottery.campsiteIds) {
                    return@runTransaction ReserveResult.Error(
                        "Campsite is not offered in this lottery",
                    )
                }
                if (sortedNights.any { it !in lottery.nightsOffered }) {
                    return@runTransaction ReserveResult.Error(
                        "Selected nights are not offered in this lottery",
                    )
                }

                if (groupSize > campsite.maxOccupancy) {
                    return@runTransaction ReserveResult.CapacityExceeded
                }

                if (!entrySnap.exists()) return@runTransaction ReserveResult.NotEligible
                val entry = entrySnap.toLotteryEntry()

                if (!entry.canReserve(lottery, now)) {
                    return@runTransaction ReserveResult.NotEligible
                }

                val openNights = if (availabilitySnap.exists()) {
                    availabilitySnap.getStringList("openNights")
                } else emptyList()
                val heldNights = if (availabilitySnap.exists()) {
                    availabilitySnap.getStringList("heldNights")
                } else emptyList()

                // Two reservations can never hold the same night (AC 4)
                if (sortedNights.any { it in heldNights || it !in openNights }) {
                    return@runTransaction ReserveResult.Conflict
                }

                val updatedHeldNights = (heldNights + sortedNights).distinct().sorted()

                // All writes after reads
                tx.set(
                    availabilityDoc,
                    mapOf(
                        "campsiteId" to campsiteId,
                        "openNights" to openNights,
                        "heldNights" to updatedHeldNights,
                    ),
                    SetOptions.merge(),
                )

                tx.update(entryDoc, "status", EntryStatus.RESERVED.name)

                val checkIn = sortedNights.first()
                val checkOut = LocalDate.parse(sortedNights.last()).plusDays(1).toString()
                val hash = (reservationDoc.id.hashCode().toLong() and 0x7FFFFFFFL)
                val reservationNumber = "R-" + (10000 + (hash % 90000)).toString()
                val totalPriceCents = campsite.totalPriceCents(sortedNights.size)

                val reservation = Reservation(
                    id = reservationDoc.id,
                    reservationNumber = reservationNumber,
                    userId = userId,
                    ownerId = campsite.ownerId,
                    campsiteId = campsite.id,
                    campsiteName = campsite.name,
                    lotteryEntryId = entry.id,
                    checkIn = checkIn,
                    checkOut = checkOut,
                    groupSize = groupSize,
                    totalPriceCents = totalPriceCents,
                    rulesAcceptedAt = now,
                    status = ReservationStatus.CONFIRMED,
                    statusChangedAt = now,
                )
                tx.set(reservationDoc, reservation.toMap())

                // Notify the entrant that their reservation is confirmed (US 01.05.01)
                val notifDoc = notifications.document("reservation_${reservationDoc.id}")
                tx.set(
                    notifDoc,
                    mapOf(
                        "id" to notifDoc.id,
                        "recipientId" to userId,
                        "senderId" to "system",
                        "campsiteId" to campsite.id,
                        "lotteryId" to lottery.id,
                        "reservationId" to reservationDoc.id,
                        "type" to NotificationType.RESERVATION_CONFIRMED.name,
                        "title" to "Reservation confirmed",
                        "message" to "${campsite.name}: $checkIn to $checkOut",
                        "read" to false,
                        "shownOnDevice" to false,
                        "createdAt" to now,
                    ),
                    SetOptions.merge(),
                )

                ReserveResult.Success(reservation)
            }.await()
        } catch (e: Exception) {
            ReserveResult.Error(e.message ?: "Failed to complete reservation")
        }
    }
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
