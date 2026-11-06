/*
 * WinNotificationTest.kt
 * Intent tests for US 01.03.01: after a lottery draw picks this device, the Notifications tab
 * (mockup E10) shows a "You won" notice naming the campsite, tapping it opens Reserve for that
 * lottery, and running the draw again doesn't add a second notice. The draw is run directly with
 * LotteryDrawer on fake repositories, so it doesn't touch Firestore.
 * Known issues: the Reserve screen (01.04.01) isn't merged yet, so the tap is checked through the
 * screen's onOpenReserve lambda instead of the Reserve screen itself.
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
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.lottery.LotteryDrawer
import com.example.vipers.ui.lottery.LotteryDrawResult
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Random

class WinNotificationTest {

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

    // registration closed an hour ago, so the draw can run; two winners for two entrants
    private val mayLottery = Lottery(
        id = "may",
        ownerId = "owner-phone",
        campsiteIds = listOf("hinton"),
        nightsOffered = listOf("2027-05-08", "2027-05-09"),
        registrationStart = now - 72 * hour,
        registrationEnd = now - hour,
        bookingStart = now,
        bookingEnd = now + 48 * hour,
        winnersCount = 2,
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
    private val drawer = LotteryDrawer(lotteries, notifications, campsites, Random(42))

    // the lottery ID the screen last asked to reserve for
    private var reservedFor: String? = null

    @Before
    fun runTheDraw() {
        // nothing starts a draw on its own yet (02.05.01b), so the test runs it
        val result = runBlocking { drawer.drawLottery("may", now) }
        assertTrue(result is LotteryDrawResult.Success)
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
    fun winnerSeesYouWonWithTheCampsiteName() {
        openNotifications()

        val rows = composeRule.onAllNodesWithTag("notification_row")
        rows.assertCountEquals(1)
        rows[0].assert(hasText("You won the lottery!"))
        composeRule.onNodeWithText(
            "You won a spot at Hinton Ridge Campground.",
            substring = true,
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun tappingTheWinOpensReserveForThatLottery() {
        openNotifications()

        composeRule.onNodeWithText("You won the lottery!").performClick()

        composeRule.waitUntil(5_000) { reservedFor != null }
        assertEquals("may", reservedFor)
    }

    @Test
    fun runningTheDrawAgainLeavesOneWinNotice() {
        openNotifications()

        // a second run, e.g. the owner tapping Draw again, while the inbox is open
        runBlocking { drawer.drawLottery("may", now) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("notification_row").assertCountEquals(1)
        assertEquals(1, runBlocking { notifications.getNotificationsFor("test-device") }.size)
    }
}
