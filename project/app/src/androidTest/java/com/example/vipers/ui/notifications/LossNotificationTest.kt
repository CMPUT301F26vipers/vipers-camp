/*
 * LossNotificationTest.kt
 * Intent tests for US 01.03.02: after a lottery draw passes over this device, the Notifications
 * tab (mockup E10) shows a "Not selected" notice naming the campsite, shows no "You won" notice,
 * and running the draw again neither adds a second notice nor makes a read one unread.
 * The draw is run directly with LotteryDrawer on fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.lottery.LotteryDrawResult
import com.example.vipers.ui.lottery.LotteryDrawer
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Random

class LossNotificationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
    )

    // registration closed an hour ago, so the draw can run; one winner for two entrants
    private val mayLottery = Lottery(
        id = "may",
        ownerId = "owner-phone",
        campsiteIds = listOf("hinton"),
        nightsOffered = listOf("2027-05-08", "2027-05-09"),
        registrationStart = now - 72 * hour,
        registrationEnd = now - hour,
        bookingStart = now,
        bookingEnd = now + 48 * hour,
        winnersCount = 1,
    )

    // this device and one other entrant, both on the waiting list
    private val waiting = listOf("test-device", "alex-phone").map { userId ->
        LotteryEntry(
            id = LotteryEntry.idFor("may", userId),
            lotteryId = "may",
            userId = userId,
            status = EntryStatus.WAITING,
        )
    }

    private val campsites = FakeCampsiteRepository(listOf(hinton))
    private val lotteries = FakeLotteryRepository(listOf(mayLottery), waiting)
    private val notifications = FakeNotificationRepository()

    // with this seed the draw picks alex-phone, so this device loses (Random(42) picks this device)
    private val drawer = LotteryDrawer(lotteries, notifications, campsites, Random(12345))

    @Before
    fun runTheDraw() {
        // nothing starts a draw on its own yet (02.05.01b), so the test runs it
        val result = runBlocking { drawer.drawLottery("may", now) }
        assertTrue(result is LotteryDrawResult.Success)
        // fail clearly here if a change to the draw makes this seed pick this device
        val entry = runBlocking { lotteries.getEntry("may", "test-device") }
        assertEquals(EntryStatus.LOST, entry?.status)
    }

    /** Shows this device's Notifications screen, reading the fake inbox the draw wrote to. */
    private fun openNotifications() {
        val controller = NotificationsController(
            NotificationService(notifications, campsites),
            userId = "test-device",
        )
        composeRule.setContent {
            VipersTheme {
                NotificationsScreen(
                    onOpenReserve = {},
                    onOpenCampsite = {},
                    onOpenReservation = {},
                    onOpenThread = {},
                    controller = controller,
                )
            }
        }
    }

    @Test
    fun loserSeesNotSelectedWithTheCampsiteName() {
        openNotifications()

        val rows = composeRule.onAllNodesWithTag("notification_row")
        rows.assertCountEquals(1)
        rows[0].assert(hasText("Not selected", substring = true))
        composeRule.onNodeWithText(
            "Hinton Ridge Campground",
            substring = true,
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun loserSeesNoYouWon() {
        openNotifications()

        composeRule.onNodeWithText("You won the lottery!").assertDoesNotExist()
        val types = runBlocking { notifications.getNotificationsFor("test-device") }.map { it.type }
        assertEquals(listOf(NotificationType.NOT_SELECTED), types)
    }

    @Test
    fun runningTheDrawAgainLeavesOneNotSelectedNotice() {
        openNotifications()

        // a second run, e.g. the owner tapping Draw again, while the inbox is open
        runBlocking { drawer.drawLottery("may", now) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("notification_row").assertCountEquals(1)
        assertEquals(1, runBlocking { notifications.getNotificationsFor("test-device") }.size)
    }

    @Test
    fun aLossAlreadyOpenedStaysReadWhenTheDrawRunsAgain() {
        openNotifications()
        composeRule.onNodeWithText("Not selected this time").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("unread_dot", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }

        runBlocking { drawer.drawLottery("may", now) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("unread_dot", useUnmergedTree = true).assertCountEquals(0)
    }
}
