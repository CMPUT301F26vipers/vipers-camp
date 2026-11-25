/*
 * SentNotificationsIntentTest.kt
 * Intent tests for US 02.10.02: the owner taps Sent Notifications on My Campsites (O01) and sees
 * every notification they sent (O13), newest first, each with its message, campsite and lottery,
 * and number of recipients. Another owner's message and the owner's other notifications aren't
 * listed, an owner who sent nothing sees the empty message, and Back returns to My Campsites.
 * Drives the app's own navigation with the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Rule
import org.junit.Test

class SentNotificationsIntentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ownerId = "priya-phone"
    private val otherOwnerId = "daniel-phone"
    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    private val hinton = Campsite(
        id = "hinton",
        ownerId = ownerId,
        name = "Hinton Valley Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 6,
        pricePerNightCents = 5000,
    )

    private val banff = Campsite(
        id = "banff",
        ownerId = ownerId,
        name = "Banff Pines",
        address = "Banff, AB",
        region = Region.BANFF,
        maxOccupancy = 4,
        pricePerNightCents = 6500,
    )

    private val mayLottery = Lottery(
        id = "hinton-may",
        ownerId = ownerId,
        campsiteIds = listOf("hinton"),
        nightsDescription = "May 2027 weekends",
    )

    private val summerLottery = Lottery(
        id = "summer",
        ownerId = ownerId,
        campsiteIds = listOf("hinton", "banff"),
        nightsDescription = "Summer long weekends",
    )

    private fun send(
        batchId: String,
        recipients: List<String>,
        lotteryId: String,
        campsiteId: String?,
        message: String,
        createdAt: Long,
        senderId: String = ownerId,
    ) = recipients.map { userId ->
        Notification(
            id = "broadcast_${batchId}_$userId",
            recipientId = userId,
            senderId = senderId,
            campsiteId = campsiteId,
            lotteryId = lotteryId,
            type = NotificationType.OWNER_BROADCAST,
            title = "Message from owner",
            message = message,
            batchId = batchId,
            createdAt = createdAt,
        )
    }

    private val savedNotifications =
        send(
            "reminder", listOf("sam", "aisha", "liam"), "hinton-may", "hinton",
            "Reminder: the draw is tomorrow at noon.", now - 72 * hour,
        ) + send(
            "thanks", listOf("sam", "aisha", "liam", "noor", "kai"), "summer", null,
            "Thanks for entering! Results come out Friday.", now - 48 * hour,
        ) + send(
            "cookhouse", listOf("sam"), "hinton-may", "hinton",
            "The cookhouse is open again.", now - 24 * hour,
        ) + send(
            "daniel", listOf("sam", "aisha"), "jasper", "jasper",
            "The Jasper showers are closed this week.", now - 12 * hour, senderId = otherOwnerId,
        ) + Notification(
            id = "rejected_r1",
            recipientId = "sam",
            senderId = ownerId,
            campsiteId = "hinton",
            reservationId = "r1",
            type = NotificationType.RESERVATION_REJECTED,
            title = "Reservation rejected",
            message = "Hinton Valley Campground: 2027-05-08 to 2027-05-10. Reason: Site flooded.",
            createdAt = now - 6 * hour,
        )

    private fun startAppAs(deviceId: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = deviceId,
            profileRepository = FakeProfileRepository(
                listOf(
                    UserProfile(id = ownerId, name = "Priya Sharma"),
                    UserProfile(id = otherOwnerId, name = "Daniel Okafor"),
                    UserProfile(id = "new-owner-phone", name = "Jordan Lee"),
                ),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton, banff)),
            lotteryRepository = FakeLotteryRepository(listOf(mayLottery, summerLottery)),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(savedNotifications),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme {
                AppNavigation(isAdmin = false, onLoadDemoData = {})
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

    private fun openSentNotifications() {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        waitForText("Sent Notifications")
        composeRule.onNodeWithText("Sent Notifications").performClick()
    }

    private fun assertRow(index: Int, message: String, subtitle: String, recipients: String) {
        composeRule.onAllNodesWithTag("sent_notification_row")[index]
            .assert(hasAnyDescendant(hasText(message)))
            .assert(hasAnyDescendant(hasText(subtitle)))
            .assert(hasAnyDescendant(hasText(recipients, substring = true)))
    }

    @Test
    fun sentNotifications_showsTheOwnersThreeSendsNewestFirst() {
        startAppAs(ownerId)
        openSentNotifications()
        waitForTag("sent_notification_row")

        composeRule.onNodeWithText("Sent").assertIsDisplayed()
        composeRule.onAllNodesWithTag("sent_notification_row").assertCountEquals(3)
        assertRow(
            0,
            "The cookhouse is open again.",
            "Hinton Valley Campground · May 2027 weekends",
            "· sent to 1",
        )
        assertRow(
            1,
            "Thanks for entering! Results come out Friday.",
            "Hinton Valley Campground, Banff Pines · Summer long weekends",
            "· sent to 5",
        )
        assertRow(
            2,
            "Reminder: the draw is tomorrow at noon.",
            "Hinton Valley Campground · May 2027 weekends",
            "· sent to 3",
        )
        composeRule.onAllNodesWithText("The Jasper showers are closed this week.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Site flooded", substring = true).assertCountEquals(0)
    }

    @Test
    fun sentNotifications_ownerWithNoSends_seesTheEmptyMessage() {
        startAppAs("new-owner-phone")
        openSentNotifications()

        waitForText("You haven't sent any notifications yet.")
        composeRule.onNodeWithText("You haven't sent any notifications yet.").assertIsDisplayed()
        composeRule.onAllNodesWithTag("sent_notification_row").assertCountEquals(0)
    }

    @Test
    fun back_fromSentNotifications_returnsToMyCampsites() {
        startAppAs(ownerId)
        openSentNotifications()
        waitForTag("sent_notification_row")

        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("My Campsites")
        composeRule.onNodeWithText("My Campsites").assertIsDisplayed()
        composeRule.onNodeWithText("Sent Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Banff Pines").assertIsDisplayed()
    }
}
