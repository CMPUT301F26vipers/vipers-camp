/*
 * LotteryIntentTest.kt
 * Integration / Intent tests for creating and viewing campsite lotteries.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LotteryIntentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
            composeRule.onAllNodesWithText("Nights Offered").fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Select night range and load nights
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        composeRule.onNodeWithTag("tag_range_start").performTextInput(dateFormat.format(today.time))
        composeRule.onNodeWithTag("tag_range_end").performTextInput(dateFormat.format(tomorrow.time))
        composeRule.onNodeWithText("Load nights").performClick()
        composeRule.onNodeWithText("Select All").performClick()

        // 6. Fill in Nights Offered description
        composeRule.onNodeWithTag("tag_nights_offered").performTextInput("May 2027 weekends")

        // 7. Fill in Registration & Booking Date/Time pickers
        composeRule.onNodeWithTag("button_reg_start").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithTag("button_reg_end").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithTag("button_book_start").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithTag("button_book_end").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("OK").performClick()

        // 8. Save the lottery using the specific test tag
        composeRule.onNodeWithTag("button_save_lottery").performClick()

        // Wait until repository actually saves it
        composeRule.waitUntil(5_000) {
            runBlocking { lotteryRepository.getLotteriesForCampsite("c-123") }.isNotEmpty()
        }

        // 9. Verify that the lottery appears in the appropriate campsite lottery list
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Lotteries").fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for lottery item and expected status badge
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Registration is open", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Registration is open", substring = true).assertIsDisplayed()

        // 10. Verify that the selected nights and description are saved correctly in the exact lottery
        val lotteries = runBlocking { lotteryRepository.getLotteriesForCampsite("c-123") }
        assertEquals(1, lotteries.size)
        val todayStr = dateFormat.format(today.time)
        val tomorrowStr = dateFormat.format(tomorrow.time)
        assertEquals(listOf(todayStr, tomorrowStr), lotteries.first().nightsOffered)
        assertEquals("May 2027 weekends", lotteries.first().nightsDescription)

        // 11. Verify that the campsite availability was exactly unmodified
        val calendar = runBlocking { campsiteRepository.getAvailability("c-123") }
        assertEquals(originalOpenNights, calendar.openNights)
        assertEquals(originalHeldNights, calendar.heldNights)
    }
}
