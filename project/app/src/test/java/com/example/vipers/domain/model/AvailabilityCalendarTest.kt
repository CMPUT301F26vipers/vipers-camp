/*
 * AvailabilityCalendarTest.kt
 * Unit tests for opening and closing nights in AvailabilityCalendar (T-10), using the May 2027
 * weekends from scenario 2 in the project description.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
