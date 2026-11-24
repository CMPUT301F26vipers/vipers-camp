/*
 * OwnerPastReservationsTest.kt
 * Intent tests for US 02.07.02: the owner's Past tab lists completed stays, most recent check-out
 * first, with the entrant's name and dates, leaves out active, cancelled and rejected reservations,
 * and tapping a row opens the reservation's details (mockup O15). Uses the fake repositories, so it
 * doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OwnerPastReservationsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com", phone = "780-555-0101"),
            UserProfile(id = "alex", name = "Alex Chen"),
            UserProfile(id = "jordan", name = "Jordan Lee"),
            UserProfile(id = "maya", name = "Maya Singh"),
        ),
    )

    // the screen uses today's date, so this stay ended long ago
    private val completed = Reservation(
        id = "completed",
        userId = "sam",
        ownerId = "test-device",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2000-05-01",
        checkOut = "2000-05-03",
        groupSize = 3,
        rulesAcceptedAt = 1_700_000_000_000,
    )

    // and this one is far in the future
    private val active = completed.copy(
        id = "active",
        userId = "alex",
        checkIn = "2099-05-08",
        checkOut = "2099-05-10",
    )

    private var openedId = ""

    /** Shows the Reservations screen with [reservations] saved, and opens the Past tab. */
    private fun openPastTab(vararg reservations: Reservation) {
        val controller = ReservationController(
            reservationRepository = FakeReservationRepository(reservations.toList()),
            profileRepository = profiles,
            userId = "test-device",
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
        composeRule.onNodeWithTag("reservations_tab_past").performClick()
    }

    @Test
    fun pastTabShowsCompletedStayWithEntrantNameAndDates() {
        openPastTab(completed)

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground · May 1 – May 3, 2000 · 3 guests")
            .assertIsDisplayed()
    }

    @Test
    fun pastTabLeavesOutActiveAndCancelledReservations() {
        openPastTab(
            active,
            completed,
            completed.copy(id = "cancelled", userId = "jordan", status = ReservationStatus.CANCELLED),
            completed.copy(id = "rejected", userId = "maya", status = ReservationStatus.REJECTED),
        )

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("Alex Chen").assertDoesNotExist()
        composeRule.onNodeWithText("Jordan Lee").assertDoesNotExist()
        composeRule.onNodeWithText("Maya Singh").assertDoesNotExist()
        composeRule.onAllNodesWithTag("reservation_row").assertCountEquals(1)
    }

    @Test
    fun pastTabIsSortedMostRecentFirst() {
        openPastTab(
            completed.copy(id = "2001", checkIn = "2001-06-01", checkOut = "2001-06-03"),
            completed.copy(id = "2003", checkIn = "2003-06-01", checkOut = "2003-06-03"),
            completed.copy(id = "2002", checkIn = "2002-06-01", checkOut = "2002-06-03"),
        )

        val rows = composeRule.onAllNodesWithTag("reservation_row")
        rows.assertCountEquals(3)
        listOf("2003", "2002", "2001").forEachIndexed { index, year ->
            rows[index].assert(hasText("Hinton Ridge Campground · Jun 1 – Jun 3, $year · 3 guests"))
        }
    }

    @Test
    fun pastTabWithNoCompletedStaysShowsEmptyMessage() {
        openPastTab(active)

        composeRule.onNodeWithText("No past reservations").assertIsDisplayed()
        composeRule.onAllNodesWithTag("reservation_row").assertCountEquals(0)
    }

    @Test
    fun tappingAPastRowCallsOnOpenReservationWithItsId() {
        openPastTab(completed)

        composeRule.onNodeWithTag("reservation_row").performClick()

        assertEquals("completed", openedId)
    }

    @Test
    fun tappingAPastRowOpensTheReservationDetails() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(listOf(completed)),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent { VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) } }

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNode(
            hasText("Reservations") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).performClick()
        composeRule.onNodeWithTag("reservations_tab_past").performClick()
        composeRule.onNodeWithTag("reservation_row").performClick()

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com · 780-555-0101").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText("May 1 – May 3, 2000").assertIsDisplayed()
    }
}
