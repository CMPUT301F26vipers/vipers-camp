/*
 * AdminHomeScreenTest.kt
 * Intent tests for the Admin tab (mockup A01, US 03.11.01): the menu lists Campsites, Profiles,
 * Images, Notification Log and Message Log, and each row opens its own screen.
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdminHomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var opened = ""

    private fun openAdminHome() {
        composeRule.setContent {
            VipersTheme {
                AdminHomeScreen(
                    onOpenCampsites = { opened = "campsites" },
                    onOpenProfiles = { opened = "profiles" },
                    onOpenImages = { opened = "images" },
                    onOpenNotificationLog = { opened = "notification log" },
                    onOpenMessageLog = { opened = "message log" },
                )
            }
        }
    }

    @Test
    fun adminHome_listsEveryAdminArea() {
        openAdminHome()

        composeRule.onNodeWithText("Admin").assertIsDisplayed()
        for (row in listOf("Campsites", "Profiles", "Images", "Notification Log", "Message Log")) {
            composeRule.onNodeWithText(row).assertIsDisplayed()
        }
    }

    @Test
    fun campsitesRow_opensBrowseCampsites() {
        openAdminHome()
        composeRule.onNodeWithText("Campsites").performClick()
        assertEquals("campsites", opened)
    }

    @Test
    fun profilesRow_opensBrowseProfiles() {
        openAdminHome()
        composeRule.onNodeWithText("Profiles").performClick()
        assertEquals("profiles", opened)
    }

    @Test
    fun imagesRow_opensBrowseImages() {
        openAdminHome()
        composeRule.onNodeWithText("Images").performClick()
        assertEquals("images", opened)
    }

    @Test
    fun logRows_openTheNotificationAndMessageLogs() {
        openAdminHome()
        composeRule.onNodeWithText("Notification Log").performClick()
        assertEquals("notification log", opened)
        composeRule.onNodeWithText("Message Log").performClick()
        assertEquals("message log", opened)
    }
}
