/*
 * SendNotificationTest.kt
 * Intent tests for US 02.10.01: the owner opens their lottery's Waiting List (O11), taps Send
 * Notification, and sends a message (O12) to everyone on the list. Checks that an empty message
 * can't be sent, that every entrant who didn't opt out gets one OWNER_BROADCAST, and that it shows
 * in a recipient's inbox as "Message from owner". Drives the app's own navigation with the fake
 * repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SendNotificationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ownerId = "priya-phone"
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val hinton = Campsite(
        id = "hinton",
        ownerId = ownerId,
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 6,
        pricePerNightCents = 5000,
    )

    private val mayLottery = Lottery(
        id = "hinton-may",
        ownerId = ownerId,
        campsiteIds = listOf("hinton"),
        nightsDescription = "May 2027 weekends",
        registrationStart = now - 2 * day,
        registrationEnd = now + 3 * day,
        bookingStart = now + 4 * day,
        bookingEnd = now + 10 * day,
        winnersCount = 1,
        waitlistCapacity = 10,
    )

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = ownerId, name = "Priya Sharma"),
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "aisha", name = "Aisha Khan"),
            UserProfile(id = "liam", name = "Liam Chen", notificationsEnabled = false),
        ),
    )

    private val entrants = listOf("sam", "aisha", "liam")
    private val notifications = FakeNotificationRepository()
    private val campsites = FakeCampsiteRepository(listOf(hinton))
    private val lotteries = FakeLotteryRepository(
        listOf(mayLottery),
        entrants.mapIndexed { index, userId ->
            LotteryEntry(
                id = LotteryEntry.idFor("hinton-may", userId),
                lotteryId = "hinton-may",
                userId = userId,
                groupSize = 2,
                joinedAt = now - (3 - index) * day,
            )
        },
    )

    private var inbox by mutableStateOf<NotificationsController?>(null)

    @Before
    fun useFakeRepositories() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = ownerId,
            profileRepository = profiles,
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(),
            notificationRepository = notifications,
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme {
                val open = inbox
                if (open == null) {
                    AppNavigation(isAdmin = false, onLoadDemoData = {})
                } else {
                    NotificationsScreen(
                        onOpenReserve = {},
                        onOpenCampsite = {},
                        onOpenReservation = {},
                        onOpenThread = {},
                        controller = open,
                    )
                }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openWaitingList() {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        waitForText("Hinton Ridge Campground")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        waitForTag("lottery_row")
        composeRule.onNodeWithTag("lottery_row").performScrollTo().performClick()
        waitForText("View Waiting List")
        composeRule.onNodeWithText("View Waiting List").performScrollTo().performClick()
        waitForTag("button_send_notification")
    }

    private fun openSendNotification() {
        openWaitingList()
        composeRule.onNodeWithTag("button_send_notification").performClick()
        waitForTag("send_notification_count")
    }

    private fun savedNotices() = runBlocking { entrants.flatMap { notifications.getNotificationsFor(it) } }

    @Test
    fun sendNotificationButton_opensTheFormWithTheEntrantCount() {
        openWaitingList()

        composeRule.onNodeWithText("Send Notification").assertIsDisplayed()
        composeRule.onNodeWithTag("button_send_notification").performClick()

        waitForTag("send_notification_count")
        composeRule.onNodeWithText("Sent to everyone on this lottery's waiting list").assertIsDisplayed()
        composeRule.onNodeWithTag("send_notification_count").assertIsDisplayed()
        composeRule.onNodeWithText("3 entrants").assertIsDisplayed()
        composeRule.onNodeWithTag("field_message").assertIsDisplayed()
        composeRule.onNodeWithText("Write a message").assertIsDisplayed()
    }

    @Test
    fun emptyOrBlankMessage_cannotBeSent() {
        openSendNotification()

        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("field_message").performTextInput("    ")
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("button_send").performClick()

        composeRule.waitForIdle()
        assertTrue(savedNotices().isEmpty())
    }

    @Test
    fun send_givesEveryoneWhoDidNotOptOutOneOwnerMessage_andReturnsToTheWaitingList() {
        openSendNotification()

        composeRule.onNodeWithTag("field_message").performTextInput("The cookhouse reopens next week.")
        composeRule.onNodeWithTag("button_send").performClick()

        waitForTag("waiting_list_count")
        composeRule.onNodeWithTag("waiting_list_count").assertIsDisplayed()
        composeRule.onNodeWithTag("button_send_notification").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("field_message").fetchSemanticsNodes().size)

        val notices = savedNotices()
        assertEquals(listOf("aisha", "sam"), notices.map { it.recipientId }.sorted())
        assertTrue(notices.all { it.type == NotificationType.OWNER_BROADCAST })
        assertTrue(notices.all { it.message == "The cookhouse reopens next week." })
        assertTrue(notices.all { it.title == "Message from owner" })
        assertTrue(notices.all { it.senderId == ownerId })
        assertTrue(notices.all { it.lotteryId == "hinton-may" })
        assertEquals(1, notices.map { it.batchId }.distinct().size)
        assertTrue(notices.first().batchId!!.isNotBlank())
        assertEquals(1, notices.map { it.createdAt }.distinct().size)
        assertTrue(notices.first().createdAt >= now)
        assertTrue(runBlocking { notifications.getNotificationsFor("liam") }.isEmpty())
    }

    @Test
    fun sentMessage_showsInARecipientsInboxAsMessageFromOwner() {
        openSendNotification()
        composeRule.onNodeWithTag("field_message").performTextInput("The cookhouse reopens next week.")
        composeRule.onNodeWithTag("button_send").performClick()
        waitForTag("waiting_list_count")

        composeRule.runOnUiThread {
            inbox = NotificationsController(
                NotificationService(notifications, campsites, profiles, lotteries),
                userId = "sam",
            )
        }

        waitForText("Message from owner")
        composeRule.onNodeWithText("Message from owner", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("The cookhouse reopens next week.", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Owner message", useUnmergedTree = true).assertIsDisplayed()
    }
}
