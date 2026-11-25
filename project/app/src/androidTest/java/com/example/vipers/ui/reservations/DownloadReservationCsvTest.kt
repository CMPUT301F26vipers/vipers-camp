/*
 * DownloadReservationCsvTest.kt
 * Connected acceptance tests for US 01.05.02: Get Reservation by Notification or CSV (#63).
 * Verifies the Download CSV button is displayed on ReservationDetailsScreen and launches
 * the document creation flow with the expected file name.
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
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
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
            messageRepository = app.container.messageRepository,
            notificationRepository = notificationRepository,
            imageRepository = app.container.imageRepository,
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
                    onMessage = {},
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
    fun downloadCsvButton_tapped_launchesWithExpectedFileName() {
        val reservationRepo = FakeReservationRepository(listOf(sampleReservation))
        val campsiteRepo = FakeCampsiteRepository(listOf(sampleCampsite))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = "res-101",
        )
        var launchedFileName: String? = null

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-101",
                    onBack = {},
                    onMessage = {},
                    controller = controller,
                    onDownloadCsv = { launchedFileName = it },
                )
            }
        }

        composeRule.onNodeWithTag("button_download_csv")
            .performScrollTo()
            .performClick()

        assertEquals("reservation_R-10042.csv", launchedFileName)
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
