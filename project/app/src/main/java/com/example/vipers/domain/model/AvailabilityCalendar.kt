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
        val added = nightsBetween(first, last).filter { canChange(it, today) }
        return copy(openNights = (openNights + added).distinct().sorted())
    }

    /**
     * Closes every night from [first] to [last], counting both ends and in either order. Nights
     * that can't change (past or booked) stay as they are.
     */
    fun closeRange(first: String, last: String, today: LocalDate): AvailabilityCalendar {
        val removed = nightsBetween(first, last).filter { canChange(it, today) }
        return copy(openNights = openNights - removed.toSet())
    }

    /**
     * The nights from [first] to [last] (both ends, in either order) that are open and not booked,
     * in date order. The date filter (01.01.02) and the Reserve screen use this.
     */
    fun freeNights(first: String, last: String): List<String> {
        return nightsBetween(first, last).filter { isFree(it) }
    }

    // Every night from first to last, counting both ends, in date order. The two ends can come in
    // either order, e.g. when the owner taps the last night first.
    private fun nightsBetween(first: String, last: String): List<String> {
        val a = LocalDate.parse(first)
        val b = LocalDate.parse(last)
        val end = if (a.isAfter(b)) a else b
        var day = if (a.isAfter(b)) b else a
        val nights = mutableListOf<String>()
        while (!day.isAfter(end)) {
            nights.add(day.toString())
            day = day.plusDays(1)
        }
        return nights
    }
}
