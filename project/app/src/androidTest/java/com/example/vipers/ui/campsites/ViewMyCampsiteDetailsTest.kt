/*
 * ViewMyCampsiteDetailsTest.kt
 * Intent tests for US 02.01.04: the owner opens one of their campsites from My Campsites and sees
 * all its saved details, its management buttons and its lotteries (mockup O04), one test per
 * acceptance criterion plus a few edge cases. Uses the fake repositories, so it doesn't touch
 * Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ViewMyCampsiteDetailsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // the Hinton campsite from scenario 2 in the project description
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "test-device",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse and an on-site tennis court.",
        maxOccupancy = 8,
        tags = listOf("Cookhouse", "Tennis court"),
        pricePerNightCents = 6500,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
    )

    private val lotteries = listOf(
        Lottery(id = "may", campsiteIds = listOf("hinton"), nightsDescription = "May 2027 weekends"),
    )

    // 3 groups on the May waiting list
    private val entries = listOf("sam", "alex", "jordan").map { userId ->
        LotteryEntry(
            id = LotteryEntry.idFor("may", userId), lotteryId = "may", userId = userId,
            status = EntryStatus.WAITING,
        )
    }

    // what each button was last asked to do
    private var opened = ""

    /** Shows My Campsite for [campsiteId], loading from [campsites] and [lotteryList]. */
    private fun openScreen(
        campsites: CampsiteRepository = FakeCampsiteRepository(listOf(hinton)),
        campsiteId: String = "hinton",
        lotteryList: List<Lottery> = lotteries,
    ): OwnerCampsiteController {
        val controller =
            OwnerCampsiteController(campsites, FakeLotteryRepository(lotteryList, entries), campsiteId)
        composeRule.setContent {
            VipersTheme {
                OwnerCampsiteScreen(
                    campsiteId = campsiteId,
                    onBack = { opened = "back" },
                    onEdit = { opened = "edit" },
                    onOpenAvailability = { opened = "availability" },
                    onOpenReservations = { opened = "reservations" },
                    onCreateLottery = { opened = "create lottery" },
                    onLotteryClick = { lotteryId -> opened = "lottery $lotteryId" },
                    controller = controller,
                )
            }
        }
        return controller
    }

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()

    @Test
    fun tappingACampsiteInMyCampsitesOpensItsDetails() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma")),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton)),
            lotteryRepository = FakeLotteryRepository(lotteries),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) } }

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")

        // Edit and Availability open their screens, and Back comes back here
        tap("button_edit")
        composeRule.onNodeWithText("Editing a campsite is added in US 02.01.03.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        tap("button_availability")
        composeRule.onNodeWithText("Setting available nights is added in T-10.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
    }

    @Test
    fun screenShowsAllTheSavedFields() {
        openScreen()
        assertShown("Hinton Ridge Campground")
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")
        assertShown("Outdoor cookhouse and an on-site tennis court.")
        assertShown("Cookhouse")
        assertShown("Tennis court")
        assertShown("Rules: Quiet hours after 10 PM")
        assertShown("Cancellation: Free up to 7 days before")
    }

    @Test
    fun screenLinksToEditAvailabilityAndReservations() {
        openScreen()
        tap("button_edit")
        assertEquals("edit", opened)
        tap("button_availability")
        assertEquals("availability", opened)
        tap("button_reservations")
        assertEquals("reservations", opened)
        tap("button_create_lottery")
        assertEquals("create lottery", opened)
        // QR codes come with 02.02.01
        composeRule.onNodeWithTag("button_qr_code").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun eachLotteryOpensItsDetails() {
        openScreen()
        composeRule.onNodeWithText("Lottery: May 2027 weekends").performScrollTo().performClick()
        assertEquals("lottery may", opened)
    }

    @Test
    fun eachLotteryShowsItsStatusLikeTheMockup() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        // registration opened yesterday and closes in 3 days
        val open = Lottery(
            id = "june", campsiteIds = listOf("hinton"), nightsDescription = "June 2027 weekends",
            registrationStart = now - day, registrationEnd = now + 3 * day,
            bookingStart = now + 5 * day, bookingEnd = now + 30 * day,
        )
        // "may" has no periods set, so it counts as Closed
        openScreen(lotteryList = lotteries + open)
        assertShown("Open")
        assertShown("Closed")
    }

    @Test
    fun priceIsLeftOutWhileItIsZero() {
        // campsites added in the app have no price until T-09 adds it to the form
        openScreen(FakeCampsiteRepository(listOf(hinton.copy(pricePerNightCents = 0))))
        assertShown("Hinton, AB · Up to 8 guests")
        composeRule.onNodeWithText("$0", substring = true).assertDoesNotExist()
    }

    @Test
    fun eachLotteryShowsHowManyJoined() {
        openScreen()
        assertShown("3 joined")
    }

    @Test
    fun screenShowsTheLatestSavedValues() {
        // a repository whose campsite changes, like after the owner edits it (US 02.01.03)
        var saved = hinton
        val campsites = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = saved
        }
        val controller = openScreen(campsites)
        assertShown("Hinton Ridge Campground")

        saved = hinton.copy(name = "Hinton Ridge Sites", maxOccupancy = 6)
        // the screen reloads like this when the owner comes back to it
        composeRule.runOnIdle { controller.loadData() }
        assertShown("Hinton Ridge Sites")
        assertShown("Hinton, AB · Up to 6 guests · $65 / night")
    }

    @Test
    fun emptyOptionalFieldsAreLeftOut() {
        val bare = hinton.copy(rules = "", cancellationPolicy = "", tags = emptyList())
        openScreen(FakeCampsiteRepository(listOf(bare)))
        assertShown("Hinton Ridge Campground")
        composeRule.onNodeWithText("Rules:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cancellation:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cookhouse").assertDoesNotExist()
    }

    @Test
    fun missingCampsiteSaysNotFound() {
        openScreen(campsiteId = "missing")
        composeRule.onNodeWithText("Campsite not found.").assertIsDisplayed()
    }
}
