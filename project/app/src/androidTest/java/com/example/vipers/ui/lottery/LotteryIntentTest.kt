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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
        // Advance to next month (setting day 1 first to avoid month-length rollover)
        val nextMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        val offeredNightCal = Calendar.getInstance().apply {
            time = nextMonthCal.time
            set(Calendar.DAY_OF_MONTH, 20)
        }

        val openNights = listOf(dateFormat.format(offeredNightCal.time))

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

    private fun selectDate(day: String, advanceMonth: Boolean = true) {
        composeRule.waitForIdle()
        uiDevice.waitForIdle()
        uiDevice.wait(Until.hasObject(By.text("OK")), 5_000)

        if (advanceMonth) {
            val nextBtn = uiDevice.findObject(By.desc("Next month"))
                ?: uiDevice.findObject(By.res("android:id/next"))
            nextBtn?.click()
            uiDevice.waitForIdle()
        }

        val dayNode = uiDevice.findObject(By.text(day))
        dayNode?.click()
        uiDevice.waitForIdle()

        clickNativeOk()
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
        selectDate("20", advanceMonth = true)

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
        val targetMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        val expectedOfferedNight = Calendar.getInstance().apply {
            time = targetMonthCal.time
            set(Calendar.DAY_OF_MONTH, 20)
        }
        val expectedNightStr = dateFormat.format(expectedOfferedNight.time)
        assertEquals(listOf(expectedNightStr), lottery.nightsOffered)
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

    @Test
    fun leaveWaitingList_dialogAndConfirmation_removesEntrantAndAllowsRejoining() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-leave-1",
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
            id = LotteryEntry.idFor("lottery-leave-1", "entrant-phone"),
            lotteryId = "lottery-leave-1",
            userId = "entrant-phone",
            groupSize = 4,
            joinedAt = now - 5_000,
            status = EntryStatus.WAITING,
        )
        val fakeLotteryRepo = FakeLotteryRepository(
            initialLotteries = listOf(lottery),
            initialEntries = listOf(existingEntry)
        )
        val controller = LotteryDetailsController(
            lotteryRepository = fakeLotteryRepo,
            profileRepository = FakeProfileRepository(),
            lotteryId = "lottery-leave-1",
            currentUserId = "entrant-phone",
            campsiteRepository = FakeCampsiteRepository(listOf(Campsite(id = "c-123", maxOccupancy = 6))),
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-leave-1",
                    onBack = {},
                    controller = controller
                )
            }
        }

        // Verify joined status and Leave button
        composeRule.onNodeWithText("On Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Group size: 4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Leave Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Join Waiting List").assertDoesNotExist()

        // Open dialog and test Stay
        composeRule.onNodeWithText("Leave Waiting List").performClick()
        composeRule.onNodeWithText("Leave waiting list?").assertIsDisplayed()
        composeRule.onNodeWithText("You will not be in the draw for this campsite.").assertIsDisplayed()
        composeRule.onNodeWithText("Stay").performClick()
        composeRule.onNodeWithText("Leave waiting list?").assertDoesNotExist()

        // Open dialog and confirm Leave
        composeRule.onNodeWithText("Leave Waiting List").performScrollTo().performClick()
        composeRule.onNodeWithText("Leave").performClick()

        // Verify success banner and updated state allowing rejoin
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Successfully left the waiting list.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Successfully left the waiting list.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Leave Waiting List").assertDoesNotExist()
        composeRule.onNodeWithText("On Waiting List").assertDoesNotExist()
        composeRule.onNodeWithText("Join Waiting List").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun leaveWaitingList_onRepositoryFailure_showsErrorAndPreservesState() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-leave-2",
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
            id = LotteryEntry.idFor("lottery-leave-2", "entrant-phone"),
            lotteryId = "lottery-leave-2",
            userId = "entrant-phone",
            groupSize = 2,
            joinedAt = now - 5_000,
            status = EntryStatus.WAITING,
        )
        val failingRepo = object : LotteryRepository by FakeLotteryRepository(
            initialLotteries = listOf(lottery),
            initialEntries = listOf(existingEntry)
        ) {
            override suspend fun leaveWaitingList(lotteryId: String, userId: String) {
                throw IllegalStateException("Network failure")
            }
        }
        val controller = LotteryDetailsController(
            lotteryRepository = failingRepo,
            profileRepository = FakeProfileRepository(),
            lotteryId = "lottery-leave-2",
            currentUserId = "entrant-phone",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "lottery-leave-2",
                    onBack = {},
                    controller = controller
                )
            }
        }

        composeRule.onNodeWithText("Leave Waiting List").performScrollTo().performClick()
        composeRule.onNodeWithText("Leave").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Couldn't leave the waiting list. Please try again.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Couldn't leave the waiting list. Please try again.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Leave Waiting List").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("On Waiting List").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editLottery_navigatesFromDetailsAndPrefillsSavedValues() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-edit-1",
            ownerId = ownerId,
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "June weekend getaway",
            registrationStart = now + 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 120_000,
            bookingEnd = now + 240_000,
            winnersCount = 3,
            waitlistCapacity = 25,
            waitingUserIds = listOf("entrant-1", "entrant-2")
        )
        val lotteryId = runBlocking { lotteryRepository.addLottery(lottery) }

        startApp()

        // 1. Navigate to Campsites tab -> Banff Spot
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Banff Spot").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Banff Spot").performClick()

        // 2. Open Lottery Details from owner's campsite screen
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lottery: June weekend getaway").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lottery: June weekend getaway").performClick()

        // 3. Verify on Lottery Details and click Edit Lottery
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit Lottery").performClick()

        // 4. Verify on Edit Lottery screen with pre-filled values
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit Lottery").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tag_nights_offered").performScrollTo().assertTextContains("June weekend getaway")
        composeRule.onNodeWithTag("tag_winners_count").performScrollTo().assertTextContains("3")
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().assertTextContains("25")
    }

    @Test
    fun editLottery_saveEditsUpdatesLotteryAndRefreshesDetails() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-edit-2",
            ownerId = ownerId,
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Original description",
            registrationStart = now + 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 120_000,
            bookingEnd = now + 240_000,
            winnersCount = 2,
            waitlistCapacity = 10,
        )
        val lotteryId = runBlocking { lotteryRepository.addLottery(lottery) }

        startApp()

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Banff Spot").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Banff Spot").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lottery: Original description").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lottery: Original description").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit Lottery").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }

        // Change nights description and winners count
        composeRule.onNodeWithTag("tag_nights_offered").performScrollTo().performTextReplacement("Updated description")
        composeRule.onNodeWithTag("tag_winners_count").performScrollTo().performTextReplacement("5")
        composeRule.onNodeWithText("Save changes").performScrollTo().performClick()

        // Wait for return to Lottery Details
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lottery Details").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lottery Details").assertIsDisplayed()

        // Verify updated lottery details are displayed on return
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Description: Updated description").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Description: Updated description").performScrollTo().assertIsDisplayed()

        // Verify fake repository has the updated data
        val updated = runBlocking { lotteryRepository.getLottery(lotteryId) }
        assertEquals("Updated description", updated?.nightsDescription)
        assertEquals(5, updated?.winnersCount)
    }

    @Test
    fun editLottery_whenClosedOrDrawn_displaysReadOnlyExplanationAndDisablesSave() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-edit-closed",
            ownerId = ownerId,
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Closed lottery",
            registrationStart = now - 50_000,
            registrationEnd = now - 10_000,
            bookingStart = now + 50_000,
            bookingEnd = now + 100_000,
            winnersCount = 2,
        )
        val lotteryId = runBlocking { lotteryRepository.addLottery(lottery) }

        startApp()

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Banff Spot").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Banff Spot").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lottery: Closed lottery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lottery: Closed lottery").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit Lottery").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit Lottery").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify read-only explanation banner is visible
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Registration has closed; this lottery can no longer be edited.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Registration has closed; this lottery can no longer be edited.").assertIsDisplayed()

        // Verify Save changes button is disabled
        composeRule.onNodeWithTag("button_save_lottery").performScrollTo().assertIsNotEnabled()

        // Verify form fields are disabled
        composeRule.onNodeWithTag("tag_nights_offered").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("tag_winners_count").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("tag_waitlist_limit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun editLottery_nonOwnerAccess_showsPermissionError() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-other-owner",
            ownerId = "someone-else",
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Other owner lottery",
            registrationStart = now + 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 120_000,
            bookingEnd = now + 240_000,
        )
        runBlocking { lotteryRepository.addLottery(lottery) }

        // Start directly with LotteryFormScreen in edit mode for this lottery
        composeRule.setContent {
            VipersTheme {
                LotteryFormScreen(
                    lotteryId = "lottery-other-owner",
                    onBack = {}
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("You do not own this lottery or it was not found.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("You do not own this lottery or it was not found.").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertDoesNotExist()
    }

    @Test
    fun editLottery_entrantViewingDetails_seesUpdatedValuesAfterOwnerEdits() {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-entrant-refresh",
            ownerId = ownerId,
            campsiteIds = listOf("c-123"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Early Summer Promo",
            registrationStart = now - 10_000,
            registrationEnd = now + 100_000,
            bookingStart = now + 120_000,
            bookingEnd = now + 240_000,
            winnersCount = 2,
            waitlistCapacity = 15,
        )
        val lotteryId = runBlocking { lotteryRepository.addLottery(lottery) }

        // Entrant opens details controller
        val entrantController = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = FakeProfileRepository(),
            lotteryId = lotteryId,
            currentUserId = "entrant-user",
        )

        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = lotteryId,
                    onBack = {},
                    controller = entrantController
                )
            }
        }

        // Entrant views initial lottery details
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Description: Early Summer Promo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Description: Early Summer Promo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Waiting List Entrants: 0 / 15").performScrollTo().assertIsDisplayed()

        // Owner saves edits to the lottery in repository
        val updated = lottery.copy(
            id = lotteryId,
            nightsDescription = "Updated Summer Special",
            waitlistCapacity = 20,
            winnersCount = 4
        )
        runBlocking { lotteryRepository.updateLottery(updated, callerId = ownerId) }

        // Entrant refreshes details (e.g. reopening or onResume triggered)
        entrantController.loadData()

        // Verify updated values are displayed to the entrant
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Description: Updated Summer Special").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Description: Updated Summer Special").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Waiting List Entrants: 0 / 20").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Description: Early Summer Promo").assertDoesNotExist()

        // Verify persisted repository values
        val persisted = runBlocking { lotteryRepository.getLottery(lotteryId) }
        assertEquals("Updated Summer Special", persisted?.nightsDescription)
        assertEquals(20, persisted?.waitlistCapacity)
        assertEquals(4, persisted?.winnersCount)
    }
}
