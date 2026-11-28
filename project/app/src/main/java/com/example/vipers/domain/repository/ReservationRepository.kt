/*
 * ReservationRepository.kt
 * What the app can do with reservations, without saying how they are stored (Repository pattern).
 * Booking nights holds them in a transaction (T-03); cancelling or rejecting gives them back in
 * one too (US 01.07.01, 02.08.01).
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus

/** Result of an atomic reservation attempt (T-03). */
sealed interface ReserveResult {
    /** Reservation successfully created and nights held. */
    data class Success(val reservation: Reservation) : ReserveResult

    /** One or more chosen nights were already booked by another winner. */
    data object Conflict : ReserveResult

    /** Entrant did not win, booking window is closed/expired, or win was already used. */
    data object NotEligible : ReserveResult

    /** Group size exceeds the campsite's maximum occupancy. */
    data object CapacityExceeded : ReserveResult

    /** General error message. */
    data class Error(val message: String) : ReserveResult
}

/** Saves and loads [Reservation]s. */
interface ReservationRepository {

    /** Saves a new [reservation] and returns the ID it was given. */
    suspend fun addReservation(reservation: Reservation): String

    /** Loads the reservation with this [id], or returns null if there is none. */
    suspend fun getReservation(id: String): Reservation?

    /** Loads every reservation made by [userId] (My Reservations). */
    suspend fun getReservationsForUser(userId: String): List<Reservation>

    /** Loads every reservation at campsites owned by [ownerId] (owner's Reservations list). */
    suspend fun getReservationsForOwner(ownerId: String): List<Reservation>

    /**
     * Atomically reserves [nights] at [campsiteId] for [userId] under [lotteryId] (T-03,
     * US 02.11.01). Enforces that the entrant has won, booking is open, groupSize <= maxOccupancy,
     * and none of the chosen nights are already held. Updates heldNights in the campsite's
     * availability, sets entry status to RESERVED, records [rulesAcceptedAt], and saves the new
     * reservation.
     */
    suspend fun createReservation(
        lotteryId: String,
        campsiteId: String,
        userId: String,
        nights: List<String>,
        groupSize: Int,
        rulesAcceptedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): ReserveResult

    /**
     * Moves a CONFIRMED reservation to [status], which is CANCELLED (by the entrant, US 01.07.01)
     * or REJECTED (by the owner, US 02.08.01), all in one transaction:
     * sets [status] and statusChangedAt to [now], saves [reason] as the rejectionReason (only for
     * REJECTED), takes the reservation's nights out of the campsite's heldNights, and writes the
     * RESERVATION_CANCELLED or RESERVATION_REJECTED notification to the entrant, unless
     * [sendNotice] is false. The lottery entry stays RESERVED.
     *
     * @param sendNotice False to skip the notification, e.g. a rejection for an entrant who
     * turned notifications off (check with NotificationService.optedIn first).
     * @return true if the status changed; false if the reservation doesn't exist or isn't
     * CONFIRMED any more (e.g. it was already cancelled on another device).
     * @throws IllegalArgumentException if [status] is CONFIRMED.
     */
    suspend fun changeStatus(
        id: String,
        status: ReservationStatus,
        reason: String? = null,
        now: Long = System.currentTimeMillis(),
        sendNotice: Boolean = true,
    ): Boolean

    /**
     * The owner rejects the reservation [reservationId] with [reason] (US 02.08.01): one
     * [changeStatus] transaction to REJECTED that saves the trimmed [reason] as the
     * rejectionReason, sets statusChangedAt to [now], gives the reservation's nights back to the
     * campsite's heldNights (openNights stays the same), and writes the RESERVATION_REJECTED
     * notification to the entrant unless [sendNotice] is false. Needs a connection, so it fails
     * offline.
     *
     * @param sendNotice False to skip the notification, for an entrant who turned notifications
     * off (check with NotificationService.optedIn first).
     * @return true if it was rejected; false if the reservation doesn't exist or isn't CONFIRMED
     * any more (e.g. the entrant cancelled it first), and then nothing changes.
     * @throws IllegalArgumentException if [reason] is blank
     * ([Reservation.isValidRejectionReason]); then nothing is saved.
     */
    suspend fun rejectTransaction(
        reservationId: String,
        reason: String,
        now: Long = System.currentTimeMillis(),
        sendNotice: Boolean = true,
    ): Boolean
}
