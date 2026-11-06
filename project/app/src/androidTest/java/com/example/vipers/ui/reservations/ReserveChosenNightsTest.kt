/*
 * ReserveChosenNightsTest.kt
 * Connected UI intent tests for issue T-03 (US 01.04.01 / 01.05.01): reserving chosen nights as a
 * winning entrant. Tests all 6 acceptance criteria: reservation creation, booking period and
 * unused win checks, max occupancy limits, concurrency conflict calendar refresh, reserved status
 * and night exclusion, and appearance in My Reservations and Owner Active Reservations.
 * Uses fake repositories, so it runs locally on the emulator without touching remote Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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

    private fun samsEntry(status: EntryStatus, userId: String = "test-device") = LotteryEntry(
        id = LotteryEntry.idFor("lot-summer", userId),
        lotteryId = "lot-summer",
        userId = userId,
        groupSize = 1,
        status = status,
    )

    data class TestHarness(
        val controller: ReserveController,
        val campsites: FakeCampsiteRepository,
        val lotteries: FakeLotteryRepository,
        val reservations: FakeReservationRepository,
    )

    /** Shows the Reserve screen for Sam's win, loading from fake repositories. */
    private fun openReserve(
        theLottery: Lottery = lottery,
        theCalendar: AvailabilityCalendar = calendar,
        status: EntryStatus = EntryStatus.WON,
        campsiteRepo: FakeCampsiteRepository? = null,
        lotteryRepo: FakeLotteryRepository? = null,
        reservationRepo: FakeReservationRepository? = null,
        userId: String = "test-device",
    ): TestHarness {
        val camps = campsiteRepo ?: FakeCampsiteRepository(listOf(hinton)).also {
            runBlocking { it.saveAvailability(theCalendar) }
        }
        val lots = lotteryRepo ?: FakeLotteryRepository(
            listOf(theLottery),
            listOf(samsEntry(status, userId)),
        )
        val res = reservationRepo ?: FakeReservationRepository(
            campsiteRepository = camps,
            lotteryRepository = lots,
        )
        val controller = ReserveController(
            camps,
            lots,
            userId = userId,
            lotteryId = "lot-summer",
            campsiteId = "hinton",
            reservationRepository = res,
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", "hinton", onBack = {}, controller = controller)
            }
        }
        return TestHarness(controller, camps, lots, res)
    }

    private fun night(index: Int) = composeRule.onNodeWithTag("night_${nights[index]}")

    private fun hasState(state: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

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
    fun reserve_disabledForAlreadyReservedOrUsedWin() {
        openReserve(status = EntryStatus.RESERVED)
        composeRule.onNodeWithTag("reserve_banner")
            .assertTextEquals("You have already used this win.")
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

    @Test
    fun successfulReservation_showsConfirmationDialogWithAllDetails() {
        openReserve()

        // Pick 2 free nights: night 0 and night 1
        night(0).performClick()
        night(1).performClick()

        // Set group size to 2
        composeRule.onNodeWithTag("reserve_guests_increase").performScrollTo().performClick()

        // Tap Reserve
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()

        // Confirmation dialog is shown (US 01.05.01 / AC 6)
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertIsDisplayed()
        composeRule.onNodeWithText("Reservation Confirmed!").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_campsite_name")
            .assertTextEquals("Hinton Ridge Campground")
        composeRule.onNodeWithTag("confirm_guests").assertTextEquals("2 guests")
        composeRule.onNodeWithTag("confirm_total_price").assertTextEquals("$100")
        composeRule.onNodeWithTag("confirm_reservation_number")
            .assertTextContains("R-", substring = true)

        // Dismiss confirmation
        composeRule.onNodeWithTag("button_confirm_done").performClick()
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertDoesNotExist()
    }

    @Test
    fun conflict_failsWithClearMessageAndRefreshesCalendar() {
        val harness = openReserve()

        // Pick free night 0
        night(0).performClick()

        // Another winner books night 0 concurrently before Sam taps Reserve
        runBlocking {
            val cal = harness.campsites.getAvailability("hinton")
            harness.campsites.saveAvailability(cal.copy(heldNights = cal.heldNights + nights[0]))
        }

        // Tap Reserve
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()

        // Fails with clear message (AC 4)
        composeRule.onNodeWithTag("reserve_error_message").performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(
                "These nights were just booked by another winner. Please pick different nights.",
            )

        // Calendar refreshes: night 0 is now Taken and unselected
        night(0).performScrollTo().assert(hasState("Taken"))
    }

    @Test
    fun reservedNights_updatesCalendarAndEntrantStatusToReserved() {
        val camps = FakeCampsiteRepository(listOf(hinton)).also {
            runBlocking { it.saveAvailability(calendar) }
        }
        val lots = FakeLotteryRepository(listOf(lottery), listOf(samsEntry(EntryStatus.WON, "sam")))
        val res = FakeReservationRepository(campsiteRepository = camps, lotteryRepository = lots)

        // Sam reserves night 0 and night 1
        openReserve(
            campsiteRepo = camps,
            lotteryRepo = lots,
            reservationRepo = res,
            userId = "sam",
        )
        night(0).performClick()
        night(1).performClick()
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertIsDisplayed()

        // 1. Entrant's lottery entry status becomes RESERVED (AC 5)
        runBlocking {
            val samEntry = lots.getEntry("lot-summer", "sam")
            assertNotNull(samEntry)
            assertEquals(EntryStatus.RESERVED, samEntry?.status)
        }

        // 2. Held nights updated in calendar (AC 5)
        runBlocking {
            val updatedCal = camps.getAvailability("hinton")
            assertTrue(nights[0] in updatedCal.heldNights)
            assertTrue(nights[1] in updatedCal.heldNights)
        }
    }

    @Test
    fun reservedNights_noLongerAvailableToOtherWinners() {
        // nights 0 and 1 are held by a prior reservation
        val calendarWithHeld = calendar.copy(
            heldNights = calendar.heldNights + listOf(nights[0], nights[1]),
        )
        openReserve(
            theCalendar = calendarWithHeld,
            status = EntryStatus.WON,
            userId = "alex",
        )

        // Nights 0 and 1 are Taken and cannot be picked (AC 5)
        night(0).performScrollTo().assert(hasState("Taken"))
        night(1).performScrollTo().assert(hasState("Taken"))
        night(0).performClick()
        night(0).assert(hasState("Taken"))
    }

    @Test
    fun reservation_appearsInOwnerActiveAndEntrantReservations() {
        val camps = FakeCampsiteRepository(listOf(hinton)).also {
            runBlocking { it.saveAvailability(calendar) }
        }
        val lots = FakeLotteryRepository(listOf(lottery), listOf(samsEntry(EntryStatus.WON, "sam")))
        val res = FakeReservationRepository(campsiteRepository = camps, lotteryRepository = lots)

        openReserve(
            campsiteRepo = camps,
            lotteryRepo = lots,
            reservationRepo = res,
            userId = "sam",
        )

        night(0).performClick()
        night(1).performClick()
        composeRule.onNodeWithTag("reserve_guests_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("button_reserve").performScrollTo().performClick()

        // Confirmation shown (AC 6)
        composeRule.onNodeWithTag("dialog_reservation_confirmed").assertIsDisplayed()

        runBlocking {
            // Appears in owner's active reservations (US 02.07.01 / AC 6)
            val ownerReservations = res.getReservationsForOwner("owner-priya")
            assertEquals(1, ownerReservations.size)
            val ownerRes = ownerReservations.first()
            assertEquals("sam", ownerRes.userId)
            assertEquals("Hinton Ridge Campground", ownerRes.campsiteName)
            assertEquals(2, ownerRes.groupSize)
            assertEquals(nights[0], ownerRes.checkIn)
            val expectedCheckOut = LocalDate.parse(nights[1]).plusDays(1).toString()
            assertEquals(expectedCheckOut, ownerRes.checkOut)

            // Appears in entrant's reservations (US 01.06.01 / AC 6)
            val entrantReservations = res.getReservationsForUser("sam")
            assertEquals(1, entrantReservations.size)
            assertEquals(ownerRes.id, entrantReservations.first().id)
        }
    }
}
