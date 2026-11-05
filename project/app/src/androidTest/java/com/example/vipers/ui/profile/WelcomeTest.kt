/*
 * WelcomeTest.kt
 * Intent tests for T-01 and US 01.09.01: first launch shows Welcome with no login, Continue
 * needs a name, and continuing saves the profile under this device and opens Home. A device
 * that already has a profile goes straight to Home. If the profile check fails it asks to
 * retry instead of showing Welcome.
 * Known issues: uses fake repositories, so it doesn't touch Firestore.
 */
package com.example.vipers.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val profiles = FakeProfileRepository()

    /** Opens the app as the device "my-phone", with fake repositories in place of Firestore. */
    private fun startApp(profileRepository: ProfileRepository = profiles) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "my-phone",
            profileRepository = profileRepository,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppRoot(app.container.deviceIdentityManager, onLoadDemoData = {}) }
        }
    }

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun firstLaunch_showsWelcomeWithNoLogin() {
        startApp()
        waitFor("Tell us your name to get started.")
        composeRule.onNodeWithTag("welcome_name").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_email").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_phone").assertIsDisplayed()
        composeRule.onNodeWithText("Password", substring = true).assertDoesNotExist()
    }

    @Test
    fun continue_isDisabledUntilANameIsEntered() {
        startApp()
        waitFor("Welcome")
        composeRule.onNodeWithTag("button_continue").assertIsNotEnabled()
        composeRule.onNodeWithTag("welcome_email").performTextInput("maya@example.com")
        composeRule.onNodeWithTag("button_continue").assertIsNotEnabled()

        composeRule.onNodeWithTag("welcome_name").performTextInput("Maya Chen")
        composeRule.onNodeWithTag("button_continue").assertIsEnabled()
    }

    @Test
    fun continue_savesTheProfileUnderThisDeviceAndOpensHome() {
        startApp()
        waitFor("Welcome")
        composeRule.onNodeWithTag("welcome_name").performTextInput("Maya Chen")
        composeRule.onNodeWithTag("welcome_phone").performTextInput("780-555-0100")
        composeRule.onNodeWithTag("button_continue").performClick()

        // Home's search bar shows once the profile is made
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("search_field")).fetchSemanticsNodes().isNotEmpty()
        }
        val saved = runBlocking { profiles.getProfile("my-phone") }!!
        assertEquals("Maya Chen", saved.name)
        assertEquals("780-555-0100", saved.phone)
    }

    @Test
    fun laterLaunch_withAProfile_opensHomeAndSkipsWelcome() {
        runBlocking { profiles.saveProfile(UserProfile(id = "my-phone", name = "Maya Chen")) }
        startApp()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("search_field")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tell us your name to get started.").assertDoesNotExist()
    }

    @Test
    fun failedProfileCheck_asksToRetryInsteadOfShowingWelcome() {
        // like opening the app offline with nothing saved on the phone
        var offline = true
        val flaky = object : ProfileRepository by profiles {
            override suspend fun getProfile(id: String): UserProfile? =
                if (offline) throw Exception("offline") else profiles.getProfile(id)
        }
        runBlocking { profiles.saveProfile(UserProfile(id = "my-phone", name = "Maya Chen")) }
        startApp(flaky)

        waitFor("Retry")
        composeRule.onNodeWithText("Tell us your name to get started.").assertDoesNotExist()

        // back online: Retry finds the profile and opens Home
        offline = false
        composeRule.onNodeWithTag("button_retry").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("search_field")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
