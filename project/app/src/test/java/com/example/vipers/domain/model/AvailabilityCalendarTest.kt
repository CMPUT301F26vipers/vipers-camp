/*
 * AvailabilityCalendarTest.kt
 * Unit tests for opening and closing nights and finding free nights in AvailabilityCalendar
 * (T-10), using the May 2027 weekends from scenario 2 in the project description.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AvailabilityCalendarTest {

    // the tests run as if today is April 28, 2027
    private val today = LocalDate.parse("2027-04-28")

    // the first May weekend is open, and a reservation holds the Saturday
    private val calendar = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = listOf("2027-05-01", "2027-05-02"),
        heldNights = listOf("2027-05-01"),
    )

    @Test
    fun isFree_onlyForOpenNightsNobodyReserved() {
        assertTrue(calendar.isFree("2027-05-02"))
        assertFalse(calendar.isFree("2027-05-01")) // reserved
        assertFalse(calendar.isFree("2027-05-08")) // not open
    }

    @Test
    fun canChange_refusesPastAndBookedNights() {
        assertTrue(calendar.canChange("2027-05-08", today))
        assertTrue(calendar.canChange("2027-04-28", today)) // tonight is still allowed
        assertFalse(calendar.canChange("2027-04-27", today))
        assertFalse(calendar.canChange("2027-05-01", today))
    }

    @Test
    fun toggle_opensAClosedNightInDateOrder() {
        val opened = calendar.toggle("2027-04-30", today)
        assertEquals(listOf("2027-04-30", "2027-05-01", "2027-05-02"), opened.openNights)
    }

    @Test
    fun toggle_closesAnOpenNight() {
        assertEquals(listOf("2027-05-01"), calendar.toggle("2027-05-02", today).openNights)
    }

    @Test
    fun toggle_leavesABookedNightOpen() {
        assertSame(calendar, calendar.toggle("2027-05-01", today))
    }

    @Test
    fun toggle_leavesPastNightsAlone() {
        assertSame(calendar, calendar.toggle("2027-04-20", today))
    }

    @Test
    fun openRange_opensEveryNightIncludingBothEnds() {
        val opened = AvailabilityCalendar().openRange("2027-05-07", "2027-05-09", today)
        assertEquals(listOf("2027-05-07", "2027-05-08", "2027-05-09"), opened.openNights)
    }

    @Test
    fun openRange_worksWhenTheEndsAreTappedBackwards() {
        val opened = AvailabilityCalendar().openRange("2027-05-09", "2027-05-08", today)
        assertEquals(listOf("2027-05-08", "2027-05-09"), opened.openNights)
    }

    @Test
    fun openRange_skipsPastNightsAndKeepsOpenOnesOnce() {
        val opened = calendar.openRange("2027-04-26", "2027-05-03", today)
        assertEquals(
            listOf(
                "2027-04-28", "2027-04-29", "2027-04-30", "2027-05-01", "2027-05-02", "2027-05-03",
            ),
            opened.openNights,
        )
        // the reservation is untouched
        assertEquals(listOf("2027-05-01"), opened.heldNights)
    }

    @Test
    fun closeRange_closesEveryNightIncludingBothEnds() {
        val nights = listOf("2027-05-07", "2027-05-08", "2027-05-09", "2027-05-10")
        val open = AvailabilityCalendar(openNights = nights)
        val closed = open.closeRange("2027-05-08", "2027-05-10", today)
        assertEquals(listOf("2027-05-07"), closed.openNights)
    }

    @Test
    fun closeRange_worksWhenTheEndsAreTappedBackwards() {
        val open = AvailabilityCalendar(openNights = listOf("2027-05-08", "2027-05-09"))
        val closed = open.closeRange("2027-05-09", "2027-05-08", today)
        assertEquals(emptyList<String>(), closed.openNights)
    }

    @Test
    fun closeRange_keepsBookedAndPastNightsOpen() {
        val withPast = calendar.copy(openNights = listOf("2027-04-27") + calendar.openNights)
        val closed = withPast.closeRange("2027-04-26", "2027-05-03", today)
        // April 27 is past and the Saturday is booked; only the Sunday closes
        assertEquals(listOf("2027-04-27", "2027-05-01"), closed.openNights)
        assertEquals(listOf("2027-05-01"), closed.heldNights)
    }

    @Test
    fun freeNights_givesTheOpenNightsNobodyReserved() {
        // the Saturday is booked, so only the Sunday is free
        assertEquals(listOf("2027-05-02"), calendar.freeNights("2027-04-30", "2027-05-03"))
        assertEquals(listOf("2027-05-02"), calendar.freeNights("2027-05-03", "2027-04-30"))
    }

    @Test
    fun freeNights_isEmptyWhenNoNightInTheRangeIsFree() {
        assertEquals(emptyList<String>(), calendar.freeNights("2027-05-08", "2027-05-09"))
        assertEquals(emptyList<String>(), calendar.freeNights("2027-05-01", "2027-05-01"))
    }

    @Test
    fun requestedNightCount_countsBothEndpointsAndCapsAt365() {
        val start = LocalDate.of(2027, 5, 8)
        assertEquals(1, AvailabilityCalendar.requestedNightCount(start, start))
        assertEquals(2, AvailabilityCalendar.requestedNightCount(LocalDate.of(2027, 5, 9), start))
        assertEquals(365, AvailabilityCalendar.requestedNightCount(
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
        ))
        assertNull(AvailabilityCalendar.requestedNightCount(
            LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1),
        ))
    }

    @Test
    fun dateProximity_isFullWhenEveryRequestedNightIsFree() {
        val full = AvailabilityCalendar("1", listOf("2027-05-08", "2027-05-09"))
        assertEquals(1.0, full.dateProximity("2027-05-08", "2027-05-09", today), 0.001)
        assertEquals(1.0, full.dateProximity("2027-05-09", "2027-05-08", today), 0.001)
        assertEquals(1.0, full.dateProximity("2027-05-08", null, today), 0.001)
    }

    @Test
    fun dateProximity_isZeroWhenNoRequestedNightIsFreeOrDatesInvalid() {
        assertEquals(0.0, calendar.dateProximity("2027-05-08", "2027-05-09", today), 0.001)
        assertEquals(0.0, calendar.dateProximity("bad-date", "2027-05-09", today), 0.001)
        assertEquals(0.0, calendar.dateProximity("2027-01-01", "2028-01-01", today), 0.001)
    }

    @Test
    fun dateProximity_favoursFreeNightsCloserToTheRequestedStartDate() {
        val onStart = AvailabilityCalendar("1", listOf("2027-05-08"))
        val inMiddle = AvailabilityCalendar("2", listOf("2027-05-10"))
        val atEnd = AvailabilityCalendar("3", listOf("2027-05-12"))

        assertEquals(0.5, onStart.dateProximity("2027-05-08", "2027-05-12", today), 0.001)
        assertEquals(0.25, inMiddle.dateProximity("2027-05-08", "2027-05-12", today), 0.001)
        assertEquals(0.0, atEnd.dateProximity("2027-05-08", "2027-05-12", today), 0.001)
    }

    @Test
    fun dateProximity_rewardsAdjacentFreeNightsAndIgnoresPastOrHeldOnes() {
        val nextDayFree = AvailabilityCalendar("1", listOf("2027-05-08", "2027-05-10"))
        val fourDaysOut = AvailabilityCalendar("2", listOf("2027-05-08", "2027-05-13"))
        val heldAdjacent = AvailabilityCalendar(
            "3", listOf("2027-05-08", "2027-05-10"), listOf("2027-05-10"),
        )
        val pastAdjacent = AvailabilityCalendar("4", listOf("2027-05-07", "2027-05-08"))

        assertEquals(1.0, nextDayFree.dateProximity("2027-05-08", "2027-05-09", today), 0.001)
        assertEquals(
            0.5 + 0.5 * (4.0 / 7.0),
            fourDaysOut.dateProximity("2027-05-08", "2027-05-09", today),
            0.001,
        )
        assertEquals(0.5, heldAdjacent.dateProximity("2027-05-08", "2027-05-09", today), 0.001)
        // May 7 is in the past when today is May 8, so it doesn't count as an adjacent option
        val onCheckIn = LocalDate.of(2027, 5, 8)
        assertEquals(0.5, pastAdjacent.dateProximity("2027-05-08", "2027-05-09", onCheckIn), 0.001)
    }

    @Test
    fun dateProximity_withoutRequestedDatesFavoursSoonerUpcomingAvailability() {
        val freeToday = AvailabilityCalendar("1", listOf("2027-04-28"))
        val freeIn30Days = AvailabilityCalendar("2", listOf("2027-05-28"))
        val freeIn60Days = AvailabilityCalendar("3", listOf("2027-06-27"))
        val onlyPastAndHeld = AvailabilityCalendar(
            "4", listOf("2027-04-20", "2027-04-28", "not-a-date"), listOf("2027-04-28"),
        )

        assertEquals(1.0, freeToday.dateProximity(today = today), 0.001)
        assertEquals(0.5, freeIn30Days.dateProximity(today = today), 0.001)
        assertEquals(0.0, freeIn60Days.dateProximity(today = today), 0.001)
        assertEquals(0.0, onlyPastAndHeld.dateProximity(today = today), 0.001)
        assertEquals(0.0, AvailabilityCalendar("5").dateProximity(today = today), 0.001)
    }
}
