/*
 * NavigationTest.kt
 * Intent tests for T-02: the app opens on Home, every tab opens its screen, Settings and Policy
 * open from Profile, Back returns to the previous screen, and the Admin tab is for admins only.
 * Known issues: tests the navigation on its own. The app's repositories are swapped for empty
 * fakes so Home's campsite search doesn't touch Firestore.
 */
package com.example.vipers.ui.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Same as the phone's Back button, but sent through the app so it doesn't depend on the
    // emulator window having focus (Espresso's pressBack() does).
    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Before
    fun useFakeRepositories() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
    }

    private fun startApp(isAdmin: Boolean = false, userId: String = "user123") {
        composeRule.setContent {
            VipersTheme {
                AppNavigation(
                    isAdmin = isAdmin,
                    onLoadDemoData = {},
                )
            }
        }
    }

    private fun tapTab(route: String) = composeRule.onNodeWithTag("tab_$route").performClick()

    private fun assertHomeShown() = composeRule.onNodeWithTag("search_field").assertIsDisplayed()

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()

    @Test
    fun opensOnHome() {
        startApp()
        assertHomeShown()
    }

    @Test
    fun eachTabOpensItsScreen() {
        startApp()
        tapTab(Routes.RESERVATIONS)
        assertShown("My Reservations")
        tapTab(Routes.CAMPSITES)
        assertShown("My Campsites")
        tapTab(Routes.NOTIFICATIONS)
        assertShown("Your notifications")
        tapTab(Routes.PROFILE)
        assertShown("Editing your name")
        tapTab(Routes.HOME)
        assertHomeShown()
    }

    @Test
    fun anyoneCanOpenAddCampsiteAndGoBack() {
        startApp()
        tapTab(Routes.CAMPSITES)
        composeRule.onNodeWithContentDescription("Add campsite").performClick()
        assertShown("Add Campsite")
        // no bottom bar on the form, like the mockup
        composeRule.onNodeWithTag("tab_${Routes.HOME}").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")
    }

    @Test
    fun settingsAndPolicyOpenFromProfile() {
        startApp()
        tapTab(Routes.PROFILE)
        composeRule.onNodeWithText("Settings").performClick()
        assertShown("Load demo data")

        pressBack()
        assertShown("Editing your name")

        composeRule.onNodeWithText("Policy").performClick()
        assertShown("How the lottery works")
    }

    @Test
    fun adminTabIsHiddenForNormalUsers() {
        startApp(isAdmin = false)
        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").assertDoesNotExist()
    }

    @Test
    fun adminTabShowsForAdmins() {
        startApp(isAdmin = true)
        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").assertIsDisplayed()
        tapTab(Routes.ADMIN)

        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).assertIsDisplayed()
        assertShown("Profiles")
        assertShown("Images")
        assertShown("Notification Log")
        assertShown("Message Log")
        composeRule.onAllNodesWithText("Browse and remove").assertCountEquals(3)
        assertShown("Every notification sent")
        assertShown("Owner–entrant conversations")
    }

    @Test
    fun adminUsersRetainAccessToAllNormalTabs() {
        startApp(isAdmin = true)
        tapTab(Routes.RESERVATIONS)
        assertShown("My Reservations")
        tapTab(Routes.CAMPSITES)
        assertShown("My Campsites")
        tapTab(Routes.NOTIFICATIONS)
        assertShown("Your notifications")
        tapTab(Routes.PROFILE)
        assertShown("Editing your name")
        tapTab(Routes.HOME)
        assertHomeShown()
    }

    @Test
    fun adminMenuItemsOpenCorrectDestinationsAndGoBack() {
        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        // Campsites
        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).performClick()
        assertShown("All Campsites")
        assertShown("Browse Campsites (03.04.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Profiles
        composeRule.onNodeWithText("Profiles").performClick()
        assertShown("All Profiles")
        assertShown("Browse Profiles (03.05.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Images
        composeRule.onNodeWithText("Images").performClick()
        assertShown("All Images")
        assertShown("Browse Images (03.06.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Notification Log
        composeRule.onNodeWithText("Notification Log").performClick()
        assertShown("Logs")
        assertShown("Notification Log (03.08.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Message Log
        composeRule.onNodeWithText("Message Log").performClick()
        assertShown("Logs")
        assertShown("Message Log (03.12.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }

    @Test
    fun ownerCampsiteButtonsOpenCorrectScreensAndGoBack() {
        startApp()
        tapTab(Routes.CAMPSITES)

        // Matches the specific node that has the text "Reservations" AND the Button role
        composeRule.onNode(hasText("Reservations") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertShown("List of reservations for your campsites is added in US 02.07.01")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")

        // Test Sent navigation
        composeRule.onNodeWithText("Sent").performClick()
        assertShown("Sent notifications is added in US 02.10.02.")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")
    }
}
