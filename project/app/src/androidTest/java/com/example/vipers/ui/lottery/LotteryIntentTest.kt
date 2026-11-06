/*
 * LotteryIntentTest.kt
 * Integration / Intent tests for creating and viewing campsite lotteries.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LotteryIntentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var campsiteRepository: FakeCampsiteRepository
    private lateinit var lotteryRepository: FakeLotteryRepository
    private val ownerId = "my-phone"

    @Before
    fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        campsiteRepository = FakeCampsiteRepository()
        lotteryRepository = FakeLotteryRepository()

        app.container = AppContainer(
            deviceId = ownerId,
            profileRepository = FakeProfileRepository(),
            campsiteRepository = campsiteRepository,
            lotteryRepository = lotteryRepository,
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )

        // Seed a campsite and availability
        val camp = Campsite(id = "c-123", ownerId = ownerId, name = "Banff Spot")
        runBlocking { campsiteRepository.addCampsite(camp) }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        val openNights = listOf(dateFormat.format(today.time), dateFormat.format(tomorrow.time))

        runBlocking {
            campsiteRepository.saveAvailability(
                AvailabilityCalendar(
                    campsiteId = "c-123",
                    openNights = openNights,
                    heldNights = emptyList()
                )
            )
        }
    }

    private fun startApp() {
        composeRule.setContent {
            VipersTheme {
                AppNavigation(isAdmin = false, onLoadDemoData = {})
            }
        }
    }

    private fun clickNativeOk() {
        composeRule.waitForIdle()
        uiDevice.waitForIdle()
        val ok = uiDevice.wait(Until.findObject(By.text("OK")), 5_000)
            ?: uiDevice.findObject(By.res("android:id/button1"))
        checkNotNull(ok) { "Could not find native OK button" }
        ok.click()
        uiDevice.waitForIdle()
        composeRule.waitForIdle()
    }

    private fun selectDateTime(day: String) {
        composeRule.waitForIdle()
        uiDevice.waitForIdle()
        uiDevice.wait(Until.hasObject(By.text("OK")), 5_000)

        // Advance to next month so selected dates are guaranteed to be in the future
        val nextBtn = uiDevice.findObject(By.desc("Next month"))
            ?: uiDevice.findObject(By.res("android:id/next"))
        nextBtn?.click()
        uiDevice.waitForIdle()

        // Select the requested day of the month
        val dayNode = uiDevice.findObject(By.text(day))
        dayNode?.click()
        uiDevice.waitForIdle()

        // Confirm DatePickerDialog
        clickNativeOk()

        // Confirm TimePickerDialog
        uiDevice.wait(Until.hasObject(By.text("OK")), 5_000)
        clickNativeOk()
    }

    @Test
    fun createLotteryWorkflow() {
        // Capture original calendar
        val originalCalendar = runBlocking { campsiteRepository.getAvailability("c-123") }
        val originalOpenNights = originalCalendar.openNights.toList()
        val originalHeldNights = originalCalendar.heldNights.toList()

        startApp()

        // 2. Navigate to a campsite
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Banff Spot").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Open campsite details
        composeRule.onNodeWithText("Banff Spot").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Create Lottery").fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Select Create Lottery
        composeRule.onNodeWithText("Create Lottery").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Select Offered Nights").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Select range dates using date pickers
        composeRule.onNodeWithTag("button_range_start").performScrollTo().performClick()
        clickNativeOk()

        composeRule.onNodeWithTag("button_range_end").performScrollTo().performClick()
        clickNativeOk()

        composeRule.onNodeWithText("Load nights").performScrollTo().performClick()
        composeRule.onNodeWithText("Select All").performScrollTo().performClick()

        // 6. Fill in Nights Offered description
        composeRule.onNodeWithTag("tag_nights_offered").performScrollTo().performTextInput("May 2027 weekends")

        // 7. Select Registration & Booking period date/times
        composeRule.onNodeWithTag("button_reg_start").performScrollTo().performClick()
        selectDateTime("10")

        composeRule.onNodeWithTag("button_reg_end").performScrollTo().performClick()
        selectDateTime("11")

        composeRule.onNodeWithTag("button_book_start").performScrollTo().performClick()
        selectDateTime("12")

        composeRule.onNodeWithTag("button_book_end").performScrollTo().performClick()
        selectDateTime("13")

        // 8. Save the lottery using the specific test tag
        composeRule.onNodeWithTag("button_save_lottery").performScrollTo().performClick()

        // Wait until repository actually saves it or displays list
        composeRule.waitUntil(5_000) {
            runBlocking { lotteryRepository.getLotteriesForCampsite("c-123") }.isNotEmpty()
        }

        // 9. Verify that the lottery appears in the appropriate campsite lottery list
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lotteries").fetchSemanticsNodes().isNotEmpty()
        }

        // 10. Verify that the selected nights and description are saved correctly
        val lotteries = runBlocking { lotteryRepository.getLotteriesForCampsite("c-123") }
        assertEquals(1, lotteries.size)
        val lottery = lotteries.first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Calendar.getInstance().time)
        assertEquals(listOf(todayStr), lottery.nightsOffered)
        assertEquals("May 2027 weekends", lottery.nightsDescription)
        assertTrue(lottery.registrationStart > System.currentTimeMillis())
        assertTrue(lottery.registrationEnd > lottery.registrationStart)
        assertTrue(lottery.bookingStart >= lottery.registrationEnd)
        assertTrue(lottery.bookingEnd > lottery.bookingStart)

        // 11. Verify that the campsite availability was exactly unmodified
        val calendar = runBlocking { campsiteRepository.getAvailability("c-123") }
        assertEquals(originalOpenNights, calendar.openNights)
        assertEquals(originalHeldNights, calendar.heldNights)
    }

    @Test
    fun lotteryForm_waitlistCapacityInput_showsInlineErrorOnInvalidInput() {
        val controller = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = lotteryRepository,
            ownerId = ownerId,
            campsiteId = "c-123"
        )

        composeRule.setContent {
            VipersTheme {
                LotteryFormScreen(
                    campsiteId = "c-123",
                    onBack = {},
                    controller = controller
                )
            }
        }

        // Initially field is displayed with no error
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().assertIsDisplayed()

        // Enter non-numeric text
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextInput("abc")
        composeRule.onNodeWithText("Waitlist limit must be a whole number at least 1").performScrollTo().assertIsDisplayed()

        // Clear and enter zero
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextInput("0")
        composeRule.onNodeWithText("Waitlist limit must be a whole number at least 1").performScrollTo().assertIsDisplayed()

        // Clear and enter decimal
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextInput("3.5")
        composeRule.onNodeWithText("Waitlist limit must be a whole number at least 1").performScrollTo().assertIsDisplayed()

        // Clear and enter valid positive integer
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().performTextInput("25")
        composeRule.onAllNodesWithText("Waitlist limit must be a whole number at least 1").assertCountEquals(0)
    }

    @Test
    fun joinButton_visibleForEligibleEntrant() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-open",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 10,
        )
        val fakeLotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val fakeProfileRepo = FakeProfileRepository()

        // Entrant viewing
        val entrantController = LotteryDetailsController(
            lotteryRepository = fakeLotteryRepo,
            profileRepository = fakeProfileRepo,
            lotteryId = "lottery-open",
            currentUserId = "entrant-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-open",
                    onBack = {},
                    controller = entrantController
                )
            }
        }

        composeRule.onNodeWithText("Join Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Edit Lottery").assertDoesNotExist()
    }

    @Test
    fun joinButton_hiddenForOwner() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-open-owner",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 10,
        )
        val fakeLotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val fakeProfileRepo = FakeProfileRepository()

        // Owner viewing their own lottery
        val ownerController = LotteryDetailsController(
            lotteryRepository = fakeLotteryRepo,
            profileRepository = fakeProfileRepo,
            lotteryId = "lottery-open-owner",
            currentUserId = "owner-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-open-owner",
                    onBack = {},
                    controller = ownerController
                )
            }
        }

        composeRule.onNodeWithText("Join Waiting List").assertDoesNotExist()
        composeRule.onNodeWithText("Edit Lottery").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun groupSizeDialog_andConfirmation_joinsWaitingList() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-open-2",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 10,
        )
        val fakeLotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val fakeProfileRepo = FakeProfileRepository()
        val fakeCampsiteRepo = FakeCampsiteRepository(listOf(Campsite(id = "c-123", maxOccupancy = 6)))

        val controller = LotteryDetailsController(
            lotteryRepository = fakeLotteryRepo,
            profileRepository = fakeProfileRepo,
            lotteryId = "lottery-open-2",
            currentUserId = "entrant-phone",
            campsiteRepository = fakeCampsiteRepo,
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-open-2",
                    onBack = {},
                    controller = controller
                )
            }
        }

        // Tap Join Waiting List
        composeRule.onNodeWithText("Join Waiting List").performScrollTo().performClick()

        // Dialog appears with default "1"
        composeRule.onNodeWithText("Group size").assertIsDisplayed()

        // Enter group size 3
        composeRule.onNodeWithText("Group size").performTextClearance()
        composeRule.onNodeWithText("Group size").performTextInput("3")

        // Confirm join
        composeRule.onNodeWithText("Confirm").performClick()

        // Success state shown
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("On Waiting List").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("On Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Group size: 3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Successfully joined the waiting list!").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun alreadyJoined_showsOnWaitingListAndGroupSize() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-open-3",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 10,
        )
        val existingEntry = LotteryEntry(
            id = LotteryEntry.idFor("lottery-open-3", "entrant-phone"),
            lotteryId = "lottery-open-3",
            userId = "entrant-phone",
            groupSize = 4,
            joinedAt = now - 5_000,
            status = EntryStatus.WAITING,
        )
        val fakeLotteryRepo = FakeLotteryRepository(
            initialLotteries = listOf(lottery),
            initialEntries = listOf(existingEntry)
        )
        val fakeProfileRepo = FakeProfileRepository()

        val controller = LotteryDetailsController(
            lotteryRepository = fakeLotteryRepo,
            profileRepository = fakeProfileRepo,
            lotteryId = "lottery-open-3",
            currentUserId = "entrant-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-open-3",
                    onBack = {},
                    controller = controller
                )
            }
        }

        composeRule.onNodeWithText("On Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Group size: 4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Join Waiting List").assertDoesNotExist()
    }

    @Test
    fun fullLottery_showsWaitingListFull() {
        val now = System.currentTimeMillis()
        val fullLottery = Lottery(
            id = "lottery-full",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 1,
        )
        val otherEntry = LotteryEntry(
            id = LotteryEntry.idFor("lottery-full", "other-entrant"),
            lotteryId = "lottery-full",
            userId = "other-entrant",
            groupSize = 2,
            joinedAt = now - 5_000,
            status = EntryStatus.WAITING,
        )
        val fullRepo = FakeLotteryRepository(
            initialLotteries = listOf(fullLottery),
            initialEntries = listOf(otherEntry)
        )
        val fullController = LotteryDetailsController(
            lotteryRepository = fullRepo,
            profileRepository = FakeProfileRepository(),
            lotteryId = "lottery-full",
            currentUserId = "entrant-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-full",
                    onBack = {},
                    controller = fullController
                )
            }
        }

        composeRule.onNodeWithText("Waiting List Full").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Join Waiting List").assertDoesNotExist()
    }

    @Test
    fun closedLottery_showsRegistrationClosed() {
        val now = System.currentTimeMillis()
        val closedLottery = Lottery(
            id = "lottery-closed",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 200_000,
            registrationEnd = now - 100_000,
            bookingStart = now - 100_000,
            bookingEnd = now - 10_000,
            waitlistCapacity = 10,
        )
        val closedRepo = FakeLotteryRepository(initialLotteries = listOf(closedLottery))
        val closedController = LotteryDetailsController(
            lotteryRepository = closedRepo,
            profileRepository = FakeProfileRepository(),
            lotteryId = "lottery-closed",
            currentUserId = "entrant-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-closed",
                    onBack = {},
                    controller = closedController
                )
            }
        }

        composeRule.onNodeWithText("Registration Closed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Join Waiting List").assertDoesNotExist()
    }

    @Test
    fun joinErrorFeedback_displaysErrorMessageAndAllowsRetry() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-error",
            ownerId = "owner-phone",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 100_000,
            bookingEnd = now + 200_000,
            waitlistCapacity = 10,
        )
        // A repo that fails when saving an entry
        val failingRepo = object : LotteryRepository by FakeLotteryRepository(initialLotteries = listOf(lottery)) {
            override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult {
                throw IllegalStateException("Network failure")
            }
        }

        val controller = LotteryDetailsController(
            lotteryRepository = failingRepo,
            profileRepository = FakeProfileRepository(),
            lotteryId = "lottery-error",
            currentUserId = "entrant-phone",
            campsiteRepository = FakeCampsiteRepository(listOf(Campsite(id = "c-123", maxOccupancy = 6))),
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-error",
                    onBack = {},
                    controller = controller
                )
            }
        }

        composeRule.onNodeWithText("Join Waiting List").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm").performClick()

        // Wait for error feedback
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Unable to join waiting list. Please try again.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Unable to join waiting list. Please try again.").performScrollTo().assertIsDisplayed()

        // Join button is still available to allow retry
        composeRule.onNodeWithText("Join Waiting List").performScrollTo().assertIsDisplayed()
    }
}
