/*
 * ReserveControllerTest.kt
 * Unit tests for ReserveController (US 01.04.01): which nights a winner can pick, where the win
 * stands during and after the booking period, picking nights in a row, and the total, and declining
 * the win (US 01.04.03), using the fake campsite and lottery repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReserveControllerTest {

    /** A date and time on this computer's clock, in milliseconds. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // booking runs May 12 to May 31, 2027; the tests run on May 14 unless they say otherwise
    private val duringBooking = at("2027-05-14T12:00")

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
        rules = "Quiet hours 10 pm to 7 am.",
    )
    private val jasper = Campsite(id = "jasper", name = "Jasper Pines", pricePerNightCents = 6500)

    // the two weeks from May 17 to May 30, like the 14 nights on mockup E11
    private val lottery = Lottery(
        id = "lot-may",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton", "jasper"),
        nightsOffered = (17..30).map { "2027-05-$it" },
        registrationStart = at("2027-05-01T09:00"),
        registrationEnd = at("2027-05-10T15:00"),
        bookingStart = at("2027-05-12T00:00"),
        bookingEnd = at("2027-05-31T23:59"),
        drawnAt = at("2027-05-10T15:15"),
    )

    // the owner opened May 17-27; May 20 and 21 are already booked by another winner
    private val hintonCalendar = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = (17..27).map { "2027-05-$it" },
        heldNights = listOf("2027-05-20", "2027-05-21"),
    )

    private fun entry(status: EntryStatus, userId: String = "user-sam") = LotteryEntry(
        id = LotteryEntry.idFor("lot-may", userId),
        lotteryId = "lot-may",
        userId = userId,
        groupSize = 4,
        status = status,
    )

    /** A controller for Sam's win in [theLottery], loaded at time [now]. */
    private fun loaded(
        status: EntryStatus? = EntryStatus.WON,
        now: Long = duringBooking,
        campsiteId: String? = "hinton",
        theLottery: Lottery = lottery,
        calendar: AvailabilityCalendar = hintonCalendar,
    ): ReserveController = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        campsites.saveAvailability(calendar)
        val entries = if (status == null) emptyList() else listOf(entry(status))
        val controller = ReserveController(
            campsites,
            FakeLotteryRepository(listOf(theLottery), entries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = campsiteId,
        )
        controller.fetchData(now)
        controller
    }

    @Test
    fun fetchData_showsEveryOfferedNightAndOnlyFreeOnesCanBePicked() {
        val controller = loaded()

        assertEquals(14, controller.nights.size)
        // May 20 and 21 are booked and May 28-30 were never opened
        val expected = listOf(17, 18, 19, 22, 23, 24, 25, 26, 27).map { "2027-05-$it" }
        assertEquals(expected, controller.freeNights)
        assertEquals(OfferState.OPEN, controller.offerState)
        assertEquals("Hinton Ridge Campground", controller.campsite?.name)
        assertFalse(controller.noNightsLeft)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_usesTheLotterysFirstCampsiteWhenNoneIsGiven() {
        assertEquals("hinton", loaded(campsiteId = null).campsite?.id)
        // a campsite this lottery doesn't cover can't be reserved with its win
        assertEquals("hinton", loaded(campsiteId = "banff").campsite?.id)
        assertEquals("jasper", loaded(campsiteId = "jasper").campsite?.id)
    }

    @Test
    fun fetchData_saysNoNightsLeftWhenEveryOfferedNightIsTaken() {
        val full = hintonCalendar.copy(heldNights = hintonCalendar.openNights)
        val controller = loaded(calendar = full)

        assertTrue(controller.freeNights.isEmpty())
        assertTrue(controller.noNightsLeft)
    }

    @Test
    fun fetchData_expiresTheOfferAfterTheBookingPeriod() {
        val controller = loaded(now = at("2027-06-01T08:00"))

        assertEquals(OfferState.EXPIRED, controller.offerState)
        // an expired offer isn't the same as no nights left
        assertFalse(controller.noNightsLeft)
    }

    @Test
    fun fetchData_waitsForTheBookingPeriodToStart() {
        val controller = loaded(now = at("2027-05-11T12:00"))
        assertEquals(OfferState.NOT_YET_OPEN, controller.offerState)
    }

    @Test
    fun fetchData_onlyLetsAWinnerPickNights() {
        assertEquals(OfferState.NO_WIN, loaded(status = EntryStatus.LOST).offerState)
        assertEquals(OfferState.NO_WIN, loaded(status = EntryStatus.WAITING).offerState)
        assertEquals(OfferState.NO_WIN, loaded(status = null).offerState)
        assertEquals(OfferState.USED, loaded(status = EntryStatus.RESERVED).offerState)
        assertEquals(OfferState.USED, loaded(status = EntryStatus.DECLINED).offerState)
        assertEquals(OfferState.EXPIRED, loaded(status = EntryStatus.EXPIRED).offerState)
    }

    @Test
    fun fetchData_leavesTheLotteryNullWhenItWasRemoved() {
        val controller = loaded(theLottery = lottery.copy(removed = true))

        assertNull(controller.lottery)
        assertTrue(controller.nights.isEmpty())
        assertEquals(OfferState.NO_WIN, controller.offerState)
    }

    @Test
    fun fetchData_showsAnErrorWhenLoadingFails() = runBlocking {
        val failing = object : LotteryRepository by FakeLotteryRepository() {
            override suspend fun getLottery(id: String): Lottery? =
                throw IllegalStateException("offline")
        }
        val controller = ReserveController(
            FakeCampsiteRepository(listOf(hinton)), failing, "user-sam", "lot-may", "hinton",
        )
        controller.fetchData(duringBooking)

        assertEquals("Unable to load your win.", controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun onNightTap_picksNightsInARow() {
        val controller = loaded()

        controller.onNightTap("2027-05-23")
        controller.onNightTap("2027-05-24")
        controller.onNightTap("2027-05-22")

        assertEquals(listOf("2027-05-22", "2027-05-23", "2027-05-24"), controller.selectedNights)
    }

    @Test
    fun onNightTap_removesTheFirstOrLastPickedNight() {
        val controller = loaded()
        controller.onNightTap("2027-05-22")
        controller.onNightTap("2027-05-23")
        controller.onNightTap("2027-05-24")

        controller.onNightTap("2027-05-24")
        controller.onNightTap("2027-05-22")

        assertEquals(listOf("2027-05-23"), controller.selectedNights)
        controller.onNightTap("2027-05-23")
        assertTrue(controller.selectedNights.isEmpty())
    }

    @Test
    fun onNightTap_startsANewPickForANightThatIsNotNextToThePickedOnes() {
        val controller = loaded()
        controller.onNightTap("2027-05-17")
        controller.onNightTap("2027-05-18")

        controller.onNightTap("2027-05-25")

        assertEquals(listOf("2027-05-25"), controller.selectedNights)
    }

    @Test
    fun onNightTap_ignoresTakenAndClosedNights() {
        val controller = loaded()
        controller.onNightTap("2027-05-19")

        // May 20 is booked, so the stay can't go past May 19
        controller.onNightTap("2027-05-20")
        // May 29 was never opened
        controller.onNightTap("2027-05-29")

        assertEquals(listOf("2027-05-19"), controller.selectedNights)
    }

    @Test
    fun onNightTap_doesNothingWhenTheOfferIsNotOpen() {
        val controller = loaded(now = at("2027-06-01T08:00"))
        controller.onNightTap("2027-05-22")
        assertTrue(controller.selectedNights.isEmpty())
    }

    @Test
    fun totalText_isTheNightlyPriceTimesThePickedNights() {
        val controller = loaded()
        assertEquals("0 nights × $50", controller.nightsTimesPriceText)
        assertEquals("Total $0", controller.totalText)

        controller.onNightTap("2027-05-22")
        assertEquals("1 night × $50", controller.nightsTimesPriceText)

        controller.onNightTap("2027-05-23")
        // 2 nights at $50, like mockup E11
        assertEquals("2 nights × $50", controller.nightsTimesPriceText)
        assertEquals("Total $100", controller.totalText)
    }

    @Test
    fun fetchData_clearsThePickWhenAPickedNightWasBookedSinceTheLastLoad() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val controller = ReserveController(
            campsites,
            FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON))),
            "user-sam",
            "lot-may",
            "hinton",
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")
        controller.onNightTap("2027-05-23")
        controller.onNightTap("2027-05-24")

        // another winner reserves May 23 while this screen is open, so 22 and 24 aren't in a row
        campsites.saveAvailability(hintonCalendar.copy(heldNights = listOf("2027-05-23")))
        controller.fetchData(duringBooking)

        assertTrue(controller.selectedNights.isEmpty())
        assertFalse("2027-05-23" in controller.freeNights)
        assertNotNull(controller.lottery)
    }

    @Test
    fun fetchData_keepsThePickWhenItsNightsAreStillFree() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val controller = ReserveController(
            campsites,
            FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON))),
            "user-sam",
            "lot-may",
            "hinton",
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        controller.fetchData(duringBooking)

        assertEquals(listOf("2027-05-22"), controller.selectedNights)
    }

    // US 01.04.03: declining the win

    /** Counts declineTransaction calls on [lotteries], so a test can see a second tap made none. */
    private class CountingLotteryRepository(private val lotteries: LotteryRepository) :
        LotteryRepository by lotteries {
        var declineCalls = 0

        override suspend fun declineTransaction(lotteryId: String, userId: String): Boolean {
            declineCalls++
            return lotteries.declineTransaction(lotteryId, userId)
        }
    }

    /** Sam's [status] entry in [lottery], held in a counting fake so tests can check what was saved. */
    private fun lotteriesWith(status: EntryStatus) =
        CountingLotteryRepository(FakeLotteryRepository(listOf(lottery), listOf(entry(status))))

    /** A controller for Sam's win, loaded from [lotteries] during booking. */
    private suspend fun loadedFrom(lotteries: LotteryRepository): ReserveController {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val controller = ReserveController(campsites, lotteries, "user-sam", "lot-may", "hinton")
        controller.fetchData(duringBooking)
        return controller
    }

    @Test
    fun decline_wonEntry_setsDeclinedAndCanDeclineFalse() = runBlocking {
        val lotteries = lotteriesWith(EntryStatus.WON)
        val controller = loadedFrom(lotteries)
        assertTrue(controller.canDecline)

        controller.decline(duringBooking)

        assertEquals(EntryStatus.DECLINED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(EntryStatus.DECLINED, controller.entry?.status)
        assertFalse(controller.canDecline)
        assertNull(controller.declineError)
        assertFalse(controller.isDeclining)
    }

    @Test
    fun decline_calledTwice_changesNothingMore() = runBlocking {
        val lotteries = lotteriesWith(EntryStatus.WON)
        val controller = loadedFrom(lotteries)

        controller.decline(duringBooking)
        controller.decline(duringBooking)

        // the second call sees the reloaded DECLINED entry and doesn't write again
        assertEquals(1, lotteries.declineCalls)
        assertEquals(EntryStatus.DECLINED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertFalse(controller.canDecline)
        assertNull(controller.declineError)
    }

    @Test
    fun decline_reservedEntry_changesNothing() = runBlocking {
        val lotteries = lotteriesWith(EntryStatus.RESERVED)
        val controller = loadedFrom(lotteries)
        assertFalse(controller.canDecline)

        controller.decline(duringBooking)

        assertEquals(0, lotteries.declineCalls)
        assertEquals(EntryStatus.RESERVED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(OfferState.USED, controller.offerState)
    }

    @Test
    fun decline_wonEntry_canNoLongerReserveFromThisWin() = runBlocking {
        val controller = loadedFrom(lotteriesWith(EntryStatus.WON))

        controller.decline(duringBooking)

        val declined = controller.entry!!
        assertFalse(declined.canReserve(controller.lottery!!, duringBooking))
        assertEquals(OfferState.USED, controller.offerState)
        // nights can't be picked any more either
        controller.onNightTap("2027-05-22")
        assertTrue(controller.selectedNights.isEmpty())
    }

    @Test
    fun decline_outsideBooking_changesNothing() = runBlocking {
        val lotteries = lotteriesWith(EntryStatus.WON)
        val controller = loadedFrom(lotteries)

        // the screen was left open until booking ended
        controller.decline(at("2027-06-01T08:00"))

        assertEquals(0, lotteries.declineCalls)
        assertEquals(EntryStatus.WON, lotteries.getEntry("lot-may", "user-sam")?.status)
    }

    @Test
    fun decline_whenTheRepositoryFails_setsDeclineErrorAndKeepsTheWin() = runBlocking {
        val failing = object : LotteryRepository by lotteriesWith(EntryStatus.WON) {
            override suspend fun declineTransaction(lotteryId: String, userId: String): Boolean =
                throw IllegalStateException("offline")
        }
        val controller = loadedFrom(failing)

        controller.decline(duringBooking)

        assertEquals("Unable to decline your win. Please try again.", controller.declineError)
        assertEquals(EntryStatus.WON, failing.getEntry("lot-may", "user-sam")?.status)
        assertTrue(controller.canDecline)
        assertFalse(controller.isDeclining)
        assertNull(controller.error) // the rest of the screen stays on show
    }
}
