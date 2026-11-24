/*
 * AutoDrawLotteryTest.kt
 * Intent tests for US 02.05.01b: opening the app draws every lottery whose registration has ended,
 * and the owner's Draw now button on Lottery Details (mockup O09) draws it any time, asking first
 * while registration is still open. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

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
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AutoDrawLotteryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hour = 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    // registration is still open for another day
    private val open = Lottery(
        id = "open",
        ownerId = "test-device",
        campsiteIds = listOf("hinton"),
        nightsDescription = "May 2027 weekends",
        registrationStart = now - 24 * hour,
        registrationEnd = now + 24 * hour,
        bookingStart = now + 48 * hour,
        bookingEnd = now + 96 * hour,
        winnersCount = 2,
    )

    // registration ended an hour ago, but nobody has drawn it yet
    private val ended = open.copy(
        id = "ended",
        nightsDescription = "June 2027 weekends",
        registrationEnd = now - hour,
    )

    private fun waiting(lotteryId: String, userId: String) =
        LotteryEntry(
            id = LotteryEntry.idFor(lotteryId, userId), lotteryId = lotteryId, userId = userId,
            status = EntryStatus.WAITING,
        )

    private val lotteries = FakeLotteryRepository(
        listOf(open, ended),
        listOf(
            waiting("open", "sam"), waiting("open", "alex"), waiting("open", "jordan"),
            waiting("ended", "sam"), waiting("ended", "alex"), waiting("ended", "jordan"),
        ),
    )

    /** Puts the fake repositories in the app, with this device as the owner of both lotteries. */
    private fun useFakes(): AppContainer {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma")),
            ),
            campsiteRepository = FakeCampsiteRepository(
                listOf(Campsite(id = "hinton", ownerId = "test-device", name = "Hinton Ridge")),
            ),
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        return app.container
    }

    /** Shows Lottery Details for [lotteryId] as seen by [userId]. */
    private fun openLottery(lotteryId: String, userId: String = "test-device") {
        val container = useFakes()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteries,
            profileRepository = container.profileRepository,
            lotteryId = lotteryId,
            currentUserId = userId,
            campsiteRepository = container.campsiteRepository,
            lotteryDrawer = container.lotteryDrawer,
        )
        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(lotteryId = lotteryId, onBack = {}, controller = controller)
            }
        }
    }

    private fun storedLottery(id: String) = runBlocking { lotteries.getLottery(id) }

    /** Waits for the draw running in the background to finish. */
    private fun waitForDraw(id: String) {
        composeRule.waitUntil(5_000) { storedLottery(id)?.drawnAt != null }
        composeRule.waitForIdle()
    }

    @Test
    fun ownerSeesDrawNow() {
        openLottery("open")

        composeRule.onNodeWithTag("button_draw_now").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun entrantDoesNotSeeDrawNow() {
        openLottery("open", userId = "casey")

        composeRule.onNodeWithText("Join Waiting List").performScrollTo()
        composeRule.onNodeWithTag("button_draw_now").assertDoesNotExist()
    }

    @Test
    fun drawNowWhileOpenAsksFirst_andCancelKeepsRegistrationOpen() {
        openLottery("open")

        composeRule.onNodeWithTag("button_draw_now").performScrollTo().performClick()
        composeRule.onNodeWithText("Draw Now?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Draw Now?").assertDoesNotExist()
        assertNull(storedLottery("open")?.drawnAt)
        assertEquals(open.registrationEnd, storedLottery("open")?.registrationEnd)
    }

    @Test
    fun confirmingAnEarlyDrawClosesRegistrationAndPicksTheWinners() {
        openLottery("open")

        composeRule.onNodeWithTag("button_draw_now").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_draw_button").performClick()
        waitForDraw("open")

        composeRule.onNodeWithTag("draw_message").performScrollTo()
            .assertTextEquals("Draw done: 2 of 3 entrants won")
        composeRule.onNodeWithTag("button_draw_now").assertDoesNotExist()
        val drawn = storedLottery("open")!!
        assertTrue(drawn.registrationEnd < open.registrationEnd)
        val entries = runBlocking { lotteries.getEntries("open") }
        assertEquals(2, entries.count { it.status == EntryStatus.WON })
    }

    @Test
    fun drawNowAfterRegistrationEnds_drawsWithoutAsking() {
        openLottery("ended")

        composeRule.onNodeWithTag("button_draw_now").performScrollTo().performClick()
        waitForDraw("ended")

        composeRule.onNodeWithText("Draw Now?").assertDoesNotExist()
        composeRule.onNodeWithTag("draw_message").performScrollTo()
            .assertTextEquals("Draw done: 2 of 3 entrants won")
        assertEquals(ended.registrationEnd, storedLottery("ended")?.registrationEnd)
    }

    @Test
    fun openingTheAppDrawsLotteriesWhoseRegistrationEnded() {
        val container = useFakes()

        composeRule.setContent {
            VipersTheme {
                AppRoot(
                    container.deviceIdentityManager,
                    onLoadDemoData = {},
                    onAppOpened = { container.drawAndNotifyWorker.runDueDraws() },
                )
            }
        }
        waitForDraw("ended")

        assertNotNull(storedLottery("ended")?.drawnAt)
        // the owner didn't do anything, and the open lottery isn't drawn early
        assertNull(storedLottery("open")?.drawnAt)
        val sam = runBlocking { container.notificationRepository.getNotificationsFor("sam") }
        assertEquals(1, sam.size)
    }
}
