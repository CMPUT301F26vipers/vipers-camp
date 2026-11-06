/*
 * AvailabilityCalendar.kt
 * The nights a campsite is open, and which of them are already booked.
 * Stored in the Firestore "availability" collection, one document per campsite.
 * Known issues: none. The owner changes it on the Availability screen (T-10); the checks here
 * keep past nights and booked nights from being changed.
 */
package com.example.vipers.domain.model

import java.time.LocalDate

/**
 * The open and booked nights of one campsite. Nights are "yyyy-MM-dd" strings.
 *
 * @property campsiteId The campsite this calendar belongs to; also the document ID.
 * @property openNights Nights the owner marked as available, in date order.
 * @property heldNights Nights held by CONFIRMED reservations.
 */
data class AvailabilityCalendar(
    val campsiteId: String = "",
    val openNights: List<String> = emptyList(),
    val heldNights: List<String> = emptyList(),
) {
    /** True if the owner marked [night] as available. */
    fun isOpen(night: String): Boolean = night in openNights

    /** True if a confirmed reservation holds [night]. */
    fun isHeld(night: String): Boolean = night in heldNights

    /** True if [night] is open and nobody has reserved it, so a winner could still book it. */
    fun isFree(night: String): Boolean = isOpen(night) && !isHeld(night)

    /** True if [night] is before [today], so it can't be changed any more. */
    fun isPast(night: String, today: LocalDate): Boolean = LocalDate.parse(night).isBefore(today)

    /**
     * True if the owner may open or close [night]: it isn't before [today] and no reservation
     * holds it (a booked night stays as it is until the reservation is rejected).
     */
    fun canChange(night: String, today: LocalDate): Boolean =
        !isPast(night, today) && !isHeld(night)

    /**
     * Opens [night] if it's closed and closes it if it's open. A night that [canChange] says no
     * to is left as it is.
     */
    fun toggle(night: String, today: LocalDate): AvailabilityCalendar {
        if (!canChange(night, today)) return this
        val nights = if (isOpen(night)) openNights - night else openNights + night
        return copy(openNights = nights.sorted())
    }

    /**
     * Opens every night from [first] to [last], counting both ends and in either order, e.g. a
     * whole weekend at once. Nights that can't change (past or booked) are skipped.
     */
    fun openRange(first: String, last: String, today: LocalDate): AvailabilityCalendar {
        var start = LocalDate.parse(first)
        var end = LocalDate.parse(last)
        if (end.isBefore(start)) start = end.also { end = start }
        val added = mutableListOf<String>()
        var day = start
        while (!day.isAfter(end)) {
            val night = day.toString()
            if (canChange(night, today)) added.add(night)
            day = day.plusDays(1)
        }
        return copy(openNights = (openNights + added).distinct().sorted())
    }

    /**
     * True if there is at least one night from [first] to [last] (inclusive, in either order)
     * that the owner opened and no confirmed reservation holds (US 01.01.02).
     */
    fun hasAvailableNight(first: String, last: String): Boolean {
        if (first.isBlank() || last.isBlank()) return false
        var start = first
        var end = last
        if (end < start) {
            start = last
            end = first
        }
        return openNights.any { night -> night in start..end && isFree(night) }
    }
}
