/*
 * RejectReservationTest.kt
 * Intent tests for US 02.08.01: an owner rejects a reservation from the owner's Reservation
 * details (mockup O15) after giving a reason in the Reject reservation dialog (mockup O16).
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
import androidx.compose.ui.test.performTextInput
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
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.theme.VipersTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RejectReservationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()

    private val hinton =
        Campsite(id = "hinton", ownerId = "owner-priya", name = "Hinton Ridge Campground")

    private val profiles = FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel")))

    private val samsStay = Reservation(
        id = "res-hinton",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = today.plusDays(30).toString(),
        checkOut = today.plusDays(32).toString(),
        groupSize = 3,
        rulesAcceptedAt = 1_800_000_000_000,
    )

    private val inbox = FakeNotificationRepository()

    /** Opens the owner's details of [reservation], with its nights held at Hinton. */
    private fun open(reservation: Reservation): FakeReservationRepository {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking {
            campsites.saveAvailability(
                AvailabilityCalendar(
                    campsiteId = "hinton",
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
        val controller = OwnerReservationDetailsController(
            reservationRepository = reservations,
            profileRepository = profiles,
            notificationService = NotificationService(inbox, campsites, profiles),
            ownerId = "owner-priya",
            reservationId = reservation.id,
        )
        composeRule.setContent {
            VipersTheme {
                OwnerReservationDetailsScreen(
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
    fun activeReservation_rejectOpensTheDialog() {
        open(samsStay)

        composeRule.onNodeWithTag("button_reject_reservation").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Reject reservation?").assertIsDisplayed()
        composeRule.onNodeWithText("The entrant will be told why.").assertIsDisplayed()
        composeRule.onNodeWithTag("reject_reason").assertIsDisplayed()
    }

    @Test
    fun cancel_closesTheDialogAndKeepsTheReservation() {
        val reservations = open(samsStay)

        composeRule.onNodeWithTag("button_reject_reservation").performClick()
        composeRule.onNodeWithTag("reject_cancel").performClick()

        composeRule.onNodeWithText("Reject reservation?").assertDoesNotExist()
        composeRule.onNodeWithTag("button_reject_reservation").assertIsDisplayed()
        val saved = runBlocking { reservations.getReservation("res-hinton") }
        assertEquals(ReservationStatus.CONFIRMED, saved?.status)
    }

    @Test
    fun rejectWithoutAReason_showsTheErrorAndChangesNothing() {
        val reservations = open(samsStay)

        composeRule.onNodeWithTag("button_reject_reservation").performClick()
        composeRule.onNodeWithTag("reject_confirm").performClick()

        composeRule.onNodeWithTag("reject_reason_error", useUnmergedTree = true)
            .assertTextEquals("Enter a reason")
        composeRule.onNodeWithText("Reject reservation?").assertIsDisplayed()
        runBlocking {
            assertEquals(
                ReservationStatus.CONFIRMED,
                reservations.getReservation("res-hinton")?.status,
            )
            assertTrue(inbox.getNotificationsFor("sam").isEmpty())
        }
    }

    @Test
    fun rejectWithAReason_rejectsHidesTheButtonAndNotifiesTheEntrant() {
        val reservations = open(samsStay)

        composeRule.onNodeWithTag("button_reject_reservation").performClick()
        composeRule.onNodeWithTag("reject_reason").performTextInput("Forest fire warning")
        composeRule.onNodeWithTag("reject_confirm").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reject reservation?").assertDoesNotExist()
        composeRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Forest fire warning").assertIsDisplayed()
        composeRule.onNodeWithTag("button_reject_reservation").assertDoesNotExist()
        runBlocking {
            val saved = reservations.getReservation("res-hinton")
            assertEquals(ReservationStatus.REJECTED, saved?.status)
            assertEquals("Forest fire warning", saved?.rejectionReason)
            val notice = inbox.getNotificationsFor("sam").single()
            assertEquals(NotificationType.RESERVATION_REJECTED, notice.type)
            assertEquals("res-hinton", notice.reservationId)
        }
    }

    @Test
    fun pastReservation_hasNoRejectButton() {
        open(
            samsStay.copy(
                checkIn = today.minusDays(5).toString(),
                checkOut = today.minusDays(2).toString(),
            )
        )

        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithTag("button_reject_reservation").assertDoesNotExist()
    }
}
