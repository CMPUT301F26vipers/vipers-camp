/*
 * AddCampsiteTest.kt
 * Intent tests for US 02.01.01 and 02.01.02a: the Add Campsite form, one test per acceptance
 * criterion. Uses fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddCampsiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repo = FakeCampsiteRepository()
    private var leftForm = false
    private var publishedCalled = false

    /** Opens just the form, with a fake repository. */
    private fun openForm() {
        val controller = CampsiteFormController(repo, ownerId = "my-phone")
        composeRule.setContent {
            VipersTheme {
                CampsiteFormScreen(
                    onBack = { leftForm = true },
                    onPublished = { publishedCalled = true },
                    controller = controller,
                )
            }
        }
    }

    private fun type(tag: String, text: String) =
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun fillValidForm() {
        type("field_name", "Hinton Ridge Campground")
        type("field_address", "Hinton, AB")
        composeRule.onNodeWithTag("field_region").performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "6")
    }

    private fun publish() = composeRule.onNodeWithTag("button_publish").performClick()

    @Test
    fun validFormSavesTheCampsiteAndCloses() {
        openForm()
        fillValidForm()
        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        val saved = runBlocking { repo.getCampsitesByOwner("my-phone") }
        assertEquals(listOf("Hinton Ridge Campground"), saved.map { it.name })
    }

    @Test
    fun missingRequiredFieldsShowErrorsAndSaveNothing() {
        openForm()
        publish()

        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a location").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a region").assertIsDisplayed()
        composeRule.onNodeWithText("Must be at least 1").assertIsDisplayed()
        assertTrue(runBlocking { repo.getAllCampsites() }.isEmpty())
        assertFalse(publishedCalled)
    }

    @Test
    fun maxGuestsMustBeAWholeNumber() {
        openForm()
        fillValidForm()
        type("field_max_guests", ".5") // now "6.5"
        publish()

        composeRule.onNodeWithText("Enter a whole number").assertIsDisplayed()
        assertTrue(runBlocking { repo.getAllCampsites() }.isEmpty())
    }

    @Test
    fun tagsCanBeAddedAndRemoved() {
        openForm()
        type("field_tag", "Cookhouse")
        composeRule.onNodeWithTag("button_add_tag").performClick()
        composeRule.onNodeWithText("Cookhouse").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Remove Cookhouse").performClick()
        composeRule.onNodeWithText("Cookhouse").assertDoesNotExist()
    }

    @Test
    fun leavingAFilledFormAsksFirst() {
        openForm()
        type("field_name", "Hinton Ridge Campground")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Discard this campsite?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        assertFalse(leftForm)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        assertTrue(leftForm)
    }

    @Test
    fun leavingAnEmptyFormDoesNotAsk() {
        openForm()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(leftForm)
        composeRule.onNodeWithText("Discard this campsite?").assertDoesNotExist()
    }

    @Test
    fun newCampsiteShowsInMyCampsitesAndSearch() {
        // the whole app, with fake repositories in place of Firestore
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

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithContentDescription("Add campsite").performClick()
        fillValidForm()
        publish()

        // back on My Campsites, with the new campsite listed
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Hinton Ridge Campground").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("My Campsites").assertIsDisplayed()

        // and entrants can find it in search
        composeRule.onNodeWithTag("tab_${Routes.HOME}").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Hinton Ridge Campground").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
