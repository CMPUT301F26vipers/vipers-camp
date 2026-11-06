/*
 * SetNightlyPriceTest.kt
 * Intent tests for T-09: the owner enters a nightly price on the Add Campsite form (mockup O02) or
 * changes it on Edit Campsite (O05), and the price shows on My Campsite (O04) and on the entrant's
 * Campsite Details (E05). Uses fake repositories, so it doesn't touch Firestore.
 * Known issues: the Reserve screen and its total (T-03, 01.04.01) aren't built yet, so they're
 * tested by those stories.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetNightlyPriceTest {

    @get:Rule
    val composeRule = createComposeRule()

    // the Hinton campsite from scenario 2 in the project description, $65 a night
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 6500,
    )

    /** Shows the whole app on this device, with [campsites] as the saved campsites. */
    private fun openApp(campsites: List<Campsite> = emptyList()): FakeCampsiteRepository {
        val repo = FakeCampsiteRepository(campsites)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "my-phone",
            profileRepository = FakeProfileRepository(),
            campsiteRepository = repo,
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) } }
        return repo
    }

    private fun type(tag: String, text: String) =
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()

    /** From My Campsites, opens the campsite called Hinton Ridge Campground once it's listed. */
    private fun openMyCampsite() {
        waitForText("Hinton Ridge Campground")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
    }

    /** Opens Add Campsite and fills in every required field except the price. */
    private fun openAddFormWithoutAPrice() {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithContentDescription("Add campsite").performClick()
        type("field_name", "Hinton Ridge Campground")
        type("field_address", "Hinton, AB")
        composeRule.onNodeWithTag("field_region").performScrollTo().performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "6")
    }

    @Test
    fun priceTypedOnTheAddFormIsSavedAndShownOnMyCampsite() {
        val repo = openApp()
        openAddFormWithoutAPrice()
        type("field_price", "65.50")
        composeRule.onNodeWithTag("button_publish").performClick()

        openMyCampsite()
        assertShown("Hinton, AB · Up to 6 guests · $65.50 / night")
        val saved = runBlocking { repo.getCampsitesByOwner("my-phone") }.single()
        assertEquals(6550L, saved.pricePerNightCents)
    }

    @Test
    fun theFormNeedsAPriceInDollarsAndCents() {
        val repo = openApp()
        openAddFormWithoutAPrice()
        composeRule.onNodeWithTag("button_publish").performClick()
        composeRule.onNodeWithText("Enter a price").performScrollTo().assertIsDisplayed()

        // three decimal places isn't dollars and cents
        type("field_price", "65.505")
        composeRule.onNodeWithTag("button_publish").performClick()
        composeRule.onNodeWithText("Enter dollars, e.g. 65.50").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { repo.getAllCampsites() }.isEmpty())
    }

    @Test
    fun editShowsTheSavedPriceAndSavesTheNewOne() {
        val repo = openApp(listOf(hinton))
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        openMyCampsite()
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")
        composeRule.onNodeWithTag("button_edit").performClick()

        composeRule.onNodeWithTag("field_price").performScrollTo().assertTextContains("65")
        composeRule.onNodeWithTag("field_price").performTextReplacement("70")
        composeRule.onNodeWithTag("button_publish").performClick()

        // back on My Campsite, which reloads the saved price
        waitForText("Hinton, AB · Up to 8 guests · $70 / night")
        assertShown("Hinton, AB · Up to 8 guests · $70 / night")
        assertEquals(hinton.copy(pricePerNightCents = 7000), runBlocking { repo.getCampsite("hinton") })
    }

    @Test
    fun entrantsSeeThePriceOnCampsiteDetails() {
        // someone else's campsite, found from the search on Home
        openApp(listOf(hinton.copy(ownerId = "owner-priya")))
        composeRule.onNodeWithTag("search_field").performTextInput("Hinton")
        waitForText("Hinton Ridge Campground")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()

        composeRule.onNodeWithText("Campsite").assertIsDisplayed()
        assertShown("Hinton, AB · Up to 8 guests · $65 / night")
    }
}
