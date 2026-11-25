/*
 * ReservationMessagingTest.kt
 * Unit tests for who can message about a reservation and who the other side is (US 02.12.01):
 * Reservation.canMessage and Reservation.otherSide.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationMessagingTest {

    // Sam's May stay at Priya's Hinton campsite
    private val samsStay = Reservation(
        id = "res-hinton",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
    )

    @Test
    fun canMessage_theCampsitesOwner() {
        assertTrue(samsStay.canMessage("owner-priya"))
    }

    @Test
    fun canMessage_theEntrantWhoReserved() {
        assertTrue(samsStay.canMessage("sam"))
    }

    @Test
    fun cannotMessage_anotherOwnerOrEntrant() {
        assertFalse(samsStay.canMessage("owner-daniel"))
        assertFalse(samsStay.canMessage("jo"))
    }

    @Test
    fun cannotMessage_blankUserEvenIfTheReservationHasNoOwner() {
        assertFalse(samsStay.copy(ownerId = "").canMessage(""))
    }

    @Test
    fun canMessage_cancelledAndRejectedReservations() {
        // the owner may need to explain a rejection
        assertTrue(samsStay.copy(status = ReservationStatus.REJECTED).canMessage("owner-priya"))
        assertTrue(samsStay.copy(status = ReservationStatus.CANCELLED).canMessage("sam"))
    }

    @Test
    fun otherSide_ofTheOwnerIsTheEntrant() {
        assertEquals("sam", samsStay.otherSide("owner-priya"))
    }

    @Test
    fun otherSide_ofTheEntrantIsTheOwner() {
        assertEquals("owner-priya", samsStay.otherSide("sam"))
    }

    @Test
    fun otherSide_ofSomeoneElse_isNull() {
        assertNull(samsStay.otherSide("owner-daniel"))
    }

    @Test
    fun otherSide_whenTheOwnerBookedTheirOwnCampsite_isNull() {
        assertNull(samsStay.copy(userId = "owner-priya").otherSide("owner-priya"))
    }
}
