/*
 * ReservationDetailsControllerTest.kt
 * Unit tests for ReservationDetailsController (US 01.05.01), using fake repositories.
 * Checks loading the entrant's reservation details, handling missing or unauthorized records,
 * repository failure error states, and cancelling before check-in (US 01.07.01).
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val beforeCheckIn = LocalDate.of(2027, 5, 20)

    @Test
    fun performCancel_beforeCheckIn_cancelsAndUpdatesTheScreen() = runBlocking {
        val repo = FakeReservationRepository(listOf(reservation))
        val controller = ReservationDetailsController(
            reservationRepository = repo,
            userId = "sam",
            reservationId = "res-101",
        )
        controller.fetchData()
        assertTrue(controller.canCancel(beforeCheckIn))

        assertTrue(controller.performCancel(now = 5000, today = beforeCheckIn))

        assertEquals(ReservationStatus.CANCELLED, controller.reservation?.status)
        assertEquals(ReservationStatus.CANCELLED, repo.getReservation("res-101")?.status)
        assertEquals(5000L, repo.getReservation("res-101")?.statusChangedAt)
        assertFalse(controller.canCancel(beforeCheckIn))
        assertFalse(controller.cancelling)
        assertNull(controller.cancelError)
    }

    @Test
    fun performCancel_afterCheckIn_doesNotCancel() = runBlocking {
        val repo = FakeReservationRepository(listOf(reservation))
        val controller = ReservationDetailsController(
            reservationRepository = repo,
            userId = "sam",
            reservationId = "res-101",
        )
        controller.fetchData()
        val afterCheckIn = LocalDate.of(2027, 6, 2)

        assertFalse(controller.canCancel(afterCheckIn))
        assertFalse(controller.performCancel(now = 5000, today = afterCheckIn))
        assertEquals(ReservationStatus.CONFIRMED, repo.getReservation("res-101")?.status)
        assertEquals("This reservation can no longer be cancelled.", controller.cancelError)
    }

    @Test
    fun performCancel_whenRejectedWhileOpen_showsTheNewStatus() = runBlocking {
        val repo = FakeReservationRepository(listOf(reservation))
        val controller = ReservationDetailsController(
            reservationRepository = repo,
            userId = "sam",
            reservationId = "res-101",
        )
        controller.fetchData()
        repo.changeStatus("res-101", ReservationStatus.REJECTED, "Site flooded", now = 4000)

        assertFalse(controller.performCancel(now = 5000, today = beforeCheckIn))
        assertEquals("This reservation can no longer be cancelled.", controller.cancelError)
        assertEquals(ReservationStatus.REJECTED, controller.reservation?.status)
        assertFalse(controller.canCancel(beforeCheckIn))
    }

    @Test
    fun performCancel_whenSaveFails_showsFixedMessageAndKeepsTheReservation() = runBlocking {
        val failingRepo = object : ReservationRepository by FakeReservationRepository(
            listOf(reservation),
        ) {
            override suspend fun changeStatus(
                id: String,
                status: ReservationStatus,
                reason: String?,
                now: Long,
                sendNotice: Boolean,
            ): Boolean = throw Exception("connection timed out")
        }
        val controller = ReservationDetailsController(
            reservationRepository = failingRepo,
            userId = "sam",
            reservationId = "res-101",
        )
        controller.fetchData()

        assertFalse(controller.performCancel(now = 5000, today = beforeCheckIn))
        assertEquals(ReservationStatus.CONFIRMED, controller.reservation?.status)
        assertEquals(
            "Couldn't cancel. Check your connection and try again.",
            controller.cancelError,
        )
        assertFalse(controller.cancelling)
    }

    @Test
    fun performCancel_beforeLoading_doesNothing() = runBlocking {
        val controller = controllerFor("res-101", "sam", reservation)

        assertFalse(controller.performCancel(now = 5000, today = beforeCheckIn))
        assertNull(controller.cancelError)
    }
}
