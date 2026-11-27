/*
 * ReservationRow.kt
 * One row of the owner's Active and Past reservation lists (US 02.07.01, 02.07.02), and the stay's
 * date and guest text that the lists and the reservation details screen both show. Also provides
 * date formatting for cancelled reservations (US 01.07.02).
 * Known issues: none. Built when the list loads and never stored, so entrantName can be the
 * entrant's current name rather than a copy.
 */
package com.example.vipers.ui.reservations

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// always English month names, like the rest of the app's text
private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * What the owner's Active and Past lists show for one reservation (US 02.07.01, 02.07.02).
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
    /** The stay as text for the UI, e.g. "May 8 – May 10, 2027" (see [formatStayDates]). */
    val datesText: String
        get() = formatStayDates(checkIn, checkOut)

    /** The group size as text for the UI, e.g. "1 guest" or "3 guests" (US 02.07.01). */
    val guestsText: String
        get() = formatGuests(groupSize)
}

/**
 * A stay from [checkIn] to [checkOut] ("yyyy-MM-dd") as text, e.g. "May 8 – May 10, 2027", with
 * the year on both sides only when the years differ (US 02.07.01). Dates that can't be read are
 * shown as saved instead of crashing the screen.
 */
fun formatStayDates(checkIn: String, checkOut: String): String =
    try {
        val start = LocalDate.parse(checkIn)
        val end = LocalDate.parse(checkOut)
        val startFormat = if (start.year == end.year) MONTH_DAY else MONTH_DAY_YEAR
        "${start.format(startFormat)} – ${end.format(MONTH_DAY_YEAR)}"
    } catch (e: DateTimeParseException) {
        "$checkIn – $checkOut"
    }

/** [groupSize] as text, e.g. "1 guest" or "3 guests" (US 02.07.01). */
fun formatGuests(groupSize: Int): String = if (groupSize == 1) "1 guest" else "$groupSize guests"

/**
 * Formats a cancellation timestamp (in milliseconds since 1970) as "MMM d, yyyy" for the
 * Cancelled on banner (US 01.07.02, mockup E15b). Falls back to today if the timestamp is
 * not positive.
 */
fun formatCancelledDate(
    timestamp: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = if (timestamp > 0L) {
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    } else {
        LocalDate.now(zone)
    }
    return date.format(MONTH_DAY_YEAR)
}

