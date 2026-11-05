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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

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
            VipersTheme { AppNavigation(isAdmin = isAdmin, onLoadDemoData = {}) }
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
        tapTab(Routes.ADMIN)
        assertShown("Browsing and removing")
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

    @Test
    fun ownerTapsCampsiteOpensDetailsAndNavigatesToManagementOptions() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        val campsiteId = "campsite-1"
        val campsite = Campsite(
            id = campsiteId,
            ownerId = "test-device",
            name = "Pine Valley Resort",
            address = "Banff, AB",
            region = Region.BANFF,
            description = "Peaceful retreat in the pines.",
            maxOccupancy = 4,
            tags = listOf("Fire pit"),
            pricePerNightCents = 6000,
            rules = "No loud music",
            cancellationPolicy = "Free up to 3 days before"
        )
        val lottery = Lottery(
            id = "lot-1",
            ownerId = "test-device",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("July long weekend"),
            registrationStart = 0,
            registrationEnd = Long.MAX_VALUE,
            bookingEnd = Long.MAX_VALUE
        )
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(),
            campsiteRepository = FakeCampsiteRepository(listOf(campsite)),
            lotteryRepository = FakeLotteryRepository(listOf(lottery)),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )

        startApp()
        tapTab(Routes.CAMPSITES)

        // 1. Tapping campsite opens owner's details screen with all its fields
        composeRule.onNodeWithText("Pine Valley Resort").performClick()
        assertShown("My Campsite")
        assertShown("Pine Valley Resort")
        assertShown("Banff, AB")
        assertShown("BANFF · Up to 4 guests · $60 / night")
        assertShown("Peaceful retreat in the pines.")
        assertShown("Fire pit")
        assertShown("No loud music")
        assertShown("Free up to 3 days before")

        // 2. Links to Edit
        composeRule.onNodeWithText("Edit").performClick()
        assertShown("Edit Campsite")
        assertShown("Editing campsite details is added in US 02.01.03.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 3. Links to QR Code (once 02.02.01 is built)
        composeRule.onNodeWithText("QR Code").performClick()
        assertShown("QR Code")
        assertShown("Campsite QR code is added in US 02.02.01.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 4. Links to Availability
        composeRule.onNodeWithText("Availability").performClick()
        assertShown("Availability")
        assertShown("Managing campsite availability is added in US 02.06.01.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 5. Links to Reservations
        composeRule.onNode(hasText("Reservations") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertShown("List of reservations for your campsites is added in US 02.07.01")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 6. Links to Lottery details
        composeRule.onNodeWithText("July long weekend").performClick()
        assertShown("Lottery Details")
        assertShown("Lottery details is added in US 02.04.01.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 7. Links to Create Lottery
        composeRule.onNodeWithText("Create Lottery").performClick()
        assertShown("Create Lottery")
        assertShown("Creating a lottery is added in US 02.04.01.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsite")

        // 8. Back from Details returns to My Campsites
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")
    }
}
