/*
 * OwnerReservationDetailsControllerTest.kt
 * Unit tests for OwnerReservationDetailsController (US 02.07.01), using the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerReservationDetailsControllerTest {

    private val sam = UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com", phone = "780-555-0101")
    private val profiles = FakeProfileRepository(listOf(sam))

    private val reservation = Reservation(
        id = "r1",
        userId = "sam",
        ownerId = "owner-1",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
        groupSize = 4,
        rulesAcceptedAt = 1_700_000_000_000,
    )

    private fun controllerFor(reservationId: String, vararg reservations: Reservation) =
        OwnerReservationDetailsController(
            reservationRepository = FakeReservationRepository(reservations.toList()),
            profileRepository = profiles,
            ownerId = "owner-1",
            reservationId = reservationId,
        )

    @Test
    fun fetchData_withOwnReservation_loadsItAndTheEntrant() = runBlocking {
        val controller = controllerFor("r1", reservation)
        controller.fetchData()

        assertEquals(reservation, controller.reservation)
        assertEquals(sam, controller.entrant)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_withMissingReservation_leavesReservationNull() = runBlocking {
        val controller = controllerFor("nope", reservation)
        controller.fetchData()

        assertNull(controller.reservation)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_withAnotherOwnersReservation_doesNotShowIt() = runBlocking {
        val controller = controllerFor("r1", reservation.copy(ownerId = "owner-2"))
        controller.fetchData()

        assertNull(controller.reservation)
        assertNull(controller.entrant)
    }

    @Test
    fun fetchData_withDeletedEntrant_loadsTheReservationWithoutAnEntrant() = runBlocking {
        val controller = controllerFor("r1", reservation.copy(userId = "gone"))
        controller.fetchData()

        assertEquals("r1", controller.reservation?.id)
        assertNull(controller.entrant)
    }

    @Test
    fun fetchData_whenTheRepositoryFails_setsError() = runBlocking {
        // a repository that always fails, e.g. Firestore being unreachable
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservation(id: String): Reservation? = throw Exception("offline")
        }
        val controller = OwnerReservationDetailsController(failing, profiles, "owner-1", "r1")
        controller.fetchData()

        assertEquals("Couldn't load the reservation", controller.error)
        assertFalse(controller.loading)
    }
}
