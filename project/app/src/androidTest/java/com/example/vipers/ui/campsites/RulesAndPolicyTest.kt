/*
 * RulesAndPolicyTest.kt
 * Intent tests for T-11: an owner adds or edits a campsite's rules and cancellation policy on the
 * campsite form (mockups O02 and O05) and sees them on the campsite's screen (mockup O04), which
 * leaves both out when the campsite has neither. Uses fake repositories, so it doesn't touch
 * Firestore.
 * Known issues: the Reserve screen (T-03, #70) isn't built yet, so there's no test that it shows
 * the rules and cancellation policy. #70 adds that test when it shows RulesAndPolicySection.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Rule
import org.junit.Test

class RulesAndPolicyTest {

    @get:Rule
    val composeRule = createComposeRule()

    // the Hinton campsite from scenario 2 in the project description, owned by this device
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
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

    /** From My Campsites, opens the campsite called [name] once it's listed. */
    private fun openCampsite(name: String = "Hinton Ridge Campground") {
        waitForText(name)
        composeRule.onNodeWithText(name).performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
    }

    @Test
    fun rulesAndPolicyAddedOnTheFormShowOnTheCampsiteScreen() {
        val repo = openApp()
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithContentDescription("Add campsite").performClick()

        type("field_name", "Hinton Ridge Campground")
        type("field_address", "Hinton, AB")
        composeRule.onNodeWithTag("field_region").performScrollTo().performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "6")
        type("field_price", "65") // required since T-09
        type("field_rules", "Quiet hours after 10 PM")
        type("field_cancellation_policy", "Free up to 7 days before")
        composeRule.onNodeWithTag("button_publish").performClick()

        openCampsite()
        assertShown("Rules: Quiet hours after 10 PM")
        assertShown("Cancellation: Free up to 7 days before")
        val saved = runBlocking { repo.getCampsitesByOwner("my-phone") }.single()
        assertEquals("Quiet hours after 10 PM", saved.rules)
        assertEquals("Free up to 7 days before", saved.cancellationPolicy)
    }

    @Test
    fun editedRulesShowOnTheCampsiteScreenAfterSaving() {
        val repo = openApp(listOf(hinton))
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        openCampsite()
        composeRule.onNodeWithTag("button_edit").performClick()
        composeRule.onNodeWithText("Edit Campsite").assertIsDisplayed()

        composeRule.onNodeWithTag("field_rules").performScrollTo()
            .performTextReplacement("No pets, quiet hours after 11 PM")
        composeRule.onNodeWithTag("button_publish").performClick()

        // back on the campsite screen, which reloads the saved values
        waitForText("Rules: No pets, quiet hours after 11 PM")
        assertShown("Rules: No pets, quiet hours after 11 PM")
        composeRule.onNodeWithText("Rules: Quiet hours after 10 PM").assertDoesNotExist()
        assertShown("Cancellation: Free up to 7 days before") // unchanged
        assertEquals(
            hinton.copy(rules = "No pets, quiet hours after 11 PM"),
            runBlocking { repo.getCampsite("hinton") },
        )
    }

    @Test
    fun campsiteWithNeitherShowsNoRulesOrCancellationLines() {
        openApp(listOf(hinton.copy(rules = "", cancellationPolicy = "   ")))
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        openCampsite()

        assertShown("Details") // the section they would be in has loaded
        composeRule.onNodeWithText("Rules:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cancellation:", substring = true).assertDoesNotExist()
    }
}
