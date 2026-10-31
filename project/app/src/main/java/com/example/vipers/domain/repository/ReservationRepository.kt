/*
 * ReservationRepository.kt
 * What the app can do with reservations, without saying how they are stored (Repository pattern).
 * Known issues: only the methods needed for the half-way checkpoint. Booking, cancelling and
 * rejecting hold or release nights in a transaction and are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Reservation

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
}
