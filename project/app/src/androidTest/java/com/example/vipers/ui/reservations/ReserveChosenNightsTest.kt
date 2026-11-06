/*
 * ReserveChosenNightsTest.kt
 * Connected UI intent tests for issue T-03 (US 01.04.01 / 01.05.01): reserving chosen nights as a
 * winning entrant. Tests the group size selector bounds, the Reserve action button state, error
 * messages, and the reservation creation flows.
 * Uses fake repositories, so it runs locally on the emulator without touching remote Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReserveChosenNightsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
        rules = "Quiet hours 10 pm to 7 am",
        cancellationPolicy = "Free up to 7 days before check-in",
    )

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val nights = (0L until 14L).map { LocalDate.now().plusDays(10 + it).toString() }
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

    private val calendar = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = nights.take(10),
        heldNights = listOf(nights[2], nights[3]),
    )

    private fun samsEntry(status: EntryStatus) = LotteryEntry(
        id = LotteryEntry.idFor("lot-summer", "test-device"),
        lotteryId = "lot-summer",
        userId = "test-device",
        groupSize = 1,
        status = status,
    )

    /** Shows the Reserve screen for Sam's win, loading from fake repositories. */
    private fun openReserve(
        theLottery: Lottery = lottery,
        theCalendar: AvailabilityCalendar = calendar,
        status: EntryStatus = EntryStatus.WON,
        reservations: FakeReservationRepository = FakeReservationRepository(),
    ) {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking { campsites.saveAvailability(theCalendar) }
        val controller = ReserveController(
            campsites,
            FakeLotteryRepository(listOf(theLottery), listOf(samsEntry(status))),
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = "hinton",
            reservationRepository = reservations,
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", "hinton", onBack = {}, controller = controller)
            }
        }
    }

    @Test
    fun groupSizeSelector_displaysInitialCountAndMaxOccupancy() {
        openReserve()
        composeRule.onNodeWithTag("reserve_guests_text").performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("1 guest (max 8)")
        composeRule.onNodeWithTag("reserve_guests_count").assertTextEquals("1")
    }

    @Test
    fun groupSizeSelector_incrementsAndDecrementsWithinOccupancyLimits() {
        openReserve()
        val increase = composeRule.onNodeWithTag("reserve_guests_increase").performScrollTo()
        val decrease = composeRule.onNodeWithTag("reserve_guests_decrease")
        val count = composeRule.onNodeWithTag("reserve_guests_count")
        val text = composeRule.onNodeWithTag("reserve_guests_text")

        // Lower bound check: cannot decrease below 1
        decrease.performClick()
        count.assertTextEquals("1")
        text.assertTextEquals("1 guest (max 8)")

        // Increment to 2
        increase.performClick()
        count.assertTextEquals("2")
        text.assertTextEquals("2 guests (max 8)")

        // Decrement back to 1
        decrease.performClick()
        count.assertTextEquals("1")
        text.assertTextEquals("1 guest (max 8)")

        // Increment up to campsite max occupancy (8)
        repeat(7) { increase.performClick() }
        count.assertTextEquals("8")
        text.assertTextEquals("8 guests (max 8)")

        // Upper bound check: cannot exceed max occupancy
        increase.performClick()
        count.assertTextEquals("8")
        text.assertTextEquals("8 guests (max 8)")
    }

    @Test
    fun reserveButton_isDisplayedAndEnabled_whenOfferIsOpen() {
        openReserve()
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun reserveButton_isDisabled_whenOfferIsExpired() {
        val ended = lottery.copy(bookingStart = now - 10 * day, bookingEnd = now - day)
        openReserve(theLottery = ended)
        composeRule.onNodeWithTag("button_reserve").performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun reserveButton_showsErrorWhenNoNightsSelected() {
        openReserve()
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()
        composeRule.onNodeWithTag("reserve_error_message").performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Please pick at least one night.")
    }
}
