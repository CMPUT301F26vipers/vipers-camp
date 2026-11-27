/*
 * ViewMyPastReservationsTest.kt
 * Connected acceptance tests for US 01.06.02: View My Past Reservations (mockup E14b).
 * Verifies that the Past tab lists completed stays, cancelled and rejected reservations
 * sorted most recent first, tapping a row opens details, and empty state shows message.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ViewMyPastReservationsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private val profiles =
        FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel")))

    // a completed stay from last month
    private val completedStay = Reservation(
        id = "res-banff",
        reservationNumber = "R-10001",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-banff",
        campsiteName = "Banff Tunnel Mountain",
        checkIn = today.minusDays(20).toString(),
        checkOut = today.minusDays(18).toString(),
        groupSize = 4,
        status = ReservationStatus.CONFIRMED,
        totalPriceCents = 16000,
    )

    // a stay cancelled by Sam
    private val cancelledStay = Reservation(
        id = "res-jasper",
        reservationNumber = "R-10002",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-jasper",
        campsiteName = "Jasper Lakeside Sites",
        checkIn = today.plusDays(10).toString(),
        checkOut = today.plusDays(14).toString(),
        groupSize = 2,
        status = ReservationStatus.CANCELLED,
        totalPriceCents = 12000,
    )

    // a stay rejected by the owner
    private val rejectedStay = Reservation(
        id = "res-canmore",
        reservationNumber = "R-10003",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-canmore",
        campsiteName = "Canmore Riverside",
        checkIn = today.plusDays(5).toString(),
        checkOut = today.plusDays(8).toString(),
        groupSize = 3,
        status = ReservationStatus.REJECTED,
        totalPriceCents = 9000,
    )

    // an active stay that should NOT appear in Past tab
    private val activeStay = Reservation(
        id = "res-hinton",
        reservationNumber = "R-10004",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = today.plusDays(1).toString(),
        checkOut = today.plusDays(3).toString(),
        groupSize = 2,
        status = ReservationStatus.CONFIRMED,
        totalPriceCents = 8000,
    )

    // another user's past reservation
    private val otherUserStay = completedStay.copy(
        id = "res-alex",
        userId = "alex",
        campsiteName = "Kananaskis Boulton Creek",
    )

    private var openedId = ""

    /** Shows My Reservations with [repository] and switches to the Past tab. */
    private fun openPastTab(repository: ReservationRepository) {
        val controller = ReservationController(
            reservationRepository = repository,
            profileRepository = profiles,
            userId = "sam",
        )
        composeRule.setContent {
            VipersTheme {
                MyReservationsScreen(
                    onOpenReservation = { openedId = it },
                    controller = controller,
                )
            }
        }
        composeRule.onNodeWithTag("my_reservations_tab_past").performClick()
        composeRule.waitForIdle()
    }

    /** Shows My Reservations with [reservations] and switches to the Past tab. */
    private fun openPastTab(vararg reservations: Reservation) =
        openPastTab(FakeReservationRepository(reservations.toList()))

    @Test
    fun pastTabListsCompletedStayCancelledAndRejectedReservations() {
        openPastTab(completedStay, cancelledStay, rejectedStay, activeStay, otherUserStay)

        composeRule.onNodeWithTag("my_reservations_tab_past").assertIsSelected()
        composeRule.onNodeWithText("Banff Tunnel Mountain").assertIsDisplayed()
        composeRule.onNodeWithText("Jasper Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Canmore Riverside").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertDoesNotExist()
        composeRule.onNodeWithText("Kananaskis Boulton Creek").assertDoesNotExist()
        composeRule.onAllNodesWithTag("my_reservation_row").assertCountEquals(3)
    }

    @Test
    fun rowShowsCampsiteDatesGuestsAndStatus() {
        openPastTab(completedStay)

        val dates = formatStayDates(completedStay.checkIn, completedStay.checkOut)
        composeRule.onNodeWithText("Banff Tunnel Mountain").assertIsDisplayed()
        composeRule.onNodeWithText("$dates · 4 guests").assertIsDisplayed()
        composeRule.onNodeWithTag("reservation_status", useUnmergedTree = true)
            .assertTextEquals("Confirmed")
    }

    @Test
    fun pastReservationsAreSortedMostRecentFirst() {
        val mostRecent = completedStay.copy(
            id = "res-recent",
            campsiteName = "Most Recent Site",
            checkIn = today.minusDays(5).toString(),
            checkOut = today.minusDays(2).toString(),
        )
        val middleStay = completedStay.copy(
            id = "res-middle",
            campsiteName = "Middle Site",
            checkIn = today.minusDays(12).toString(),
            checkOut = today.minusDays(9).toString(),
        )
        val oldestStay = completedStay.copy(
            id = "res-oldest",
            campsiteName = "Oldest Site",
            checkIn = today.minusDays(30).toString(),
            checkOut = today.minusDays(25).toString(),
        )

        openPastTab(middleStay, oldestStay, mostRecent)

        val rows = composeRule.onAllNodesWithTag("my_reservation_row")
        rows.assertCountEquals(3)
        rows[0].assert(hasText("Most Recent Site"))
        rows[1].assert(hasText("Middle Site"))
        rows[2].assert(hasText("Oldest Site"))
    }

    @Test
    fun tappingRowCallsOnOpenReservationWithId() {
        openPastTab(completedStay)

        composeRule.onNodeWithTag("my_reservation_row").performClick()

        assertEquals("res-banff", openedId)
    }

    @Test
    fun tappingRowInAppNavigationOpensReservationDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "sam",
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(listOf(completedStay)),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }

        composeRule.onNodeWithTag("tab_${Routes.RESERVATIONS}").performClick()
        composeRule.onNodeWithTag("my_reservations_tab_past").performClick()
        composeRule.onNodeWithTag("my_reservation_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number").assertTextEquals("R-10001")
        composeRule.onNodeWithTag("detail_header").assertTextEquals("Confirmed Reservation")
    }

    @Test
    fun noPastReservationsShowsEmptyMessage() {
        openPastTab(activeStay)

        composeRule.onNodeWithText("No past reservations").assertIsDisplayed()
        composeRule.onAllNodesWithTag("my_reservation_row").assertCountEquals(0)
    }

    @Test
    fun failedLoadShowsErrorMessage() {
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                throw Exception("offline")
        }
        openPastTab(failing)

        composeRule.onNodeWithText("Couldn't load reservations").assertIsDisplayed()
    }
}
