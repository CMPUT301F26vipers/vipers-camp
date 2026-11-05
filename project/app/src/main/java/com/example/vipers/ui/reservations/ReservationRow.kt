/*
 * ReservationRow.kt
 * One row of the owner's active reservations list (US 02.07.01).
 * Known issues: none. Built when the list loads and never stored, so entrantName can be the
 * entrant's current name rather than a copy.
 */
package com.example.vipers.ui.reservations

/**
 * What the owner's list shows for one active reservation (US 02.07.01).
 *
 * @property reservationId The reservation's Firestore document ID.
 * @property entrantName The entrant's current name, from ProfileRepository.getDisplayName.
 * @property campsiteName The campsite's name, from Reservation.campsiteName.
 * @property checkIn First night, as a "yyyy-MM-dd" string.
 * @property checkOut Day the stay ends, as a "yyyy-MM-dd" string.
 * @property groupSize Number of guests.
 */
data class ReservationRow(
    val reservationId: String,
    val entrantName: String,
    val campsiteName: String,
    val checkIn: String,
    val checkOut: String,
    val groupSize: Int,
)
