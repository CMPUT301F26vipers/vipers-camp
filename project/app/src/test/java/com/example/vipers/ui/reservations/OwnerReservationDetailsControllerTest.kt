/*
 * OwnerReservationDetailsControllerTest.kt
 * Unit tests for OwnerReservationDetailsController (US 02.07.01) and its reject flow
 * (US 02.08.01), using the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OwnerReservationDetailsControllerTest {

    private val sam = UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com", phone = "780-555-0101")
    private val profiles = FakeProfileRepository(listOf(sam))
    private val notifications = FakeNotificationRepository()
    private val campsites =
        FakeCampsiteRepository(listOf(Campsite(id = "hinton", name = "Hinton Ridge Campground")))
    private val notificationService =
        NotificationService(notifications, campsites, profiles, FakeLotteryRepository())
    private val today = LocalDate.parse("2027-05-09")

    private val reservation = Reservation(
        id = "r1",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "hinton",
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
            notificationService = notificationService,
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
        val controller = OwnerReservationDetailsController(failing, profiles, notificationService, "owner-1", "r1")
        controller.fetchData()

        assertEquals("Couldn't load the reservation", controller.error)
        assertFalse(controller.loading)
    }

    private lateinit var reservationRepo: FakeReservationRepository

    private suspend fun loadedRejectController(
        stay: Reservation = reservation,
    ): OwnerReservationDetailsController {
        campsites.saveAvailability(
            AvailabilityCalendar(
                campsiteId = "hinton",
                openNights = listOf("2027-05-08", "2027-05-09", "2027-05-10"),
                heldNights = listOf("2027-05-08", "2027-05-09", "2027-05-10"),
            )
        )
        reservationRepo =
            FakeReservationRepository(campsites, FakeLotteryRepository(), notifications, listOf(stay))
        val controller = OwnerReservationDetailsController(
            reservationRepository = reservationRepo,
            profileRepository = profiles,
            notificationService = notificationService,
            ownerId = "owner-1",
            reservationId = stay.id,
        )
        controller.fetchData()
        return controller
    }

    @Test
    fun canReject_activeConfirmedReservation_isTrue() = runBlocking {
        assertTrue(loadedRejectController().canReject(today))
    }

    @Test
    fun canReject_pastReservation_isFalse() = runBlocking {
        assertFalse(loadedRejectController().canReject(LocalDate.parse("2027-05-11")))
    }

    @Test
    fun canReject_cancelledOrRejectedReservation_isFalse() = runBlocking {
        val cancelled = loadedRejectController(reservation.copy(status = ReservationStatus.CANCELLED))
        assertFalse(cancelled.canReject(today))
        val rejected = loadedRejectController(
            reservation.copy(status = ReservationStatus.REJECTED, rejectionReason = "Flooding"),
        )
        assertFalse(rejected.canReject(today))
    }

    @Test
    fun dismissRejectDialog_clearsTheReasonAndChangesNothing() = runBlocking {
        val controller = loadedRejectController()
        controller.openRejectDialog()
        controller.onReasonChange("Forest fire warning")

        controller.dismissRejectDialog()

        assertFalse(controller.showRejectDialog)
        assertEquals("", controller.rejectReason)
        assertEquals(ReservationStatus.CONFIRMED, reservationRepo.getReservation("r1")!!.status)
        assertEquals(
            listOf("2027-05-08", "2027-05-09", "2027-05-10"),
            campsites.getAvailability("hinton").heldNights,
        )
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun performReject_blankReason_setsAnErrorAndChangesNothing() = runBlocking {
        val controller = loadedRejectController()
        controller.openRejectDialog()
        controller.onReasonChange("   ")

        assertFalse(controller.performReject(now = 3_000, today = today))

        assertEquals("Enter a reason", controller.reasonError)
        assertTrue(controller.showRejectDialog)
        assertEquals(ReservationStatus.CONFIRMED, reservationRepo.getReservation("r1")!!.status)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun performReject_validReason_rejectsReleasesTheNightsAndNotifiesTheEntrant() = runBlocking {
        val controller = loadedRejectController()
        controller.openRejectDialog()
        controller.onReasonChange("  Forest fire warning  ")

        assertTrue(controller.performReject(now = 3_000, today = today))

        assertEquals(ReservationStatus.REJECTED, controller.reservation?.status)
        assertEquals("Forest fire warning", controller.reservation?.rejectionReason)
        assertFalse(controller.showRejectDialog)
        assertNull(controller.rejectError)
        assertEquals(listOf("2027-05-10"), campsites.getAvailability("hinton").heldNights)
        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals(NotificationType.RESERVATION_REJECTED, notice.type)
        assertEquals("owner-1", notice.senderId)
        assertTrue(notice.message.contains("Forest fire warning"))
    }

    @Test
    fun performReject_afterTheEntrantCancelled_saysItCanNoLongerBeRejected() = runBlocking {
        val controller = loadedRejectController()
        reservationRepo.changeStatus("r1", ReservationStatus.CANCELLED, now = 2_000, sendNotice = false)
        controller.openRejectDialog()
        controller.onReasonChange("Forest fire warning")

        assertFalse(controller.performReject(now = 3_000, today = today))

        assertEquals("This reservation can no longer be rejected.", controller.rejectError)
        assertFalse(controller.showRejectDialog)
        assertEquals(ReservationStatus.CANCELLED, controller.reservation?.status)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun performReject_whenTheSaveFails_keepsTheDialogOpenWithTheReason() = runBlocking {
        val failing = object : ReservationRepository by FakeReservationRepository(listOf(reservation)) {
            override suspend fun rejectTransaction(
                reservationId: String,
                reason: String,
                now: Long,
                sendNotice: Boolean,
            ): Boolean = throw Exception("offline")
        }
        val controller =
            OwnerReservationDetailsController(failing, profiles, notificationService, "owner-1", "r1")
        controller.fetchData()
        controller.openRejectDialog()
        controller.onReasonChange("Forest fire warning")

        assertFalse(controller.performReject(now = 3_000, today = today))

        assertEquals("Couldn't reject. Check your connection and try again.", controller.rejectError)
        assertTrue(controller.showRejectDialog)
        assertEquals("Forest fire warning", controller.rejectReason)
        assertFalse(controller.rejecting)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }
}
