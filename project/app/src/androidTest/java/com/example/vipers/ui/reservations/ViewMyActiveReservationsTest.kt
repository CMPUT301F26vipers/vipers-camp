/*
 * ViewMyActiveReservationsTest.kt
 * Intent tests for US 01.06.01: the Active tab of My Reservations (mockup E14) lists the entrant's
 * confirmed stays that haven't ended, with the campsite name, dates, group size and status,
 * soonest first. Tapping one opens its details (mockup E15), and an empty list shows a message.
 * Uses the fake repositories, so it doesn't touch Firestore.
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
import com.example.vipers.data.fake.FakeLotteryRepository
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

class ViewMyActiveReservationsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private val profiles =
        FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel")))

    // a long weekend at Jasper in three weeks
    private val jasper = Reservation(
        id = "res-jasper",
        reservationNumber = "R-20417",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-jasper",
        campsiteName = "Jasper Lakeside Sites",
        checkIn = today.plusDays(21).toString(),
        checkOut = today.plusDays(24).toString(),
        groupSize = 4,
        totalPriceCents = 15000,
    )

    // a shorter trip to Hinton next week, so it should be listed first
    private val hinton = jasper.copy(
        id = "res-hinton",
        reservationNumber = "R-20418",
        campsiteId = "site-hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = today.plusDays(7).toString(),
        checkOut = today.plusDays(9).toString(),
        groupSize = 2,
        totalPriceCents = 9000,
    )

    // a stay that ended last week
    private val ended = jasper.copy(
        id = "res-banff",
        campsiteName = "Banff Tunnel Mountain",
        checkIn = today.minusDays(10).toString(),
        checkOut = today.minusDays(8).toString(),
    )

    // one Sam cancelled
    private val cancelled = jasper.copy(
        id = "res-canmore",
        campsiteName = "Canmore Riverside",
        status = ReservationStatus.CANCELLED,
    )

    // another entrant's stay at the same campsite
    private val someoneElses = jasper.copy(
        id = "res-alex",
        userId = "alex",
        campsiteName = "Kananaskis Boulton Creek",
    )

    private var openedId = ""

    /** Shows My Reservations for Sam with [repository] behind it. */
    private fun openScreen(repository: ReservationRepository) {
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
        composeRule.waitForIdle()
    }

    /** Shows My Reservations for Sam with [reservations] saved. */
    private fun openScreen(vararg reservations: Reservation) =
        openScreen(FakeReservationRepository(reservations.toList()))

    @Test
    fun activeTabIsSelectedAndListsActiveReservations() {
        openScreen(jasper, hinton)

        composeRule.onNodeWithTag("my_reservations_tab_active").assertIsSelected()
        composeRule.onNodeWithText("Jasper Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
    }

    @Test
    fun endedCancelledAndOtherEntrantsReservationsAreNotListed() {
        openScreen(jasper, ended, cancelled, someoneElses)

        composeRule.onNodeWithText("Jasper Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Banff Tunnel Mountain").assertDoesNotExist()
        composeRule.onNodeWithText("Canmore Riverside").assertDoesNotExist()
        composeRule.onNodeWithText("Kananaskis Boulton Creek").assertDoesNotExist()
        composeRule.onAllNodesWithTag("my_reservation_row").assertCountEquals(1)
    }

    @Test
    fun reservationCheckingOutTodayIsStillActive() {
        openScreen(
            hinton.copy(
                checkIn = today.minusDays(2).toString(),
                checkOut = today.toString(),
            ),
        )

        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
    }

    @Test
    fun rowShowsCampsiteDatesGroupSizeAndStatus() {
        openScreen(jasper)

        val dates = formatStayDates(jasper.checkIn, jasper.checkOut)
        composeRule.onNodeWithText("Jasper Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("$dates · 4 guests").assertIsDisplayed()
        // the chip is inside the clickable row, so it's only found in the unmerged tree
        composeRule.onNodeWithTag("reservation_status", useUnmergedTree = true)
            .assertTextEquals("Confirmed")
    }

    @Test
    fun rowsAreSortedByCheckInSoonestFirst() {
        openScreen(jasper, hinton)

        val rows = composeRule.onAllNodesWithTag("my_reservation_row")
        rows[0].assert(hasText("Hinton Ridge Campground"))
        rows[1].assert(hasText("Jasper Lakeside Sites"))
    }

    @Test
    fun tappingARowCallsOnOpenReservationWithItsId() {
        openScreen(jasper)

        composeRule.onNodeWithTag("my_reservation_row").performClick()

        assertEquals("res-jasper", openedId)
    }

    @Test
    fun tappingARowOpensTheReservationDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "sam",
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(listOf(jasper)),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }

        composeRule.onNodeWithTag("tab_${Routes.RESERVATIONS}").performClick()
        composeRule.onNodeWithTag("my_reservation_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number").assertTextEquals("R-20417")
        composeRule.onNodeWithTag("detail_header").assertTextEquals("Confirmed Reservation")
    }

    @Test
    fun noActiveReservationsShowsEmptyMessage() {
        openScreen(ended, cancelled)

        composeRule.onNodeWithText("No active reservations").assertIsDisplayed()
        composeRule.onAllNodesWithTag("my_reservation_row").assertCountEquals(0)
    }

    @Test
    fun failedLoadShowsAnErrorMessage() {
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                throw Exception("offline")
        }
        openScreen(failing)

        composeRule.onNodeWithText("Couldn't load reservations").assertIsDisplayed()
    }

    @Test
    fun pastTabCanBeSelected() {
        openScreen(jasper)

        composeRule.onNodeWithTag("my_reservations_tab_past").performClick()

        composeRule.onNodeWithTag("my_reservations_tab_past").assertIsSelected()
        composeRule.onNodeWithText("Jasper Lakeside Sites").assertDoesNotExist()
    }
}
