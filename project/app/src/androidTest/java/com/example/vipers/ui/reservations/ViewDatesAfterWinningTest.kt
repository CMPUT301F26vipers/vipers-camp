/*
 * ViewDatesAfterWinningTest.kt
 * Intent tests for US 01.04.01: a winner opens the Reserve screen (mockup E11) from Campsite
 * Details and sees the reserve-by date, the lottery's nights with taken ones greyed out, and the
 * price and total for the nights they pick; plus the "no nights left" and "offer expired" cases.
 * Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: opening the screen from the win notification isn't wired up yet.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.common.shortDate
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ViewDatesAfterWinningTest {

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

    // the screens check the booking period against the real clock, so times are set around now
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    // 14 nights starting in 10 days, like mockup E11; booking opened yesterday and ends in 5 days
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

    // the owner opened the first 10 nights; nights 3 and 4 are already booked by someone else
    private val calendar = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = nights.take(10),
        heldNights = listOf(nights[2], nights[3]),
    )

    private fun samsEntry(status: EntryStatus) = LotteryEntry(
        id = LotteryEntry.idFor("lot-summer", "test-device"),
        lotteryId = "lot-summer",
        userId = "test-device",
        groupSize = 4,
        status = status,
    )

    /** Shows the Reserve screen for Sam's win, loading from fake repositories. */
    private fun openReserve(
        theLottery: Lottery = lottery,
        theCalendar: AvailabilityCalendar = calendar,
        status: EntryStatus = EntryStatus.WON,
    ) {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking { campsites.saveAvailability(theCalendar) }
        val controller = ReserveController(
            campsites,
            FakeLotteryRepository(listOf(theLottery), listOf(samsEntry(status))),
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = "hinton",
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", "hinton", onBack = {}, controller = controller)
            }
        }
    }

    private fun night(index: Int) = composeRule.onNodeWithTag("night_${nights[index]}")

    private fun hasState(state: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    /** Starts the whole app for "test-device", whose entry in the lottery has [status]. */
    private fun startApp(status: EntryStatus) {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking { campsites.saveAvailability(calendar) }
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(listOf(lottery), listOf(samsEntry(status))),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }
    }

    /** Checks that the Reserve screen is open for Sam's win at Hinton. */
    private fun assertReserveIsOpen() {
        composeRule.onNodeWithTag("reserve_banner")
            .assertTextEquals("You won! Reserve by ${shortDate(lottery.bookingEnd)}")
        composeRule.onNodeWithTag("reserve_campsite").assertTextEquals("Hinton Ridge Campground")
    }

    @Test
    fun winnerOpensReserveFromCampsiteDetails() {
        startApp(EntryStatus.WON)

        composeRule.onNodeWithTag("search_field").performTextInput("Hinton")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("You won! Reserve by", substring = true).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("reserve_button").performScrollTo().performClick()

        assertReserveIsOpen()
    }

    @Test
    fun noReserveButtonForSomeoneWhoLost() {
        startApp(EntryStatus.LOST)

        composeRule.onNodeWithTag("search_field").performTextInput("Hinton")
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("Not selected this time").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reserve_button").assertDoesNotExist()
    }

    @Test
    fun reserveShowsEveryNightOfTheLottery() {
        openReserve()
        for (index in nights.indices) {
            night(index).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun onlyFreeNightsCanBePicked() {
        openReserve()

        // booked by another winner
        night(2).assert(hasState("Taken"))
        // never opened by the owner
        night(12).assert(hasState("Taken"))
        night(0).assert(hasState("Free"))

        night(2).performClick()
        night(2).assert(hasState("Taken"))
        night(0).performClick()
        night(0).assert(hasState("Selected"))
    }

    @Test
    fun priceAndTotalFollowThePickedNights() {
        openReserve()
        composeRule.onNodeWithTag("reserve_total").assertTextEquals("Total $0")

        night(5).performClick()
        night(6).performClick()

        composeRule.onNodeWithTag("reserve_nights_price").assertTextEquals("2 nights × $50")
        composeRule.onNodeWithTag("reserve_total").assertTextEquals("Total $100")
        composeRule.onNodeWithText("Rules: Quiet hours 10 pm to 7 am").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun saysWhenNoNightsAreLeft() {
        openReserve(theCalendar = calendar.copy(heldNights = calendar.openNights))
        composeRule.onNodeWithTag("reserve_no_nights").performScrollTo().assertIsDisplayed()
        night(0).assert(hasState("Taken"))
    }

    @Test
    fun saysTheOfferExpiredAfterTheBookingPeriod() {
        val ended = lottery.copy(bookingStart = now - 10 * day, bookingEnd = now - day)
        openReserve(theLottery = ended)

        composeRule.onNodeWithTag("reserve_banner").assertTextEquals(
            "This offer expired on ${shortDate(ended.bookingEnd)}. You can no longer reserve.",
        )
        night(0).assert(hasState("Taken"))
        night(0).performClick()
        composeRule.onNodeWithTag("reserve_total").assertTextEquals("Total $0")
    }
}
