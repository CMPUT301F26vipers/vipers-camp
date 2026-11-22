/*
 * ReservationTest.kt
 * Unit tests for the Active / Past rule on reservations, and for which ones can be cancelled
 * (US 01.07.01).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
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

    private val samsStay = mayEightToTen.copy(userId = "sam", ownerId = "priya")

    @Test
    fun canMessageOwner_whenEntrantsStayIsConfirmed() {
        assertTrue(samsStay.canMessageOwner("sam"))
    }

    @Test
    fun canMessageOwner_whenEntrantsStayIsRejected() {
        assertTrue(samsStay.copy(status = ReservationStatus.REJECTED).canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_whenStayIsCancelled() {
        assertFalse(samsStay.copy(status = ReservationStatus.CANCELLED).canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_asAnotherEntrant() {
        assertFalse(samsStay.canMessageOwner("another-entrant"))
    }

    @Test
    fun cannotMessageOwner_asTheOwner() {
        assertFalse(samsStay.canMessageOwner("priya"))
    }

    @Test
    fun cannotMessageOwner_whenEntrantBookedTheirOwnCampsite() {
        assertFalse(samsStay.copy(ownerId = "sam").canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_whenOwnerIdIsBlank() {
        assertFalse(samsStay.copy(ownerId = "").canMessageOwner("sam"))
    }

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

    @Test
    fun canCancel_beforeCheckIn() {
        assertTrue(mayEightToTen.canCancel(LocalDate.parse("2027-05-01")))
    }

    @Test
    fun canCancel_onCheckInDay() {
        assertTrue(mayEightToTen.canCancel(LocalDate.parse("2027-05-08")))
    }

    @Test
    fun cannotCancel_afterCheckInHasPassed() {
        assertFalse(mayEightToTen.canCancel(today))
    }

    @Test
    fun cannotCancel_whenAlreadyCancelledOrRejected() {
        val early = LocalDate.parse("2027-05-01")
        assertFalse(mayEightToTen.copy(status = ReservationStatus.CANCELLED).canCancel(early))
        assertFalse(mayEightToTen.copy(status = ReservationStatus.REJECTED).canCancel(early))
    }

    @Test
    fun cannotCancel_whenCheckInCantBeRead() {
        assertFalse(mayEightToTen.copy(checkIn = "").canCancel(today))
    }

    @Test
    fun nights_runFromCheckInToTheNightBeforeCheckOut() {
        assertEquals(listOf("2027-05-08", "2027-05-09"), mayEightToTen.nights())
    }
}
