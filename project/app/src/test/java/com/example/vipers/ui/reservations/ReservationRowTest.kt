/*
 * ReservationRowTest.kt
 * Unit tests for the text ReservationRow shows on the owner's list (US 02.07.01).
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationRowTest {

    private fun row(checkIn: String = "2027-05-08", checkOut: String = "2027-05-10", groupSize: Int = 1) =
        ReservationRow(
            reservationId = "r1",
            entrantName = "Sam Patel",
            campsiteName = "Hinton Ridge Campground",
            checkIn = checkIn,
            checkOut = checkOut,
            groupSize = groupSize,
        )

    @Test
    fun datesText_sameYear_showsTheYearOnlyAtTheEnd() {
        assertEquals("May 8 – May 10, 2027", row().datesText)
    }

    @Test
    fun datesText_differentMonthsSameYear_showsTheYearOnlyAtTheEnd() {
        assertEquals(
            "May 30 – Jun 2, 2027",
            row(checkIn = "2027-05-30", checkOut = "2027-06-02").datesText,
        )
    }

    @Test
    fun datesText_differentYears_showsTheYearOnBothSides() {
        assertEquals(
            "Dec 30, 2027 – Jan 2, 2028",
            row(checkIn = "2027-12-30", checkOut = "2028-01-02").datesText,
        )
    }

    @Test
    fun datesText_unreadableDate_showsTheDatesAsSaved() {
        assertEquals(
            "soon – 2027-05-10",
            row(checkIn = "soon").datesText,
        )
    }

    @Test
    fun guestsText_oneGuest_isSingular() {
        assertEquals("1 guest", row(groupSize = 1).guestsText)
    }

    @Test
    fun guestsText_severalGuests_isPlural() {
        assertEquals("3 guests", row(groupSize = 3).guestsText)
    }
}
