/*
 * OptOutNotificationsTest.kt
 * Intent tests for US 01.10.01: the notifications switch on Settings (mockups E20, E20b) is on by
 * default, turning it off saves to the profile and stays off after a restart, a draw then adds
 * nothing to the entrant's inbox, the result still shows on Campsite Details, and turning it back
 * on saves it again. Uses the fake repositories, and runs the draw directly with LotteryDrawer.
 * Known issues: device notifications (T-14) and owner notifications (02.10.01, 02.12.01) aren't
 * built yet; they will use NotificationService.optedIn too.
 */
package com.example.vipers.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.campsites.CampsiteDetailsController
import com.example.vipers.ui.campsites.CampsiteDetailsScreen
import com.example.vipers.ui.campsites.formatDate
import com.example.vipers.ui.lottery.LotteryDrawResult
import com.example.vipers.ui.lottery.LotteryDrawer
import com.example.vipers.ui.notifications.NotificationsController
import com.example.vipers.ui.notifications.NotificationsScreen
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Random

class OptOutNotificationsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

    private val sam = UserProfile(id = "test-device", name = "Sam Patel", email = "sam@example.com")

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 6500,
    )

    // registration closed an hour ago, so the draw can run; both entrants win
    private val mayLottery = Lottery(
        id = "may",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        nightsOffered = listOf("2027-05-08", "2027-05-09"),
        registrationStart = now - 72 * hour,
        registrationEnd = now - hour,
        bookingStart = now,
        bookingEnd = now + 48 * hour,
        winnersCount = 2,
    )
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

    /** Shows Settings for this device, loading its profile from [profiles]. */
    private fun openSettings(profiles: FakeProfileRepository) {
        val controller = profileController(profiles, "test-device")
        composeRule.setContent {
            VipersTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenPolicy = {},
                    onLoadDemoData = {},
                    controller = controller,
                )
            }
        }
    }

    private fun switch() = composeRule.onNodeWithTag("notifications_switch")

    /** Waits until the switch has saved [on] to this device's profile in [profiles]. */
    private fun waitForSaved(profiles: FakeProfileRepository, on: Boolean) =
        composeRule.waitUntil(5_000) {
            runBlocking { profiles.getProfile("test-device")?.notificationsEnabled } == on
        }

    /** Runs the May draw, which picks both entrants. */
    private fun runTheDraw(profiles: FakeProfileRepository) {
        val drawer = LotteryDrawer(lotteries, notifications, campsites, profiles, Random(42))
        val result = runBlocking { drawer.drawLottery("may", now) }
        assertTrue(result is LotteryDrawResult.Success)
    }

    // the profile photo isn't used here, so it gets a fake image repository and photo reader
    private fun profileController(repository: ProfileRepository, deviceId: String) =
        ProfileController(repository, deviceId, FakeImageRepository(), FakePhotoReader())

    @Test
    fun switchIsOnByDefault() {
        openSettings(FakeProfileRepository(listOf(sam)))

        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Lottery results and messages").assertIsDisplayed()
        switch().assertIsOn()
    }

    @Test
    fun turningTheSwitchOffSavesItToTheProfile() {
        val profiles = FakeProfileRepository(listOf(sam))
        openSettings(profiles)

        switch().performClick()

        switch().assertIsOff()
        composeRule.onNodeWithText("Off: no lottery results or messages will be sent to you")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Draw results still show on each campsite's details screen.")
            .assertIsDisplayed()
        waitForSaved(profiles, on = false)
        // only the setting changed
        val saved = runBlocking { profiles.getProfile("test-device") }
        assertEquals(sam.copy(notificationsEnabled = false), saved)
    }

    @Test
    fun switchIsStillOffAfterRestarting() {
        val profiles = FakeProfileRepository(listOf(sam))
        // the screen shows whichever controller this holds; a new one stands for a restart
        var controller by mutableStateOf(profileController(profiles, "test-device"))
        composeRule.setContent {
            VipersTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenPolicy = {},
                    onLoadDemoData = {},
                    controller = controller,
                )
            }
        }
        switch().performClick()
        waitForSaved(profiles, on = false)

        // "restart": a new controller loads the profile again from the same repository
        controller = profileController(profiles, "test-device")

        composeRule.waitUntil(5_000) { !controller.isLoading }
        switch().assertIsOff()
        composeRule.onNodeWithText("Off: no lottery results or messages will be sent to you")
            .assertIsDisplayed()
    }

    @Test
    fun turningTheSwitchBackOnSavesItAgain() {
        val profiles = FakeProfileRepository(listOf(sam.copy(notificationsEnabled = false)))
        openSettings(profiles)

        switch().performClick()

        switch().assertIsOn()
        composeRule.onNodeWithText("Lottery results and messages").assertIsDisplayed()
        waitForSaved(profiles, on = true)
    }

    @Test
    fun afterTurningItOffADrawAddsNothingToTheInbox() {
        val profiles = FakeProfileRepository(
            listOf(sam, UserProfile(id = "alex-phone", name = "Alex Kim")),
        )
        openSettings(profiles)
        switch().performClick()
        waitForSaved(profiles, on = false)

        runTheDraw(profiles)

        assertTrue(runBlocking { notifications.getNotificationsFor("test-device") }.isEmpty())
        // the other winner kept notifications on, so they still get theirs
        assertEquals(1, runBlocking { notifications.getNotificationsFor("alex-phone") }.size)
    }

    @Test
    fun notificationsTabIsEmptyForAnOptedOutWinner() {
        val profiles = FakeProfileRepository(listOf(sam.copy(notificationsEnabled = false)))
        runTheDraw(profiles)
        val controller = NotificationsController(
            NotificationService(notifications, campsites, profiles),
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

        composeRule.onNodeWithText("No notifications yet").assertIsDisplayed()
    }

    @Test
    fun campsiteDetailsStillShowsTheWinWithNotificationsOff() {
        val profiles = FakeProfileRepository(listOf(sam.copy(notificationsEnabled = false)))
        runTheDraw(profiles)
        val controller = CampsiteDetailsController(campsites, lotteries, "hinton", "test-device")
        composeRule.setContent {
            VipersTheme {
                CampsiteDetailsScreen(
                    campsiteId = "hinton",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithText("You won! Reserve by ${formatDate(mayLottery.bookingEnd)}")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("reserve_button").performScrollTo().assertIsDisplayed()
    }
}
