/*
 * AvailabilityCalendar.kt
 * The nights a campsite is open, and which of them are already booked.
 * Stored in the Firestore "availability" collection, one document per campsite.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * The open and booked nights of one campsite. Nights are "yyyy-MM-dd" strings.
 *
 * @property campsiteId The campsite this calendar belongs to; also the document ID.
 * @property openNights Nights the owner marked as available.
 * @property heldNights Nights held by CONFIRMED reservations.
 */
data class AvailabilityCalendar(
    val campsiteId: String = "",
    val openNights: List<String> = emptyList(),
    val heldNights: List<String> = emptyList(),
) {
    /** The open nights nobody has booked yet, in date order. */
    fun freeNights(): List<String> = (openNights - heldNights.toSet()).sorted()

    /** True if [night] is open and not booked. */
    fun isFree(night: String): Boolean = night in openNights && night !in heldNights
}
