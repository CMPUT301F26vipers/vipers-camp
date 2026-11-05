/*
 * LotteryIntentTest.kt
 * Integration / Intent tests for creating and viewing campsite lotteries.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
}
