/*
 * NotificationLogTest.kt
 * Intent tests for US 03.08.01: an admin opens the Admin tab, taps Notification Log (A01), and sees
 * every send on the Logs screen (A09), newest first, with its type, sender, recipient, campsite, time
 * and text. Checks that the log is read-only. Drives the app's own navigation with the fake
 * repositories, so it doesn't touch Firestore.
 * Known issues: none. NavigationTest already checks that non-admins don't see the Admin tab.
 */
package com.example.vipers.ui.admin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.messages.messageTime
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotificationLogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drawTime = 1_806_657_300_000L
    private val broadcastTime = drawTime + 3_600_000L
    private val rejectionTime = drawTime + 7_200_000L

    private val profiles = listOf(
        UserProfile(id = "test-device", name = "Ada Admin", isAdmin = true),
        UserProfile(id = "seed-owner-priya", name = "Priya Sharma"),
        UserProfile(id = "seed-owner-daniel", name = "Daniel Okafor"),
        UserProfile(id = "seed-entrant-sam", name = "Sam Patel"),
        UserProfile(id = "user-alex", name = "Alex Chen"),
        UserProfile(id = "user-kim", name = "Kim Lee"),
        UserProfile(id = "user-lee", name = "Lee Wong"),
        UserProfile(id = "user-ravi", name = "Ravi Singh"),
    )

    private val campsites = listOf(
        Campsite(id = "hinton-ridge", ownerId = "seed-owner-priya", name = "Hinton Ridge Campground"),
        Campsite(id = "bow-river", ownerId = "seed-owner-daniel", name = "Bow River Sites", removed = true),
    )

    private val lottery = Lottery(
        id = "lottery-hinton",
        ownerId = "seed-owner-priya",
        campsiteIds = listOf("hinton-ridge"),
        nightsOffered = listOf("2027-05-08", "2027-05-09"),
        winnersCount = 2,
    )

    private fun drawNotice(userId: String, won: Boolean) = Notification(
        id = "draw_lottery-hinton_$userId",
        recipientId = userId,
        senderId = "system",
        campsiteId = "hinton-ridge",
        lotteryId = "lottery-hinton",
        type = if (won) NotificationType.WON else NotificationType.NOT_SELECTED,
        title = if (won) "You won the lottery!" else "Not selected this time",
        message = if (won) {
            "You won a spot at Hinton Ridge Campground. Reserve your nights before May 1, 2027."
        } else {
            "You weren't selected for Hinton Ridge Campground this time."
        },
        createdAt = drawTime,
    )

    private fun broadcast(userId: String) = Notification(
        id = "broadcast_batch-hinton_$userId",
        recipientId = userId,
        senderId = "seed-owner-priya",
        campsiteId = "hinton-ridge",
        lotteryId = "lottery-hinton",
        type = NotificationType.OWNER_BROADCAST,
        title = "Message from owner",
        message = "Hinton Ridge Campground: Bring bear spray.",
        batchId = "batch-hinton",
        createdAt = broadcastTime,
    )

    private val rejection = Notification(
        id = "rejected_res-20431",
        recipientId = "seed-entrant-sam",
        senderId = "seed-owner-daniel",
        campsiteId = "bow-river",
        reservationId = "res-20431",
        type = NotificationType.RESERVATION_REJECTED,
        title = "Reservation rejected",
        message = "Bow River Sites: 2027-06-01 to 2027-06-04. Reason: Fire warning",
        createdAt = rejectionTime,
    )

    @Before
    fun useFakeRepositories() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(profiles),
            campsiteRepository = FakeCampsiteRepository(campsites),
            lotteryRepository = FakeLotteryRepository(listOf(lottery)),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(
                listOf(
                    drawNotice("seed-entrant-sam", won = true),
                    drawNotice("user-alex", won = true),
                    drawNotice("user-kim", won = false),
                    drawNotice("user-lee", won = false),
                    drawNotice("user-ravi", won = false),
                    broadcast("seed-entrant-sam"),
                    broadcast("user-kim"),
                    broadcast("user-lee"),
                    rejection,
                ),
            ),
            imageRepository = FakeImageRepository(),
        )
    }

    private fun openNotificationLogAsAdmin() {
        composeRule.setContent {
            VipersTheme {
                AppNavigation(isAdmin = true, onLoadDemoData = {})
            }
        }
        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").performClick()
        composeRule.onNodeWithText("Notification Log").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("notification_log_row").fetchSemanticsNodes().size == 4
        }
    }

    private fun assertCard(index: Int, vararg texts: String) {
        val card = composeRule.onAllNodesWithTag("notification_log_row")[index]
        for (text in texts) {
            card.assert(hasAnyDescendant(hasText(text)))
        }
    }

    @Test
    fun adminOpensNotificationLog_fromAdminTab_showsLogsWithNotificationLogSelected() {
        openNotificationLogAsAdmin()

        composeRule.onNodeWithText("Logs").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_tab_notification_log").assertIsSelected()
        composeRule.onNodeWithTag("notification_log_list").assertIsDisplayed()
    }

    @Test
    fun notificationLog_showsEverySendNewestFirstWithAllFields() {
        openNotificationLogAsAdmin()

        composeRule.onAllNodesWithTag("notification_log_row").assertCountEquals(4)
        assertCard(
            0,
            "Reservation rejected",
            "Daniel Okafor → Sam Patel · ${messageTime(rejectionTime)} · Bow River Sites",
            "“Bow River Sites: 2027-06-01 to 2027-06-04. Reason: Fire warning”",
        )
        assertCard(
            1,
            "Owner message",
            "Priya Sharma → Waiting list · ${messageTime(broadcastTime)} · Hinton Ridge Campground",
            "“Hinton Ridge Campground: Bring bear spray.”",
        )
        assertCard(
            2,
            "Not selected",
            "System → 3 entrants · ${messageTime(drawTime)} · Hinton Ridge Campground",
            "“You weren't selected for Hinton Ridge Campground this time.”",
        )
        assertCard(
            3,
            "Won",
            "System → 2 entrants · ${messageTime(drawTime)} · Hinton Ridge Campground",
            "“You won a spot at Hinton Ridge Campground. Reserve your nights before May 1, 2027.”",
        )
    }

    @Test
    fun notificationLog_isReadOnly_cardsDoNothingWhenTapped() {
        openNotificationLogAsAdmin()

        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("notification_log_list")))
            .assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction() and hasText("Retry")).assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction() and hasText("Refresh")).assertCountEquals(0)

        composeRule.onAllNodesWithTag("notification_log_row")[0].performClick()

        composeRule.onNodeWithText("Logs").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_tab_notification_log").assertIsSelected()
        composeRule.onAllNodesWithTag("notification_log_row").assertCountEquals(4)
    }
}
