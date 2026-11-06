/*
 * OwnerReservationDetailsTest.kt
 * Intent tests for the owner's Reservation details screen (mockup O15, US 02.07.01): the entrant's
 * name and contact details, the stay, whether the rules were accepted, a deleted entrant, and a
 * reservation at another owner's campsite. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Rule
import org.junit.Test

class OwnerReservationDetailsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = FakeProfileRepository(
        listOf(UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com", phone = "780-555-0101")),
    )

    // Sam's May long weekend at Priya's Hinton campsite
    private val samsStay = Reservation(
        id = "res-1",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
        groupSize = 3,
        rulesAcceptedAt = 1_800_000_000_000,
    )

    /** Shows the details of "res-1" to [ownerId], with [reservation] saved. */
    private fun openDetails(reservation: Reservation, ownerId: String = "owner-priya") {
        val controller = OwnerReservationDetailsController(
            reservationRepository = FakeReservationRepository(listOf(reservation)),
            profileRepository = profiles,
            ownerId = ownerId,
            reservationId = "res-1",
        )
        composeRule.setContent {
            VipersTheme { OwnerReservationDetailsScreen("res-1", onBack = {}, controller = controller) }
        }
    }

    @Test
    fun details_showTheEntrantsNameAndContactDetails() {
        openDetails(samsStay)

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com · 780-555-0101").assertIsDisplayed()
    }

    @Test
    fun details_showTheCampsiteDatesAndGuests() {
        openDetails(samsStay)

        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText(formatStayDates("2027-05-08", "2027-05-10")).assertIsDisplayed()
        composeRule.onNodeWithText("Guests").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun details_showWhetherTheRulesWereAccepted() {
        openDetails(samsStay.copy(rulesAcceptedAt = 0))

        composeRule.onNodeWithText("Rules accepted").assertIsDisplayed()
        composeRule.onNodeWithText("No").assertIsDisplayed()
    }

    @Test
    fun details_showDeletedUserWhenTheEntrantsProfileIsGone() {
        openDetails(samsStay.copy(userId = "gone"))

        composeRule.onNodeWithText("Deleted user").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
    }

    @Test
    fun details_ofAnotherOwnersReservation_sayNotFound() {
        openDetails(samsStay, ownerId = "someone-else")

        composeRule.onNodeWithText("Reservation not found").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com · 780-555-0101").assertDoesNotExist()
    }
}
