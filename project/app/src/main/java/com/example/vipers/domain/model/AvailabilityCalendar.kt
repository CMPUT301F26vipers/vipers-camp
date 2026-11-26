/*
 * AvailabilityCalendar.kt
 * The nights a campsite is open, and which of them are already booked.
 * Stored in the Firestore "availability" collection, one document per campsite.
 * Known issues: none. The owner changes it on the Availability screen (T-10); the checks here
 * keep past nights and booked nights from being changed.
 */
package com.example.vipers.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

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
    companion object {
        /** Days before or after a requested stay that still count for date proximity (W-01). */
        const val ADJACENT_WINDOW_DAYS = 7

        /** Days ahead from today over which upcoming free-night proximity decays to zero (W-01). */
        const val UPCOMING_WINDOW_DAYS = 60

        /** Number of requested nights, including both endpoints, or null outside 1–365 nights. */
        fun requestedNightCount(first: LocalDate, last: LocalDate): Int? {
            val nights = kotlin.math.abs(first.toEpochDay() - last.toEpochDay()) + 1
            return if (nights <= 365) nights.toInt() else null
        }
    }

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

    /**
     * Scores how close this campsite's free nights are to the entrant's dates, from 0.0 to 1.0
     * (W-01).
     *
     * When [first] and [last] are given (in either order), full coverage of the requested range
     * returns 1.0 and zero free nights in the range returns 0.0. Partial matches combine how close
     * the earliest free night in the range is to the requested start date (half) and how close
     * the nearest non-past free night within [ADJACENT_WINDOW_DAYS] days outside the range is
     * (half).
     *
     * When neither date is given, returns 1.0 if a night is free on [today], falling linearly to
     * 0.0 at [UPCOMING_WINDOW_DAYS] days away or when no upcoming night is free.
     */
    fun dateProximity(
        first: String? = null,
        last: String? = null,
        today: LocalDate = LocalDate.now(),
    ): Double {
        val firstDate = first?.trim()?.takeIf { it.isNotEmpty() }
        val lastDate = last?.trim()?.takeIf { it.isNotEmpty() }
        val startText = firstDate ?: lastDate
        val endText = lastDate ?: firstDate
        if (startText == null || endText == null) return upcomingProximity(today)

        val a = parseDate(startText) ?: return 0.0
        val b = parseDate(endText) ?: return 0.0
        val start = if (a.isAfter(b)) b else a
        val end = if (a.isAfter(b)) a else b
        val requested = requestedNightCount(start, end) ?: return 0.0

        val freeInRange = freeNights(start.toString(), end.toString())
        if (freeInRange.isEmpty()) return 0.0
        if (freeInRange.size >= requested) return 1.0

        val span = requested - 1
        val earliestFree = LocalDate.parse(freeInRange.first())
        val gapFromStart = earliestFree.toEpochDay() - start.toEpochDay()
        val startCloseness = 1.0 - gapFromStart.toDouble() / span.toDouble()

        return 0.5 * startCloseness + 0.5 * closestAdjacentProximity(start, end, today)
    }

    private fun upcomingProximity(today: LocalDate): Double {
        var bestGap: Long? = null
        for (night in openNights) {
            if (isHeld(night)) continue
            val date = parseDate(night) ?: continue
            if (date.isBefore(today)) continue
            val gap = date.toEpochDay() - today.toEpochDay()
            if (bestGap == null || gap < bestGap) bestGap = gap
        }
        val gap = bestGap ?: return 0.0
        if (gap >= UPCOMING_WINDOW_DAYS) return 0.0
        return (UPCOMING_WINDOW_DAYS - gap).toDouble() / UPCOMING_WINDOW_DAYS
    }

    private fun closestAdjacentProximity(
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): Double {
        for (daysAway in 1..ADJACENT_WINDOW_DAYS) {
            val before = start.minusDays(daysAway.toLong())
            val after = end.plusDays(daysAway.toLong())
            val beforeFree = !before.isBefore(today) && isFree(before.toString())
            val afterFree = !after.isBefore(today) && isFree(after.toString())
            if (beforeFree || afterFree) {
                return (ADJACENT_WINDOW_DAYS + 1 - daysAway).toDouble() / ADJACENT_WINDOW_DAYS
            }
        }
        return 0.0
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
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
