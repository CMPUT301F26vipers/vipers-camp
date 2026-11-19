/*
 * RequireRulesAcceptanceTest.kt
 * Intent tests for US 02.11.01: require rules and policy acceptance before confirming a
 * reservation on the Reserve screen (mockup E11). Verifies campsite rules and cancellation policy
 * display, "No specific rules" fallback, checkbox enabling the Reserve button, and acceptance
 * timestamp recording.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RequireRulesAcceptanceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val nights = (0L until 7L).map { LocalDate.now().plusDays(10 + it).toString() }

    private val hintonWithRules = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
    )

    private val hintonWithoutRules = hintonWithRules.copy(rules = "")

    private val lottery = Lottery(
        id = "lot-summer",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        nightsOffered = nights,
        registrationStart = now - 10 * day,
        registrationEnd = now - 2 * day,
        bookingStart = now - day,
        bookingEnd = now + 5 * day,
        drawnAt = now - 2 * day,
    )

    private data class Harness(
        val controller: ReserveController,
        val campsites: FakeCampsiteRepository,
        val reservations: FakeReservationRepository,
    )

    private fun openReserve(campsite: Campsite = hintonWithRules): Harness {
        val camps = FakeCampsiteRepository(listOf(campsite)).also {
            runBlocking {
                it.saveAvailability(
                    AvailabilityCalendar(campsiteId = campsite.id, openNights = nights),
                )
            }
        }
        val lots = FakeLotteryRepository(
            listOf(lottery),
            listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "test-device"),
                    lotteryId = "lot-summer",
                    userId = "test-device",
                    groupSize = 2,
                    status = EntryStatus.WON,
                ),
            ),
        )
        val res = FakeReservationRepository(campsiteRepository = camps, lotteryRepository = lots)
        val controller = ReserveController(
            campsiteRepository = camps,
            lotteryRepository = lots,
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = campsite.id,
            reservationRepository = res,
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", campsite.id, onBack = {}, controller = controller)
            }
        }
        return Harness(controller, camps, res)
    }

    @Test
    fun rulesAndPolicy_displayedOnReserveScreenWithCheckbox() {
        openReserve(hintonWithRules)

        composeRule.onNodeWithText("Rules: Quiet hours after 10 PM").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancellation: Free up to 7 days before").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("I accept the campsite rules").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("checkbox_accept_rules").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun noRules_displaysNoSpecificRulesFallbackAndCheckboxStillRequired() {
        openReserve(hintonWithoutRules)

        composeRule.onNodeWithText("No specific rules").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancellation: Free up to 7 days before").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("I accept the campsite rules").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("checkbox_accept_rules").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun reserveButton_staysDisabledUntilCheckboxIsTicked() {
        openReserve(hintonWithRules)

        // Pick nights so nights selection is valid
        composeRule.onNodeWithTag("night_${nights[0]}").performClick()

        // 1. Initially disabled
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsNotEnabled()

        // 2. Tick checkbox -> becomes enabled
        composeRule.onNodeWithTag("checkbox_accept_rules").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsEnabled()

        // 3. Untick checkbox -> disabled again
        composeRule.onNodeWithTag("checkbox_accept_rules").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun reserve_savesRulesAcceptedAtTimestampWithReservation() {
        val harness = openReserve(hintonWithRules)

        // Pick free night
        composeRule.onNodeWithTag("night_${nights[0]}").performClick()

        // Accept rules
        composeRule.onNodeWithTag("checkbox_accept_rules").performScrollTo()
            .performClick()

        val beforeReserve = System.currentTimeMillis()

        // Tap Reserve
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .performClick()

        // Confirmation dialog shown
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertIsDisplayed()

        // Verify rulesAcceptedAt timestamp is saved
        runBlocking {
            val userReservations = harness.reservations.getReservationsForUser("test-device")
            assertEquals(1, userReservations.size)
            val res = userReservations.first()
            assertNotNull(res.rulesAcceptedAt)
            assertTrue(res.rulesAcceptedAt >= beforeReserve - 5000L)
            assertTrue(res.rulesAcceptedAt <= System.currentTimeMillis())
        }
    }
}
