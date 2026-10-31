/*
 * ReservationTest.kt
 * Unit tests for the Active / Past rule on reservations.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReservationTest {

    private val today = LocalDate.parse("2027-05-09")
    private val mayEightToTen = Reservation(
        campsiteName = "Bow River Sites",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
        groupSize = 4,
        status = ReservationStatus.CONFIRMED,
    )

    @Test
    fun isActive_whenConfirmedAndNotCheckedOut() {
        assertTrue(mayEightToTen.isActive(today))
    }

    @Test
    fun isActive_onCheckOutDay() {
        assertTrue(mayEightToTen.isActive(LocalDate.parse("2027-05-10")))
    }

    @Test
    fun isPast_afterCheckOut() {
        assertFalse(mayEightToTen.isActive(LocalDate.parse("2027-05-11")))
    }

    @Test
    fun isPast_whenCancelledOrRejected() {
        assertFalse(mayEightToTen.copy(status = ReservationStatus.CANCELLED).isActive(today))
        assertFalse(mayEightToTen.copy(status = ReservationStatus.REJECTED).isActive(today))
    }
}
