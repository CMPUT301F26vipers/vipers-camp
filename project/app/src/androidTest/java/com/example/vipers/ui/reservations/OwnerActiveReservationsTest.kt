/*
 * OwnerActiveReservationsTest.kt
 * Intent tests for US 02.07.01: the owner's Active tab lists active reservations with the entrant's
 * name and dates, leaves out past ones, and tapping a row opens the reservation's details (mockup
 * O15). Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OwnerActiveReservationsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com", phone = "780-555-0101"),
            UserProfile(id = "alex", name = "Alex Chen"),
        ),
    )

    // the screen uses today's date, so this stay is far in the future
    private val active = Reservation(
        id = "active",
        userId = "sam",
        ownerId = "test-device",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2099-05-08",
        checkOut = "2099-05-10",
        groupSize = 3,
        rulesAcceptedAt = 1_700_000_000_000,
    )

    // and this one ended long ago
    private val past = active.copy(
        id = "past",
        userId = "alex",
        checkIn = "2000-05-01",
        checkOut = "2000-05-03",
    )

    private var openedId = ""

    /** Shows the Reservations screen with [reservations] saved. */
    private fun openScreen(vararg reservations: Reservation) {
        val controller = ReservationController(
            reservationRepository = FakeReservationRepository(reservations.toList()),
            profileRepository = profiles,
            ownerId = "test-device",
        )
        composeRule.setContent {
            VipersTheme {
                OwnerReservationsScreen(
                    onBack = {},
                    onOpenReservation = { openedId = it },
                    controller = controller,
                )
            }
        }
    }

    @Test
    fun activeReservationAppearsWithEntrantNameAndDates() {
        openScreen(active)

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground · May 8 – May 10, 2099 · 3 guests")
            .assertIsDisplayed()
    }

    @Test
    fun pastReservationDoesNotAppear() {
        openScreen(active, past)

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("Alex Chen").assertDoesNotExist()
    }

    @Test
    fun tappingARowCallsOnOpenReservationWithItsId() {
        openScreen(active)

        composeRule.onNodeWithTag("reservation_row").performClick()

        assertEquals("active", openedId)
    }

    @Test
    fun tappingARowOpensTheReservationDetails() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(listOf(active)),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) } }

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNode(
            hasText("Reservations") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        composeRule.onNodeWithTag("reservation_row").performClick()

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com · 780-555-0101").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText("May 8 – May 10, 2099").assertIsDisplayed()
        composeRule.onNodeWithText("Rules accepted").assertIsDisplayed()
        composeRule.onNodeWithText("Yes").assertIsDisplayed()
    }
}
