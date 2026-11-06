/*
 * ViewCampsiteDetailsTest.kt
 * Intent tests for US 01.01.05: an entrant taps a search result on Home and sees the campsite's
 * details and its active lottery (mockups E05, E05b), one test per acceptance criterion plus a
 * few edge cases. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Rule
import org.junit.Test

class ViewCampsiteDetailsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // the Hinton campsite from scenario 2 in the project description; 46 stars over 10 ratings
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse and an on-site tennis court.",
        maxOccupancy = 8,
        tags = listOf("Cookhouse", "Tennis court"),
        pricePerNightCents = 6500,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
        ratingSum = 46,
        ratingCount = 10,
    )
    private val banff = Campsite(
        id = "banff", name = "Bow River Sites", address = "Banff, AB", region = Region.BANFF,
        maxOccupancy = 4,
    )

    // Home checks the lotteries against the real clock, so the periods are set around today
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    // registration opened 2 days ago and closes in 3 days
    private val openLottery = Lottery(
        id = "may", campsiteIds = listOf("hinton"), nightsDescription = "May 2027 weekends",
        registrationStart = now - 2 * day, registrationEnd = now + 3 * day,
        bookingStart = now + 5 * day, bookingEnd = now + 30 * day,
    )

    /** Shows Campsite Details for [campsiteId], loading from [campsites] and [lotteries]. */
    private fun openScreen(
        campsites: List<Campsite> = listOf(hinton),
        lotteries: List<Lottery> = emptyList(),
        campsiteId: String = "hinton",
    ) {
        val controller = CampsiteDetailsController(
            FakeCampsiteRepository(campsites),
            FakeLotteryRepository(lotteries),
            campsiteId,
        )
        composeRule.setContent {
            VipersTheme { CampsiteDetailsScreen(campsiteId, onBack = {}, controller = controller) }
        }
    }

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    private fun assertNotShown(text: String) =
        composeRule.onNodeWithText(text, substring = true).assertDoesNotExist()

    @Test
    fun tappingAResultOpensItsDetailsAndBackKeepsTheSearch() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton, banff)),
            lotteryRepository = FakeLotteryRepository(listOf(openLottery)),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }

        composeRule.onNodeWithTag("search_field").performTextInput("Hinton")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("Campsite").assertIsDisplayed()
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")

        composeRule.onNodeWithContentDescription("Back").performClick()
        // back on Home with the same search: only the Hinton result
        composeRule.onNodeWithTag("search_field").assert(hasText("Hinton"))
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText("Bow River Sites").assertDoesNotExist()
    }

    @Test
    fun screenShowsAllTheCampsitesDetails() {
        openScreen()
        assertShown("Hinton Ridge Campground")
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")
        assertShown("★ 4.6 (10)")
        assertShown("Outdoor cookhouse and an on-site tennis court.")
        assertShown("Cookhouse")
        assertShown("Tennis court")
        assertShown("Region: Hinton")
        assertShown("Rules: Quiet hours after 10 PM")
        assertShown("Cancellation: Free up to 7 days before")
    }

    @Test
    fun emptyOptionalFieldsAreHiddenWithoutErrors() {
        val bare = hinton.copy(
            description = "", tags = emptyList(), rules = "", cancellationPolicy = "",
            ratingSum = 0, ratingCount = 0,
        )
        openScreen(campsites = listOf(bare))
        assertShown("Hinton Ridge Campground")
        assertShown("No ratings yet")
        assertNotShown("Rules:")
        assertNotShown("Cancellation:")
        assertNotShown("Cookhouse")
    }

    @Test
    fun activeLotteryShowsItsRegistrationDeadline() {
        openScreen(lotteries = listOf(openLottery))
        assertShown("Lottery open until ${formatDate(openLottery.registrationEnd)}")
        assertShown("Available: May 2027 weekends")
    }

    @Test
    fun activeLotteryWithCapacityShowsSpotsRemaining() {
        val lotteryWithCapacity = openLottery.copy(
            waitlistCapacity = 20,
            waitingUserIds = listOf("u1", "u2", "u3", "u4", "u5", "u6"),
        )
        openScreen(lotteries = listOf(lotteryWithCapacity))
        assertShown("Lottery open until ${formatDate(openLottery.registrationEnd)} · 14 spots left")
        assertShown("Available: May 2027 weekends")
    }

    @Test
    fun activeLotteryWithOneSpotRemainingShowsSingularText() {
        val oneSpotLottery = openLottery.copy(
            waitlistCapacity = 3,
            waitingUserIds = listOf("u1", "u2"),
        )
        openScreen(lotteries = listOf(oneSpotLottery))
        assertShown("Lottery open until ${formatDate(openLottery.registrationEnd)} · 1 spot left")
    }

    @Test
    fun activeLotteryAtCapacityShowsZeroSpotsLeft() {
        val fullLottery = openLottery.copy(
            waitlistCapacity = 2,
            waitingUserIds = listOf("u1", "u2"),
        )
        openScreen(lotteries = listOf(fullLottery))
        assertShown("Lottery open until ${formatDate(openLottery.registrationEnd)} · 0 spots left")
    }

    @Test
    fun campsiteWithoutAnOpenLotterySaysNoActiveLottery() {
        // registration only starts next week, so entrants can't join yet
        val upcoming = openLottery.copy(
            registrationStart = now + 7 * day, registrationEnd = now + 12 * day,
            bookingStart = now + 14 * day, bookingEnd = now + 40 * day,
        )
        openScreen(lotteries = listOf(upcoming))
        assertShown("No active lottery")
        assertNotShown("Lottery open until")
    }

    @Test
    fun campsiteSavedFromTheAddFormShowsCorrectly() {
        // what the Add Campsite form saves today: no rules or policy yet (T-11)
        val fromForm = Campsite(
            id = "new", ownerId = "owner-daniel", name = "Kananaskis Lakes Sites",
            address = "Kananaskis Trail, AB", region = Region.KANANASKIS, maxOccupancy = 6,
            description = "Mountain views and hiking trails.", tags = listOf("Hiking", "Playground"),
            pricePerNightCents = 4000,
        )
        openScreen(campsites = listOf(fromForm), campsiteId = "new")
        assertShown("Kananaskis Lakes Sites")
        assertShown("Kananaskis Trail, AB · Up to 6 guests · $40 / night")
        assertShown("Region: Kananaskis")
        assertShown("Mountain views and hiking trails.")
        assertShown("Hiking")
        assertShown("Playground")
    }

    @Test
    fun removedCampsiteSaysNotFound() {
        openScreen(campsites = listOf(hinton.copy(removed = true)))
        composeRule.onNodeWithText("Campsite not found.").assertIsDisplayed()
    }
}
