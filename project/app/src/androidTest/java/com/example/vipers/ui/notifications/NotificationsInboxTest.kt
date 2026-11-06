/*
 * NotificationsInboxTest.kt
 * Intent tests for T-13: the entrant opens the Notifications tab (mockup E10), sees their own
 * notifications newest first or an empty-state message, and taps a win to mark it read and open
 * Reserve. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none. The win tap is checked through the screen's onOpenReserve lambda here;
 * ViewDatesAfterWinningTest (01.04.01) checks that it opens the Reserve screen in the whole app.
 */
package com.example.vipers.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsInboxTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    // this device's inbox, saved out of order, plus one for someone else
    private val win = Notification(
        id = "win", recipientId = "test-device", type = NotificationType.WON, lotteryId = "may",
        campsiteId = "hinton", title = "You won the lottery!",
        message = "Hinton Ridge Campground: reserve by May 9", createdAt = now - 2 * hour,
    )
    private val ownerMessage = Notification(
        id = "broadcast", recipientId = "test-device", type = NotificationType.OWNER_BROADCAST,
        campsiteId = "hinton", title = "Message from owner",
        message = "Hinton Ridge Campground: the cookhouse reopens next week.", read = true,
        createdAt = now - 3 * 24 * hour,
    )
    private val confirmed = Notification(
        id = "confirmed", recipientId = "test-device", type = NotificationType.RESERVATION_CONFIRMED,
        reservationId = "r1", title = "Reservation confirmed",
        message = "Bow River Sites: May 8 to May 10", read = true, createdAt = now - 5 * 60 * 1000L,
    )
    private val someoneElses = Notification(
        id = "alex", recipientId = "alex-phone", type = NotificationType.NOT_SELECTED,
        campsiteId = "hinton", title = "Not selected this time", message = "Alex's notification",
        createdAt = now,
    )

    // the lottery ID the screen last asked to reserve for
    private var reservedFor: String? = null

    /** Shows the Notifications screen for "test-device" with [notifications] in a fake repository. */
    private fun openScreen(notifications: List<Notification>) {
        val controller = NotificationsController(
            NotificationService(FakeNotificationRepository(notifications)),
            userId = "test-device",
        )
        composeRule.setContent {
            VipersTheme {
                NotificationsScreen(
                    onOpenReserve = { lotteryId -> reservedFor = lotteryId },
                    onOpenCampsite = {},
                    onOpenReservation = {},
                    onOpenThread = {},
                    controller = controller,
                )
            }
        }
    }

    @Test
    fun notificationsTabWithNoNotificationsShowsTheEmptyState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            // someone else's notification isn't this user's, so the inbox is still empty
            notificationRepository = FakeNotificationRepository(listOf(someoneElses)),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) } }

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("No notifications yet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("notification_row").assertCountEquals(0)
    }

    @Test
    fun itemsAreNewestFirstAndOnlyTheUsersOwn() {
        openScreen(listOf(win, ownerMessage, someoneElses, confirmed))

        val rows = composeRule.onAllNodesWithTag("notification_row")
        rows.assertCountEquals(3)
        rows[0].assert(hasText("Reservation confirmed"))
        rows[1].assert(hasText("You won the lottery!"))
        rows[2].assert(hasText("Message from owner"))
        composeRule.onNodeWithText("Not selected this time").assertDoesNotExist()
    }

    @Test
    fun eachRowShowsItsCategoryMessageAndTime() {
        openScreen(listOf(win))

        composeRule.onNodeWithText("Won", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground: reserve by May 9", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("2h", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingAnUnreadWinRemovesItsDotAndOpensReserve() {
        openScreen(listOf(win, ownerMessage))
        // only the win is unread
        composeRule.onAllNodesWithTag("unread_dot", useUnmergedTree = true).assertCountEquals(1)

        composeRule.onNodeWithText("You won the lottery!").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("unread_dot", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals("may", reservedFor)
    }
}
