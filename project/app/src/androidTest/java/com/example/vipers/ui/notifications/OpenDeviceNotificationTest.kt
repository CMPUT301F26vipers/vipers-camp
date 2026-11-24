/*
 * OpenDeviceNotificationTest.kt
 * Intent tests for T-14: starting MainActivity the way a tapped phone notification does
 * (DeviceNotificationLink.toIntent) opens the same screen as tapping the item in the inbox, marks
 * it read, and Back returns to the inbox; an intent without a notification opens Home. Uses the
 * fake repositories, so it doesn't touch Firestore, and grants notification permission so the
 * system dialog doesn't cover the screen.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.MainActivity
import com.example.vipers.data.device.DeviceNotificationLink
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OpenDeviceNotificationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val hinton = Campsite(
        id = "hinton", ownerId = "owner-priya", name = "Hinton Ridge Campground",
        pricePerNightCents = 5000, rules = "Quiet hours 10 pm to 7 am",
    )

    private val reservation = Reservation(
        id = "res-1", reservationNumber = "R-10042", userId = "test-device", ownerId = "owner-priya",
        campsiteId = "hinton", campsiteName = "Hinton Ridge Campground",
        checkIn = LocalDate.now().plusDays(10).toString(),
        checkOut = LocalDate.now().plusDays(12).toString(),
        groupSize = 3, totalPriceCents = 10000, rulesAcceptedAt = now - day,
    )

    private val confirmed = Notification(
        id = "confirmed_res-1", recipientId = "test-device",
        type = NotificationType.RESERVATION_CONFIRMED, campsiteId = "hinton", reservationId = "res-1",
        title = "Reservation confirmed", message = "Hinton Ridge Campground: May 8 to May 10",
        createdAt = now - 60_000,
    )

    private val nights = (0L until 5L).map { LocalDate.now().plusDays(10 + it).toString() }

    private val lottery = Lottery(
        id = "lot-summer", ownerId = "owner-priya", campsiteIds = listOf("hinton"),
        nightsOffered = nights,
        registrationStart = now - 10 * day, registrationEnd = now - 2 * day,
        bookingStart = now - day, bookingEnd = now + 5 * day, drawnAt = now - 2 * day,
    )

    private val win = Notification(
        id = "draw_lot-summer_test-device", recipientId = "test-device", type = NotificationType.WON,
        lotteryId = "lot-summer", campsiteId = "hinton", title = "You won the lottery!",
        message = "You won a spot at Hinton Ridge Campground.", createdAt = now - 120_000,
    )

    private val notifications = FakeNotificationRepository(listOf(confirmed, win))

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun useFakes() {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking {
            campsites.saveAvailability(AvailabilityCalendar(campsiteId = "hinton", openNights = nights))
        }
        val lotteries = FakeLotteryRepository(
            listOf(lottery),
            listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "test-device"), lotteryId = "lot-summer",
                    userId = "test-device", groupSize = 2, status = EntryStatus.WON,
                ),
            ),
        )
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries, listOf(reservation)),
            messageRepository = FakeMessageRepository(),
            notificationRepository = notifications,
            imageRepository = FakeImageRepository(),
        )
    }

    private fun launch(intent: Intent) {
        useFakes()
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun isRead(id: String): Boolean =
        runBlocking { notifications.getNotificationsFor("test-device") }.single { it.id == id }.read

    @After
    fun closeApp() {
        scenario?.close()
    }

    @Test
    fun tappingAReservationNotification_opensItsDetailsAndMarksItRead() {
        launch(DeviceNotificationLink.toIntent(app, confirmed))

        waitForTag("detail_reservation_number")
        composeRule.onNodeWithTag("detail_reservation_number").assertTextEquals("R-10042")
        composeRule.waitUntil(5_000) { isRead("confirmed_res-1") }
        assertTrue(isRead("confirmed_res-1"))
    }

    @Test
    fun backFromTheOpenedScreen_returnsToTheInbox() {
        launch(DeviceNotificationLink.toIntent(app, confirmed))
        waitForTag("detail_reservation_number")

        Espresso.pressBack()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("You won the lottery!")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("You won the lottery!").assertIsDisplayed()
        composeRule.onNodeWithText("Reservation confirmed").assertIsDisplayed()
    }

    @Test
    fun tappingAWinNotification_opensReserveAndMarksItRead() {
        launch(DeviceNotificationLink.toIntent(app, win))

        waitForTag("reserve_campsite")
        composeRule.onNodeWithTag("reserve_campsite").assertTextEquals("Hinton Ridge Campground")
        composeRule.waitUntil(5_000) { isRead("draw_lot-summer_test-device") }
        assertTrue(isRead("draw_lot-summer_test-device"))
    }

    @Test
    fun openingTheAppWithoutANotification_showsHome() {
        launch(Intent(app, MainActivity::class.java))

        waitForTag("button_scan_qr")
        composeRule.onNodeWithTag("button_scan_qr").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number").assertDoesNotExist()
    }
}
