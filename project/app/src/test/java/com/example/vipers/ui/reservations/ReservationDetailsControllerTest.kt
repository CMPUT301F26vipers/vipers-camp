/*
 * ReservationDetailsControllerTest.kt
 * Unit tests for ReservationDetailsController (US 01.05.01), using fake repositories.
 * Checks loading the entrant's reservation details, handling missing or unauthorized records,
 * and repository failure error states.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReservationDetailsControllerTest {

    private val campsite = Campsite(
        id = "site-hinton",
        name = "Hinton Ridge Campground",
        pricePerNightCents = 6550,
    )

    private val reservation = Reservation(
        id = "res-101",
        reservationNumber = "R-10042",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-06-01",
        checkOut = "2027-06-03",
        groupSize = 3,
        totalPriceCents = 13100,
        rulesAcceptedAt = 1_700_000_000_000,
    )

    private fun controllerFor(
        reservationId: String,
        userId: String = "sam",
        vararg reservations: Reservation,
    ): ReservationDetailsController {
        val reservationRepo = FakeReservationRepository(reservations.toList())
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        return ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = userId,
            reservationId = reservationId,
        )
    }

    @Test
    fun fetchData_withOwnReservation_loadsReservationAndCampsiteDetails() = runBlocking {
        val controller = controllerFor("res-101", "sam", reservation)
        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        val res = controller.reservation
        assertNotNull(res)
        assertEquals("res-101", res?.id)
        assertEquals("R-10042", res?.reservationNumber)
        assertEquals("Hinton Ridge Campground", res?.campsiteName)
        assertEquals("2027-06-01", res?.checkIn)
        assertEquals("2027-06-03", res?.checkOut)
        assertEquals(3, res?.groupSize)
        assertEquals(13100L, res?.totalPriceCents)
        assertEquals("$131", res?.totalPriceText)
        assertEquals(campsite, controller.campsite)
    }

    @Test
    fun fetchData_withMissingReservation_leavesReservationNull() = runBlocking {
        val controller = controllerFor("nope", "sam", reservation)
        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.reservation)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_withAnotherUsersReservation_doesNotShowIt() = runBlocking {
        val controller = controllerFor("res-101", "alex", reservation)
        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.reservation)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_whenRepositoryFails_setsError() = runBlocking {
        val failingRepo = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservation(id: String): Reservation? =
                throw Exception("connection timed out")
        }
        val controller = ReservationDetailsController(
            reservationRepository = failingRepo,
            campsiteRepository = FakeCampsiteRepository(),
            userId = "sam",
            reservationId = "res-101",
        )
        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.reservation)
        assertEquals("Couldn't load the reservation", controller.error)
    }
}
