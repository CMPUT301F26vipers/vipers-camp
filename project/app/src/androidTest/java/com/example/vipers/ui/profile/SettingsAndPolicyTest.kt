/*
 * SettingsAndPolicyTest.kt
 * Intent tests for the Settings screen (mockup E20) and the Policy screen (mockup E21): Settings
 * opens Policy and loads the demo data, and both screens go back.
 * Known issues: the notifications switch (US 01.10.01) and the policy text (US 01.11.01) aren't
 * built yet, so there's nothing to test for them.
 */
package com.example.vipers.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsAndPolicyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var wentBack = false
    private var policyOpened = false
    private var demoLoaded = false

    private fun openSettings() {
        composeRule.setContent {
            VipersTheme {
                SettingsScreen(
                    onBack = { wentBack = true },
                    onOpenPolicy = { policyOpened = true },
                    onLoadDemoData = { demoLoaded = true },
                )
            }
        }
    }

    @Test
    fun settings_showsPolicyAndDemoData() {
        openSettings()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Policy and Guidelines").assertIsDisplayed()
        composeRule.onNodeWithText("Load demo data").assertIsDisplayed()
    }

    @Test
    fun policyRow_opensPolicy() {
        openSettings()
        composeRule.onNodeWithText("Policy and Guidelines").performClick()
        assertTrue(policyOpened)
    }

    @Test
    fun loadDemoDataButton_loadsTheDemoData() {
        openSettings()
        composeRule.onNodeWithText("Load demo data").performClick()
        assertTrue(demoLoaded)
    }

    @Test
    fun settingsBackArrow_goesBack() {
        openSettings()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(wentBack)
    }

    @Test
    fun policy_showsItsTitleAndGoesBack() {
        composeRule.setContent { VipersTheme { PolicyScreen(onBack = { wentBack = true }) } }

        composeRule.onNodeWithText("Policy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(wentBack)
    }
}
