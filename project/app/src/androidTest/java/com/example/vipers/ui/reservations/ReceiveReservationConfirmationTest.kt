/*
 * ReceiveReservationConfirmationTest.kt
 * Connected UI acceptance tests for US 01.05.01: Receive Reservation Confirmation (#62).
 * Verifies all 3 acceptance criteria:
 * AC 1: After a successful reservation (T-03), a confirmation screen shows campsite,
 *       dates, group size, total price, and a reservation number.
 * AC 2: A "Reservation confirmed" notification is added to the entrant's inbox.
 * AC 3: The same details can be seen again later from the reservation details screen.
 * Uses fake repositories, so tests run on the emulator without touching remote Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ReserveResult
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReceiveReservationConfirmationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
        rules = "Quiet hours after 10 PM",
    )

    private val mayNights = listOf("2027-05-22", "2027-05-23", "2027-05-24")

    private val lottery = Lottery(
        id = "lottery-may",
        campsiteIds = listOf("hinton"),
        nightsOffered = mayNights,
        bookingStart = now - day,
        bookingEnd = now + 7 * day,
    )

    private val entry = LotteryEntry(
        id = "lottery-may_sam",
        lotteryId = "lottery-may",
        userId = "sam",
        status = EntryStatus.WON,
        groupSize = 1,
    )

    @Test
    fun ac1_successfulReservation_showsConfirmationScreenWithAllDetails() {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking {
            campsites.saveAvailability(
                AvailabilityCalendar(campsiteId = "hinton", openNights = mayNights)
            )
        }
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry))
        val notifications = FakeNotificationRepository()
        val reservations = FakeReservationRepository(campsites, lotteries, notifications)

        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
            userId = "sam",
            lotteryId = "lottery-may",
            campsiteId = "hinton",
        )

        composeRule.setContent {
            VipersTheme {
                ReserveScreen(
                    lotteryId = "lottery-may",
                    campsiteId = "hinton",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        // Pick 2 nights: May 22 and May 23 ($50 x 2 = $100)
        composeRule.onNodeWithTag("night_2027-05-22").performClick()
        composeRule.onNodeWithTag("night_2027-05-23").performClick()

        // Increase group size to 3
        composeRule.onNodeWithTag("reserve_guests_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("reserve_guests_increase").performClick()
        composeRule.onNodeWithTag("reserve_guests_count").assertTextEquals("3")

        // Reserve nights
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()

        // AC 1: Confirmation screen shows campsite, dates, group size, total price, and number
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_reservation_number")
            .assertTextContains("R-", substring = true)
        composeRule.onNodeWithTag("confirm_campsite_name")
            .assertTextEquals("Hinton Ridge Campground")
        composeRule.onNodeWithTag("confirm_dates")
            .assertTextEquals("May 22 – May 24, 2027")
        composeRule.onNodeWithTag("confirm_guests")
            .assertTextEquals("3 guests")
        composeRule.onNodeWithTag("confirm_total_price")
            .assertTextEquals("$100")

        // Clicking Done dismisses the dialog
        composeRule.onNodeWithTag("button_confirm_done").performClick()
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertDoesNotExist()
    }

    @Test
    fun ac2_successfulReservation_addsReservationConfirmedNotificationToInbox() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "hinton", openNights = mayNights)
        )
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry))
        val notifications = FakeNotificationRepository()
        val reservations = FakeReservationRepository(campsites, lotteries, notifications)

        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
            userId = "sam",
            lotteryId = "lottery-may",
            campsiteId = "hinton",
        )
        controller.fetchData(now)
        controller.onNightTap("2027-05-22")
        controller.onNightTap("2027-05-23")
        controller.incrementGroupSize()
        controller.incrementGroupSize()

        val result = controller.reserveNights(now)
        assertTrue(result is ReserveResult.Success)
        val res = (result as ReserveResult.Success).reservation

        // AC 2: "Reservation confirmed" notification is added to the entrant's inbox
        val inbox = notifications.getNotificationsFor("sam")
        assertEquals(1, inbox.size)
        val notice = inbox.first()
        assertEquals(NotificationType.RESERVATION_CONFIRMED, notice.type)
        assertEquals("Reservation confirmed", notice.title)
        assertTrue(notice.message.contains("Hinton Ridge Campground"))
        assertEquals(res.id, notice.reservationId)
        assertEquals("hinton", notice.campsiteId)
        assertEquals("lottery-may", notice.lotteryId)
        assertEquals(NotificationTarget.OpenReservation(res.id), notice.target)
    }

    @Test
    fun ac3_tappingInboxNotification_opensReservationDetailsScreenWithSameDetails() {
        val reservation = Reservation(
            id = "res-201",
            reservationNumber = "R-10025",
            userId = "test-device",
            ownerId = "owner-priya",
            campsiteId = "hinton",
            campsiteName = "Hinton Ridge Campground",
            checkIn = "2027-05-22",
            checkOut = "2027-05-24",
            groupSize = 3,
            totalPriceCents = 10000,
            status = com.example.vipers.domain.model.ReservationStatus.CONFIRMED,
        )

        val notice = Notification(
            id = "notice-conf",
            recipientId = "test-device",
            type = NotificationType.RESERVATION_CONFIRMED,
            reservationId = "res-201",
            campsiteId = "hinton",
            title = "Reservation confirmed",
            message = "Hinton Ridge Campground: 2027-05-22 to 2027-05-24",
            createdAt = now,
        )

        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton)),
            lotteryRepository = FakeLotteryRepository(listOf(lottery)),
            reservationRepository = FakeReservationRepository(listOf(reservation)),
            notificationRepository = FakeNotificationRepository(listOf(notice)),
        )

        composeRule.setContent {
            VipersTheme {
                AppNavigation(isAdmin = false, onLoadDemoData = {})
            }
        }

        // Navigate to Notifications tab
        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("Reservation confirmed").assertIsDisplayed()

        // Tap the "Reservation confirmed" notification row
        composeRule.onNodeWithText("Reservation confirmed").performClick()

        // AC 3: The same details can be seen again later from the reservation details screen
        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number")
            .assertTextEquals("R-10025")
        composeRule.onNodeWithTag("detail_campsite_name")
            .assertTextEquals("Hinton Ridge Campground")
        composeRule.onNodeWithTag("detail_dates")
            .assertTextEquals("May 22 – May 24, 2027")
        composeRule.onNodeWithTag("detail_guests")
            .assertTextEquals("3 guests")
        composeRule.onNodeWithTag("detail_total_price")
            .assertTextEquals("$100")
        composeRule.onNodeWithTag("detail_status")
            .assertTextEquals("Confirmed")
    }

    @Test
    fun ac3_reservationDetailsScreen_showsSameDetailsDirectly() {
        val reservation = Reservation(
            id = "res-202",
            reservationNumber = "R-10026",
            userId = "sam",
            ownerId = "owner-priya",
            campsiteId = "hinton",
            campsiteName = "Hinton Ridge Campground",
            checkIn = "2027-05-22",
            checkOut = "2027-05-24",
            groupSize = 3,
            totalPriceCents = 10000,
            status = com.example.vipers.domain.model.ReservationStatus.CONFIRMED,
        )
        val reservationRepo = FakeReservationRepository(listOf(reservation))
        val campsiteRepo = FakeCampsiteRepository(listOf(hinton))
        val controller = ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = "sam",
            reservationId = "res-202",
        )

        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-202",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number")
            .assertTextEquals("R-10026")
        composeRule.onNodeWithTag("detail_campsite_name")
            .assertTextEquals("Hinton Ridge Campground")
        composeRule.onNodeWithTag("detail_dates")
            .assertTextEquals("May 22 – May 24, 2027")
        composeRule.onNodeWithTag("detail_guests")
            .assertTextEquals("3 guests")
        composeRule.onNodeWithTag("detail_total_price")
            .assertTextEquals("$100")
        composeRule.onNodeWithTag("detail_status")
            .assertTextEquals("Confirmed")
        composeRule.onNodeWithTag("detail_campsite_rules")
            .assertTextEquals("Quiet hours after 10 PM")
    }
}
