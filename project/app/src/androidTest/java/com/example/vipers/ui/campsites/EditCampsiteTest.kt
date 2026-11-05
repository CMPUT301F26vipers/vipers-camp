/*
 * EditCampsiteTest.kt
 * Intent tests for US 02.01.03: the owner opens the form from their campsite with its details
 * filled in, saves a change and sees it on the campsite screen. Only the owner gets the Edit
 * button. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditCampsiteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // the Hinton campsite from scenario 2 in the project description, owned by this device
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
        ratingSum = 46,
        ratingCount = 10,
    )

    /** Shows the whole app on this device, with [campsite] as the only saved campsite. */
    private fun openApp(campsite: Campsite = hinton, isAdmin: Boolean = false): FakeCampsiteRepository {
        val campsites = FakeCampsiteRepository(listOf(campsite))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma", isAdmin = isAdmin)),
            ),
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = isAdmin, onLoadDemoData = {}) } }
        return campsites
    }

    /** From the app's first screen, opens the form for the owner's campsite. */
    private fun openEditForm() {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithTag("button_edit").performClick()
        composeRule.onNodeWithText("Edit Campsite").assertIsDisplayed()
    }

    @Test
    fun editOpensTheFormFilledInWithTheSavedDetails() {
        openApp()
        openEditForm()

        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        composeRule.onNodeWithTag("field_name").assertTextContains("Hinton Ridge Campground")
        composeRule.onNodeWithTag("field_address").assertTextContains("Hinton, AB")
        composeRule.onNodeWithText("Hinton").assertIsDisplayed() // the region dropdown
        composeRule.onNodeWithTag("field_max_guests").assertTextContains("8")
        composeRule.onNodeWithTag("field_description")
            .assertTextContains("Outdoor cookhouse and an on-site tennis court.")
        composeRule.onNodeWithText("Cookhouse").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tennis court").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingANewNameGoesBackAndTheCampsiteShowsIt() {
        val campsites = openApp()
        openEditForm()

        composeRule.onNodeWithTag("field_name").performTextReplacement("Hinton Ridge Sites")
        composeRule.onNodeWithTag("button_publish").performClick()

        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertDoesNotExist()
        // only the name changed: rules, price and ratings are all kept
        assertEquals(
            hinton.copy(name = "Hinton Ridge Sites"),
            runBlocking { campsites.getCampsite("hinton") },
        )
    }

    @Test
    fun emptyNameShowsTheAddCampsiteErrorAndSavesNothing() {
        val campsites = openApp()
        openEditForm()

        composeRule.onNodeWithTag("field_name").performTextClearance()
        composeRule.onNodeWithTag("button_publish").performClick()

        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Campsite").assertIsDisplayed() // still on the form
        assertEquals(hinton, runBlocking { campsites.getCampsite("hinton") })
    }

    @Test
    fun campsiteOwnedBySomeoneElseOpenedFromBrowseCampsitesHasNoEditButton() {
        openApp(campsite = hinton.copy(ownerId = "owner-daniel"), isAdmin = true)

        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").performClick()
        // the menu row, not the Campsites tab in the bottom bar
        composeRule.onNode(hasText("Campsites") and hasText("Browse and remove")).performClick()
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()

        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithTag("button_qr_code").assertIsDisplayed()
        composeRule.onNodeWithTag("button_edit").assertDoesNotExist()
    }
}
