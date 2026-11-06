/*
 * SetAvailableNightsTest.kt
 * Intent tests for T-10: the owner opens Availability from My Campsite (mockup O07), opens and
 * closes nights one at a time or as a date range, and saves them; booked and past nights don't
 * change. One test per acceptance criterion plus saving. Uses the fake repositories, so it doesn't
 * touch Firestore.
 * Known issues: the search filter (01.01.02) and Reserve screen (01.04.01) that read the saved
 * nights are tested by those stories.
 */
package com.example.vipers.ui.campsites

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class SetAvailableNightsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // the screen checks nights against the real date, so the tests use next month's nights
    private val today = LocalDate.now()
    private val nextMonth = YearMonth.from(today).plusMonths(1)
    private fun night(day: Int): String = nextMonth.atDay(day).toString()

    private val hinton = Campsite(
        id = "hinton", ownerId = "test-device", name = "Hinton Ridge Campground",
        address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 8,
    )

    /** Shows Availability for the Hinton campsite with [calendar] saved; returns the repository. */
    private fun openScreen(
        calendar: AvailabilityCalendar = AvailabilityCalendar("hinton"),
    ): FakeCampsiteRepository {
        val repository = FakeCampsiteRepository(listOf(hinton))
        runBlocking { repository.saveAvailability(calendar) }
        val controller = AvailabilityController(repository, "hinton")
        composeRule.setContent {
            VipersTheme { AvailabilityScreen("hinton", onBack = {}, controller = controller) }
        }
        return repository
    }

    private fun showNextMonth() =
        composeRule.onNodeWithContentDescription("Next month").performClick()

    private fun tapNight(night: String) =
        composeRule.onNodeWithTag("night_$night").performScrollTo().performClick()

    /** Checks that [night] shows as "Available", "Closed" or "Booked". */
    private fun assertNight(night: String, state: String) =
        composeRule.onNodeWithTag("night_$night")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private fun assertMessage(text: String) =
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    @Test
    fun availabilityOpensFromMyCampsite() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma")),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton)),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithTag("button_availability").performClick()

        composeRule.onNodeWithText("Availability").assertIsDisplayed()
        val thisMonth = "${today.month.getDisplayName(TextStyle.FULL, Locale.US)} ${today.year}"
        composeRule.onNodeWithText(thisMonth).assertIsDisplayed()
        composeRule.onNodeWithText("+ Open a range").assertIsDisplayed()
        composeRule.onNodeWithText("− Close a range").assertIsDisplayed()
    }

    @Test
    fun tappingANightTogglesIt() {
        openScreen()
        showNextMonth()
        assertNight(night(10), "Closed")
        tapNight(night(10))
        assertNight(night(10), "Available")
        tapNight(night(10))
        assertNight(night(10), "Closed")
    }

    @Test
    fun aDateRangeOpensEveryNightInIt() {
        openScreen()
        showNextMonth()
        composeRule.onNodeWithTag("button_open_range").performClick()
        tapNight(night(5))
        tapNight(night(7))
        assertNight(night(5), "Available")
        assertNight(night(6), "Available")
        assertNight(night(7), "Available")
        assertNight(night(8), "Closed")
        // the range is done, so the buttons are back
        composeRule.onNodeWithText("+ Open a range").assertIsDisplayed()
    }

    @Test
    fun aCloseRangeClosesEveryNightButTheBookedOnes() {
        val open = listOf(night(5), night(6), night(7), night(8))
        openScreen(AvailabilityCalendar("hinton", openNights = open, heldNights = listOf(night(6))))
        showNextMonth()
        composeRule.onNodeWithTag("button_close_range").performClick()
        tapNight(night(5))
        tapNight(night(7))
        assertNight(night(5), "Closed")
        assertNight(night(6), "Booked")
        assertNight(night(7), "Closed")
        assertNight(night(8), "Available")
        assertMessage("Booked nights in that range stay open.")
    }

    @Test
    fun saveStoresTheOpenNights() {
        val repository = openScreen()
        showNextMonth()
        tapNight(night(10))
        tapNight(night(11))
        composeRule.onNodeWithTag("button_save_availability").performClick()
        assertMessage("Availability saved")
        val saved = runBlocking { repository.getAvailability("hinton") }
        assertEquals(listOf(night(10), night(11)), saved.openNights)
    }

    @Test
    fun aBookedNightCantBeClosed() {
        val reserved = listOf(night(12))
        openScreen(AvailabilityCalendar("hinton", openNights = reserved, heldNights = reserved))
        showNextMonth()
        assertNight(night(12), "Booked")
        tapNight(night(12))
        assertNight(night(12), "Booked")
        assertMessage("This night is booked. Reject the reservation first to close it.")
    }

    @Test
    fun pastNightsCantBeChanged() {
        val yesterday = today.minusDays(1)
        openScreen()
        // on the 1st, yesterday is in last month
        if (YearMonth.from(yesterday) != YearMonth.from(today)) {
            composeRule.onNodeWithContentDescription("Previous month").performClick()
        }
        tapNight(yesterday.toString())
        assertNight(yesterday.toString(), "Closed")
        assertMessage("Past nights can't be changed.")
    }
}
