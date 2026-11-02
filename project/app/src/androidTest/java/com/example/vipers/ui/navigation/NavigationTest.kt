/*
 * NavigationTest.kt
 * Intent tests for T-02: the app opens on Home, every tab opens its screen, Settings and Policy
 * open from Profile, Back returns to the previous screen, and the Admin tab is for admins only.
 * Known issues: tests the navigation on its own, so it doesn't touch Firestore.
 */
package com.example.vipers.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun startApp(isAdmin: Boolean = false, userId: String = "user123") {
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = isAdmin, onLoadDemoData = {}, userId = userId) }
        }
    }

    private fun tapTab(route: String) = composeRule.onNodeWithTag("tab_$route").performClick()

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()

    @Test
    fun opensOnHome() {
        startApp()
        assertShown("Campsite search is added")
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
        assertShown("Campsite search is added")
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
        tapTab(Routes.ADMIN)
        assertShown("Browsing and removing")
    }
}
