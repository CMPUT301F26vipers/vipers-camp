/*
 * ViewAppPolicyTest.kt
 * Intent tests for US 01.11.01: "Policy and Guidelines" in Settings (mockup E20) opens the Policy
 * screen (mockup E21), its text scrolls, back returns to Settings, and every user can open it,
 * with or without reservations. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class ViewAppPolicyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()

    // a stay at Jasper next month, for the user who has a reservation
    private val jasperStay = Reservation(
        id = "res-jasper",
        reservationNumber = "R-20417",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-jasper",
        campsiteName = "Jasper Lakeside Sites",
        checkIn = today.plusDays(30).toString(),
        checkOut = today.plusDays(32).toString(),
        groupSize = 4,
        totalPriceCents = 10000,
    )

    /** Starts the app as Sam with [reservations] saved, as a normal user or an [isAdmin]. */
    private fun startApp(reservations: List<Reservation> = emptyList(), isAdmin: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "sam",
            profileRepository =
                FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel"))),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(reservations),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = isAdmin, onLoadDemoData = {}) }
        }
    }

    /** Goes Profile > Settings > Policy and Guidelines. */
    private fun openPolicyFromSettings() {
        composeRule.onNodeWithTag("tab_${Routes.PROFILE}").performClick()
        // Settings is at the bottom of Profile, below the screen on small phones
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Policy and Guidelines").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun policyAndGuidelinesInSettingsOpensThePolicyScreen() {
        startApp()
        openPolicyFromSettings()

        composeRule.onNodeWithText("Policy").assertIsDisplayed()
        composeRule.onNodeWithText("How the lottery works").assertIsDisplayed()
    }

    @Test
    fun everySectionCanBeScrolledToAndRead() {
        startApp()
        openPolicyFromSettings()

        POLICY_SECTIONS.forEach { section ->
            composeRule.onNodeWithText(section.title).performScrollTo().assertIsDisplayed()
            section.paragraphs.forEach { paragraph ->
                composeRule.onNodeWithText(paragraph).performScrollTo().assertIsDisplayed()
            }
        }
    }

    @Test
    fun backArrowReturnsToSettings() {
        startApp()
        openPolicyFromSettings()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Policy and Guidelines").assertIsDisplayed()
        composeRule.onNodeWithText("Load demo data").assertIsDisplayed()
    }

    @Test
    fun userWithNoReservationsCanOpenThePolicy() {
        startApp(reservations = emptyList())
        openPolicyFromSettings()

        composeRule.onNodeWithText("Fair use").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun userWithAReservationCanOpenThePolicy() {
        startApp(reservations = listOf(jasperStay))
        openPolicyFromSettings()

        composeRule.onNodeWithText("Fair use").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun adminCanOpenThePolicy() {
        startApp(isAdmin = true)
        openPolicyFromSettings()

        composeRule.onNodeWithText("How the lottery works").assertIsDisplayed()
    }

    @Test
    fun policyAlsoOpensFromProfile() {
        startApp()
        composeRule.onNodeWithTag("tab_${Routes.PROFILE}").performClick()
        composeRule.onNodeWithText("Policy").performScrollTo().performClick()

        composeRule.onNodeWithText("How the lottery works").assertIsDisplayed()
    }
}
