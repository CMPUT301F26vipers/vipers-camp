/*
 * ReceiveCancellationConfirmationTest.kt
 * Connected UI acceptance tests for US 01.07.02: Receive Cancellation Confirmation by
 * Notification or CSV (#67).
 * Verifies all acceptance criteria and mockup E15b:
 * AC 1: After cancelling, a "Reservation cancelled" notification with campsite and dates
 *       is added to the entrant's inbox, and tapping it opens the reservation details.
 * AC 2: The cancelled reservation's details screen offers "Download CSV" with status Cancelled.
 * Mockup E15b: The cancelled reservation's details screen displays the "Cancelled on …" banner,
 *              and neither "Message Owner" nor "Cancel Reservation" is present.
 * Uses fake repositories, so tests run on the emulator without touching remote Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.service.CsvExporter
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReceiveCancellationConfirmationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()
    private val checkIn = today.plusDays(20)
    private val checkOut = today.plusDays(23)

    private val sampleCampsite = Campsite(
        id = "campsite-jasper",
        ownerId = "owner-priya",
        name = "Jasper Lakeside Sites",
        address = "Jasper, AB",
        region = Region.JASPER,
        maxOccupancy = 6,
        pricePerNightCents = 6000,
        rules = "Quiet hours 10 PM to 7 AM",
        cancellationPolicy = "Free cancellation up to 48 hours before check-in.",
    )

    private val sampleReservation = Reservation(
        id = "res-jasper-1",
        reservationNumber = "R-50021",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "campsite-jasper",
        campsiteName = "Jasper Lakeside Sites",
        checkIn = checkIn.toString(),
        checkOut = checkOut.toString(),
        groupSize = 4,
        totalPriceCents = 18000,
        rulesAcceptedAt = now - 100_000L,
        status = ReservationStatus.CONFIRMED,
    )

    private fun setContainer(
        campsiteRepo: FakeCampsiteRepository,
        reservationRepo: FakeReservationRepository,
        notificationRepo: FakeNotificationRepository,
    ) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "sam",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "sam", name = "Sam Patel"))
            ),
            campsiteRepository = campsiteRepo,
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = reservationRepo,
            messageRepository = FakeMessageRepository(),
            notificationRepository = notificationRepo,
            imageRepository = FakeImageRepository(),
        )
    }

    @Test
    fun ac1_cancellingReservation_addsNotificationToInbox_andTappingOpensDetails() {
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        runBlocking {
            campsiteRepo.saveAvailability(
                AvailabilityCalendar(
                    campsiteId = sampleCampsite.id,
                    openNights = sampleReservation.nights(),
                    heldNights = sampleReservation.nights(),
                )
            )
        }
        val notificationRepo = FakeNotificationRepository()
        val reservationRepo = FakeReservationRepository(
            campsiteRepo,
            FakeLotteryRepository(),
            notificationRepo,
            listOf(sampleReservation),
        )
        setContainer(campsiteRepo, reservationRepo, notificationRepo)

        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }

        // Navigate to reservations and cancel the reservation
        composeRule.onNodeWithTag("tab_${Routes.RESERVATIONS}").performClick()
        composeRule.onNodeWithTag("my_reservation_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("button_cancel_reservation").performScrollTo().performClick()
        composeRule.onNodeWithTag("cancel_confirm").performClick()
        composeRule.waitForIdle()

        // Verify the cancellation notification is saved in the entrant's inbox
        runBlocking {
            val notices = notificationRepo.getNotificationsFor("sam")
            val cancelNotice = notices.firstOrNull {
                it.type == NotificationType.RESERVATION_CANCELLED
            }
            assertTrue("Expected cancellation notice in inbox", cancelNotice != null)
            assertEquals("Reservation cancelled", cancelNotice!!.title)
            val expectedDates =
                "${sampleReservation.campsiteName}: ${sampleReservation.checkIn} to " +
                    sampleReservation.checkOut
            assertEquals(expectedDates, cancelNotice.message)
            assertEquals("res-jasper-1", cancelNotice.reservationId)
        }

        // Navigate back to reservations list to see the bottom navigation bar
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // Navigate to Notifications tab and tap the "Reservation cancelled" notice
        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("Reservation cancelled").performClick()
        composeRule.waitForIdle()

        // Verify that tapping the notification opened the Reservation Details screen
        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_header").assertTextEquals("Cancelled Reservation")
        composeRule.onNodeWithTag("detail_reservation_number").assertTextEquals("R-50021")
    }

    @Test
    fun ac2_cancelledReservation_offersDownloadCsvWithStatusCancelled() {
        val cancelled = sampleReservation.copy(
            status = ReservationStatus.CANCELLED,
            statusChangedAt = now,
        )
        val reservationRepo = FakeReservationRepository(listOf(cancelled))
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = cancelled.id,
        )
        var requestedFileName: String? = null

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = cancelled.id,
                    onBack = {},
                    onMessage = {},
                    controller = controller,
                    onDownloadCsv = { requestedFileName = it },
                )
            }
        }

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("button_download_csv")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("reservation_R-50021.csv", requestedFileName)

        // Verify that the generated CSV string has status CANCELLED
        val csv = CsvExporter.toCsv(cancelled, sampleCampsite)
        val lines = csv.lines()
        val dataRow = lines[1]
        assertTrue(dataRow.endsWith(",CANCELLED"))
        assertTrue(dataRow.contains("R-50021"))
        assertTrue(dataRow.contains("Jasper Lakeside Sites"))
    }

    @Test
    fun mockupE15b_cancelledReservation_showsCancelledOnBanner_andLeavesOutActionButtons() {
        val cancelled = sampleReservation.copy(
            status = ReservationStatus.CANCELLED,
            statusChangedAt = now,
        )
        val reservationRepo = FakeReservationRepository(listOf(cancelled))
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = cancelled.id,
        )

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = cancelled.id,
                    onBack = {},
                    onMessage = {},
                    controller = controller,
                )
            }
        }

        // Mockup E15b header and banner
        composeRule.onNodeWithTag("detail_header").assertTextEquals("Cancelled Reservation")
        composeRule.onNodeWithTag("detail_cancelled_banner")
            .assertIsDisplayed()
            .assertTextContains("Cancelled on", substring = true)

        // Mockup E15b: neither Message Owner nor Cancel Reservation is present
        composeRule.onNodeWithTag("button_message_owner").assertDoesNotExist()
        composeRule.onNodeWithTag("button_cancel_reservation").assertDoesNotExist()

        // Download CSV remains available
        composeRule.onNodeWithTag("button_download_csv")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
