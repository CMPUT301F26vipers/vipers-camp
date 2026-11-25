/*
 * ViewDatesAfterWinningTest.kt
 * Intent tests for US 01.04.01 and US 01.04.02: a winner opens the Reserve screen (mockup E11)
 * from Campsite Details or from the win notification, selects among campsites in a multi-site
 * lottery, and sees the reserve-by date, the lottery's nights with taken ones greyed out, and the
 * price and total for the nights they pick; plus the "no nights left" and "offer expired" cases.
 * Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.common.shortDate
import com.example.vipers.ui.lottery.LotteryDrawer
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
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
        val lotteries = FakeLotteryRepository(listOf(theLottery), listOf(samsEntry(status)))
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            lotteryDrawer = LotteryDrawer(
                lotteries,
                FakeNotificationRepository(),
                campsites,
                FakeProfileRepository(),
            ),
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

    // the win notification T-13 shows in the inbox (mockup E10)
    private val winNotification = Notification(
        id = "win", recipientId = "test-device", type = NotificationType.WON,
        lotteryId = "lot-summer", campsiteId = "hinton", title = "You won the lottery!",
        message = "Hinton Ridge Campground: reserve by ${shortDate(lottery.bookingEnd)}",
        createdAt = now - 60 * 60 * 1000L,
    )

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
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(listOf(winNotification)),
            imageRepository = FakeImageRepository(),
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
    fun winnerOpensReserveFromTheWinNotification() {
        startApp(EntryStatus.WON)

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("You won the lottery!").performClick()

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

    // US 01.04.02: choosing campsite and dates in a multi-campsite lottery

    private val jasper = Campsite(
        id = "jasper",
        ownerId = "owner-priya",
        name = "Jasper Pines",
        address = "Jasper, AB",
        region = Region.JASPER,
        maxOccupancy = 6,
        pricePerNightCents = 6500,
        rules = "No campfires during fire ban",
        cancellationPolicy = "No refunds within 48 hours",
    )

    private val multiLottery = lottery.copy(campsiteIds = listOf("hinton", "jasper"))

    private val hintonDisjointCalendar = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = nights.take(4),
    )

    private val jasperDisjointCalendar = AvailabilityCalendar(
        campsiteId = "jasper",
        openNights = nights.drop(5).take(4),
    )

    private fun startMultiSiteApp(
        hintonCal: AvailabilityCalendar = hintonDisjointCalendar,
        jasperCal: AvailabilityCalendar = jasperDisjointCalendar,
    ) {
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        runBlocking {
            campsites.saveAvailability(hintonCal)
            campsites.saveAvailability(jasperCal)
        }
        val multiWinNotice = winNotification.copy(
            campsiteId = null,
            message = "You won a spot at Hinton Ridge Campground, Jasper Pines.",
        )
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(
                listOf(multiLottery),
                listOf(samsEntry(EntryStatus.WON)),
            ),
            reservationRepository = FakeReservationRepository(campsites, FakeLotteryRepository()),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(listOf(multiWinNotice)),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }
    }

    @Test
    fun multiSite_fromCampsiteDetailsHint_preselectsSiteAndShowsSelector() {
        startMultiSiteApp()

        composeRule.onNodeWithTag("search_field").performTextInput("Jasper")
        composeRule.onNodeWithText("Jasper Pines").performClick()
        composeRule.onNodeWithTag("reserve_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("reserve_campsite_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("reserve_campsite_option_jasper").assert(hasState("Selected"))
        composeRule.onNodeWithTag("reserve_campsite_option_hinton").assert(hasState("Unselected"))
        night(5).assert(hasState("Free"))
        night(0).assert(hasState("Taken"))
        composeRule.onNodeWithText("Rules: No campfires during fire ban").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun multiSite_fromWinNotification_listsSitesFirst_andSwitchingClearsPickedNights() {
        startMultiSiteApp()

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("You won the lottery!").performClick()

        composeRule.onNodeWithTag("reserve_campsite_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("reserve_campsite_option_hinton").assert(hasState("Unselected"))
        composeRule.onNodeWithTag("reserve_campsite_option_jasper").assert(hasState("Unselected"))
        composeRule.onNodeWithTag("reserve_choose_campsite_prompt").assertIsDisplayed()
        composeRule.onNodeWithTag("button_reserve").assertDoesNotExist()

        // Choose Hinton first and pick a night
        composeRule.onNodeWithTag("reserve_campsite_option_hinton").performClick()
        composeRule.onNodeWithTag("reserve_campsite_option_hinton").assert(hasState("Selected"))
        night(0).assert(hasState("Free"))
        night(5).assert(hasState("Taken"))
        night(0).performClick()
        composeRule.onNodeWithTag("reserve_nights_price").assertTextEquals("1 night × $50")
        composeRule.onNodeWithTag("reserve_total").assertTextEquals("Total $50")
        composeRule.onNodeWithText("Rules: Quiet hours 10 pm to 7 am").performScrollTo()
            .assertIsDisplayed()

        // Switch to Jasper: picked night clears, disjoint calendar and rules update
        composeRule.onNodeWithTag("reserve_campsite_option_jasper").performScrollTo().performClick()
        composeRule.onNodeWithTag("reserve_campsite_option_jasper").assert(hasState("Selected"))
        night(0).assert(hasState("Taken"))
        night(5).assert(hasState("Free"))
        composeRule.onNodeWithTag("reserve_nights_price").assertTextEquals("0 nights × $65")
        composeRule.onNodeWithTag("reserve_total").assertTextEquals("Total $0")
        composeRule.onNodeWithText("Rules: No campfires during fire ban").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun multiSite_noNightsLeftAtChosenSite_promptsOtherChoicesAndCanSwitch() {
        startMultiSiteApp(
            hintonCal = hintonDisjointCalendar.copy(heldNights = hintonDisjointCalendar.openNights),
            jasperCal = jasperDisjointCalendar,
        )

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        composeRule.onNodeWithText("You won the lottery!").performClick()

        composeRule.onNodeWithTag("reserve_campsite_option_hinton").performClick()
        composeRule.onNodeWithTag("reserve_no_nights").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "No nights are left at this campsite. Choose another campsite or decline your win " +
                "so someone else gets a chance.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("reserve_campsite_option_jasper").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reserve_no_nights").fetchSemanticsNodes().isEmpty()
        }
        night(5).assert(hasState("Free"))
    }
}
