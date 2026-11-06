/*
 * ReservationRepository.kt
 * What the app can do with reservations, without saying how they are stored (Repository pattern).
 * Booking nights holds them in a transaction (T-03).
 * Known issues: cancelling and rejecting release nights in a transaction and are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Reservation

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
     * Atomically reserves [nights] at [campsiteId] for [userId] under [lotteryId] (T-03).
     * Enforces that the entrant has won, booking is open, groupSize <= maxOccupancy,
     * and none of the chosen nights are already held.
     * Updates heldNights in the campsite's availability, sets entry status to RESERVED,
     * and saves the new reservation.
     */
    suspend fun createReservation(
        lotteryId: String,
        campsiteId: String,
        userId: String,
        nights: List<String>,
        groupSize: Int,
        now: Long = System.currentTimeMillis(),
    ): ReserveResult
}
