/*
 * FakeReservationRepository.kt
 * An in-memory ReservationRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.ReservationRepository

/** Keeps reservations in a map instead of Firestore. Pass [initial] to start with some reservations. */
class FakeReservationRepository(initial: List<Reservation> = emptyList()) : ReservationRepository {

    private val reservations = initial.associateBy { it.id }.toMutableMap()
    private var nextId = 1

    override suspend fun addReservation(reservation: Reservation): String {
        val id = "reservation-${nextId++}"
        reservations[id] = reservation.copy(id = id)
        return id
    }

    override suspend fun getReservation(id: String): Reservation? = reservations[id]

    override suspend fun getReservationsForUser(userId: String): List<Reservation> =
        reservations.values.filter { it.userId == userId }

    override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
        reservations.values.filter { it.ownerId == ownerId }
}
