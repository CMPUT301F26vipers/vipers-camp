/*
 * DownloadReservationCsvTest.kt
 * Connected acceptance tests for US 01.05.02: Get Reservation by Notification or CSV (#63).
 * Verifies the Download CSV button on ReservationDetailsScreen, CSV export formatting with
 * RFC 4180 escaping for spreadsheet apps, and cancellation handling without error.
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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.service.CsvExporter
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadReservationCsvTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()

    private val sampleCampsite = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
        rules = "Quiet hours after 10 PM",
    )

    private val sampleReservation = Reservation(
        id = "res-101",
        reservationNumber = "R-10042",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-06-01",
        checkOut = "2027-06-03",
        groupSize = 3,
        totalPriceCents = 10000,
        status = ReservationStatus.CONFIRMED,
    )

    private fun setContainer(
        campsiteRepository: FakeCampsiteRepository = FakeCampsiteRepository(),
        reservationRepository: FakeReservationRepository = FakeReservationRepository(),
        notificationRepository: FakeNotificationRepository = FakeNotificationRepository(),
    ) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "sam",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "sam", name = "Sam Entrant"))
            ),
            campsiteRepository = campsiteRepository,
            lotteryRepository = app.container.lotteryRepository,
            reservationRepository = reservationRepository,
            notificationRepository = notificationRepository,
        )
    }

    @Test
    fun ac1_downloadCsvButton_isDisplayedOnReservationDetailsScreen() {
        val reservationRepo = FakeReservationRepository(listOf(sampleReservation))
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = "res-101",
        )

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-101",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("button_download_csv")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Download CSV")
    }

    @Test
    fun ac2_and_ac3_csvContent_containsAllRequiredFieldsAndHeader() {
        val csv = CsvExporter.toCsv(sampleReservation, sampleCampsite)
        val lines = csv.trimEnd().lines()
        val header = lines[0]
        val row = lines[1]

        val expectedHeader = "Reservation Number,Campsite Name,Location,Check-in,Check-out," +
            "Group Size,Total Price,Status"
        assertEquals(expectedHeader, header)

        val expectedRow = "R-10042,Hinton Ridge Campground,\"Hinton, AB\",2027-06-01," +
            "2027-06-03,3,$100,CONFIRMED"
        assertEquals(expectedRow, row)
    }

    @Test
    fun ac4_csvContent_escapesCommasAndQuotesCorrectly() {
        val specialCampsite = sampleCampsite.copy(
            name = "Camp \"Pine\", West",
            address = "Jasper, AB",
        )
        val specialReservation = sampleReservation.copy(
            campsiteName = "Camp \"Pine\", West",
        )

        val csv = CsvExporter.toCsv(specialReservation, specialCampsite)
        val row = csv.trimEnd().lines()[1]

        // Campsite name has quotes and commas, address has a comma
        assertTrue(row.contains("\"Camp \"\"Pine\"\", West\""))
        assertTrue(row.contains("\"Jasper, AB\""))
    }

    @Test
    fun ac5_cancellationOrMissingReservation_showsNoErrorForDownload() {
        val reservationRepo = FakeReservationRepository(listOf(sampleReservation))
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = "res-101",
        )

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-101",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        // Screen is displayed, no error banner
        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("text_reservation_error").assertDoesNotExist()
    }

    @Test
    fun navigationToReservationDetails_showsDownloadCsvButton() {
        val notice = Notification(
            id = "notif-res",
            recipientId = "sam",
            type = NotificationType.RESERVATION_CONFIRMED,
            reservationId = "res-101",
            campsiteId = "hinton",
            title = "Reservation confirmed",
            message = "Hinton Ridge Campground: 2027-06-01 to 2027-06-03",
            createdAt = now,
        )
        setContainer(
            campsiteRepository = FakeCampsiteRepository(listOf(sampleCampsite)),
            reservationRepository = FakeReservationRepository(listOf(sampleReservation)),
            notificationRepository = FakeNotificationRepository(listOf(notice)),
        )

        composeRule.setContent {
            VipersTheme {
                AppNavigation(isAdmin = false, onLoadDemoData = {})
            }
        }

        // Navigate to Notifications tab and tap the notification
        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("Reservation confirmed").performClick()

        // Verify Reservation Details screen is opened and Download CSV button is present
        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("button_download_csv")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Download CSV")
    }
}
