/*
 * ReservationRow.kt
 * One row of the owner's active reservations list (US 02.07.01).
 * Known issues: none. Built when the list loads and never stored, so entrantName can be the
 * entrant's current name rather than a copy.
 */
package com.example.vipers.ui.reservations

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// always English month names, like the rest of the app's text
private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

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
) {
    /**
     * The stay as text for the UI, e.g. "May 8 – May 10, 2027", with the year on both sides only
     * when the years differ (US 02.07.01). Dates that can't be read are shown as saved, so one
     * bad document can't crash the list.
     */
    val datesText: String
        get() = try {
            val start = LocalDate.parse(checkIn)
            val end = LocalDate.parse(checkOut)
            val startFormat = if (start.year == end.year) MONTH_DAY else MONTH_DAY_YEAR
            "${start.format(startFormat)} – ${end.format(MONTH_DAY_YEAR)}"
        } catch (e: DateTimeParseException) {
            "$checkIn – $checkOut"
        }

    /** The group size as text for the UI, e.g. "1 guest" or "3 guests" (US 02.07.01). */
    val guestsText: String
        get() = if (groupSize == 1) "1 guest" else "$groupSize guests"
}
