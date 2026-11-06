/*
 * ReservationDetailsScreenTest.kt
 * UI tests for ReservationDetailsScreen (US 01.05.01).
 * Verifies that the reservation details screen displays the reservation number, campsite name,
 * dates, group size, total price, and status, and handles missing reservations.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Reservation
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReservationDetailsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val campsite = Campsite(
        id = "site-hinton",
        name = "Hinton Ridge Campground",
        rules = "Quiet hours after 10 PM",
        pricePerNightCents = 6550,
    )

    private val reservation = Reservation(
        id = "res-1",
        reservationNumber = "R-10042",
        userId = "sam",
        ownerId = "owner-1",
        campsiteId = "site-hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-06-01",
        checkOut = "2027-06-03",
        groupSize = 3,
        totalPriceCents = 13100,
        rulesAcceptedAt = 1_700_000_000_000,
    )

    private fun controllerFor(
        reservationId: String,
        userId: String = "sam",
        vararg reservations: Reservation,
    ): ReservationDetailsController {
        val reservationRepo = FakeReservationRepository(reservations.toList())
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        return ReservationDetailsController(
            reservationRepository = reservationRepo,
            campsiteRepository = campsiteRepo,
            userId = userId,
            reservationId = reservationId,
        )
    }

    @Test
    fun displaysAllReservationDetails() {
        val controller = controllerFor("res-1", "sam", reservation)
        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-1",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithTag("screen_reservation_details").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_reservation_number")
            .assertTextEquals("R-10042")
        composeRule.onNodeWithTag("detail_campsite_name")
            .assertTextEquals("Hinton Ridge Campground")
        composeRule.onNodeWithTag("detail_dates").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_guests")
            .assertTextEquals("3 guests")
        composeRule.onNodeWithTag("detail_total_price")
            .assertTextEquals("$131")
        composeRule.onNodeWithTag("detail_status")
            .assertTextEquals("Confirmed")
        composeRule.onNodeWithTag("detail_campsite_rules")
            .assertTextEquals("Quiet hours after 10 PM")
    }

    @Test
    fun displaysNotFoundMessageWhenReservationMissing() {
        val controller = controllerFor("nope", "sam", reservation)
        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "nope",
                    onBack = {},
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithTag("text_reservation_not_found").assertIsDisplayed()
        composeRule.onNodeWithTag("text_reservation_not_found")
            .assertTextContains("Reservation not found", substring = true)
    }

    @Test
    fun backButtonCallsOnBack() {
        var backCalled = false
        val controller = controllerFor("res-1", "sam", reservation)
        composeRule.setContent {
            VipersTheme {
                ReservationDetailsScreen(
                    reservationId = "res-1",
                    onBack = { backCalled = true },
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backCalled)
    }
}
