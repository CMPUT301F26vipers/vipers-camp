/*
 * CancelReservationTest.kt
 * Intent tests for US 01.07.01: an entrant cancels a reservation from Reservation Details
 * (mockup E15) after confirming in the Cancel Reservation dialog (mockup E16).
 * Uses fake repositories, so nothing is written to Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.ui.theme.VipersTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CancelReservationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private val checkIn = today.plusDays(30)
    private val checkOut = today.plusDays(32)

    private val jasper = Campsite(
        id = "site-jasper",
        ownerId = "owner-1",
        name = "Jasper Lakeside Sites",
        rules = "Quiet hours 10 PM to 7 AM",
        cancellationPolicy = "Free cancellation up to 7 days before check-in.",
        pricePerNightCents = 5000,
    )

    private val stay = Reservation(
        id = "res-jasper",
        reservationNumber = "R-20417",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-jasper",
        campsiteName = "Jasper Lakeside Sites",
        checkIn = checkIn.toString(),
        checkOut = checkOut.toString(),
        groupSize = 4,
        totalPriceCents = 10000,
    )

    private val inbox = FakeNotificationRepository()

    /** Opens Reservation Details for [reservation], with its nights held at [campsite]. */
    private fun open(
        reservation: Reservation,
        campsite: Campsite = jasper,
    ): FakeReservationRepository {
        val campsites = FakeCampsiteRepository(listOf(campsite))
        runBlocking {
            campsites.saveAvailability(
                AvailabilityCalendar(
                    campsiteId = campsite.id,
                    openNights = reservation.nights(),
                    heldNights = reservation.nights(),
                )
            )
        }
        val reservations = FakeReservationRepository(
            campsites,
            FakeLotteryRepository(),
            inbox,
            listOf(reservation),
        )
        val controller = ReservationDetailsController(
            reservationRepository = reservations,
            campsiteRepository = campsites,
            userId = "sam",
            reservationId = reservation.id,
        )
        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = reservation.id,
                    onBack = {},
                    onMessage = {},
                    controller = controller,
                )
            }
        }
        composeRule.waitForIdle()
        return reservations
    }

    @Test
    fun activeReservation_showsCancelButton() {
        open(stay)

        composeRule.onNodeWithTag("button_cancel_reservation")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tappingCancel_opensDialogWithCancellationPolicy() {
        open(stay)

        composeRule.onNodeWithTag("button_cancel_reservation").performScrollTo().performClick()

        composeRule.onNodeWithText("Cancel reservation?").assertIsDisplayed()
        composeRule.onNodeWithText("The nights will be released to other winners.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_cancellation_policy").assertIsDisplayed()
        composeRule.onNodeWithText("Free cancellation up to 7 days before check-in.")
            .assertIsDisplayed()
    }

    @Test
    fun campsiteWithoutPolicy_dialogLeavesThePolicyOut() {
        open(stay, jasper.copy(cancellationPolicy = ""))

        composeRule.onNodeWithTag("button_cancel_reservation").performScrollTo().performClick()

        composeRule.onNodeWithText("Cancel reservation?").assertIsDisplayed()
        composeRule.onNodeWithTag("dialog_cancellation_policy").assertDoesNotExist()
    }

    @Test
    fun keepIt_closesDialogAndKeepsTheReservation() {
        val reservations = open(stay)

        composeRule.onNodeWithTag("button_cancel_reservation").performScrollTo().performClick()
        composeRule.onNodeWithTag("cancel_keep").performClick()

        composeRule.onNodeWithText("Cancel reservation?").assertDoesNotExist()
        composeRule.onNodeWithTag("detail_status").assertTextEquals("Confirmed")
        val saved = runBlocking { reservations.getReservation("res-jasper") }
        assertEquals(ReservationStatus.CONFIRMED, saved?.status)
    }

    @Test
    fun confirmingCancel_setsCancelledReleasesNightsAndNotifies() {
        val reservations = open(stay)

        composeRule.onNodeWithTag("button_cancel_reservation").performScrollTo().performClick()
        composeRule.onNodeWithTag("cancel_confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_status").assertTextEquals("Cancelled")
        composeRule.onNodeWithTag("detail_header").assertTextEquals("Cancelled Reservation")
        composeRule.onNodeWithTag("button_cancel_reservation").assertDoesNotExist()

        runBlocking {
            assertEquals(
                ReservationStatus.CANCELLED,
                reservations.getReservation("res-jasper")?.status,
            )
            val calendar = reservations.campsiteRepository!!.getAvailability("site-jasper")
            assertTrue(calendar.heldNights.isEmpty())
            val notice = inbox.getNotificationsFor("sam").single()
            assertEquals(NotificationType.RESERVATION_CANCELLED, notice.type)
            assertTrue(notice.message.startsWith("Jasper Lakeside Sites"))
        }
    }

    @Test
    fun checkInPassed_hasNoCancelButton() {
        open(
            stay.copy(
                checkIn = today.minusDays(1).toString(),
                checkOut = today.plusDays(1).toString(),
            )
        )

        composeRule.onNodeWithTag("detail_status").assertTextEquals("Confirmed")
        composeRule.onNodeWithTag("button_cancel_reservation").assertDoesNotExist()
    }

    @Test
    fun alreadyCancelled_hasNoCancelButton() {
        open(stay.copy(status = ReservationStatus.CANCELLED))

        composeRule.onNodeWithTag("detail_status").assertTextEquals("Cancelled")
        composeRule.onNodeWithTag("button_cancel_reservation").assertDoesNotExist()
    }
}
