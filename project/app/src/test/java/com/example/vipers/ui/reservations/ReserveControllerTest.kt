/*
 * ReserveControllerTest.kt
 * Unit tests for ReserveController (US 01.04.01, T-03, US 01.04.03): which nights a winner can
 * pick, where the win stands during and after the booking period, picking nights in a row, group
 * size, reserving nights and handling conflicts, and declining the win, using fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ReserveResult
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
        reservationRepo: FakeReservationRepository? = null,
    ): ReserveController = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        campsites.saveAvailability(calendar)
        val entries = if (status == null) emptyList() else listOf(entry(status))
        val lotteries = FakeLotteryRepository(listOf(theLottery), entries)
        val reservations = reservationRepo ?: FakeReservationRepository()
        reservations.attachRepositories(campsites, lotteries)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
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
            campsiteRepository = FakeCampsiteRepository(listOf(hinton)),
            lotteryRepository = failing,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
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
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(
                listOf(lottery),
                listOf(entry(EntryStatus.WON)),
            ),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
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
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(
                listOf(lottery),
                listOf(entry(EntryStatus.WON)),
            ),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        controller.fetchData(duringBooking)

        assertEquals(listOf("2027-05-22"), controller.selectedNights)
    }

    @Test
    fun fetchData_initializesGroupSizeFromLotteryEntry() {
        val controller = loaded()
        assertEquals(4, controller.groupSize)
    }

    @Test
    fun groupSize_boundsCheckingAndControls() {
        val controller = loaded()
        assertEquals(4, controller.groupSize)

        controller.incrementGroupSize()
        assertEquals(5, controller.groupSize)

        controller.onGroupSizeChange(8)
        assertEquals(8, controller.groupSize)

        // Cannot exceed campsite.maxOccupancy (8)
        controller.incrementGroupSize()
        assertEquals(8, controller.groupSize)
        controller.onGroupSizeChange(10)
        assertEquals(8, controller.groupSize)

        // Decrement down to 1
        controller.onGroupSizeChange(2)
        controller.decrementGroupSize()
        assertEquals(1, controller.groupSize)
        controller.decrementGroupSize()
        assertEquals(1, controller.groupSize)
    }

    @Test
    fun reserveNights_createsReservationWithChosenNightsAndGroupSize() = runBlocking {
        val controller = loaded()
        controller.onNightTap("2027-05-22")
        controller.onNightTap("2027-05-23")
        controller.onGroupSizeChange(5)

        val result = controller.reserveNights(duringBooking)
        assertTrue(result is ReserveResult.Success)

        val reservation = controller.confirmedReservation
        assertNotNull(reservation)
        assertEquals("2027-05-22", reservation?.checkIn)
        assertEquals("2027-05-24", reservation?.checkOut)
        assertEquals(5, reservation?.groupSize)
        assertEquals(10000L, reservation?.totalPriceCents)
        assertEquals(OfferState.USED, controller.offerState)
        assertNull(controller.reserveError)
        assertFalse(controller.isReserving)
    }

    @Test
    fun reserveNights_failsWhenNoNightsPicked() = runBlocking {
        val controller = loaded()
        assertTrue(controller.selectedNights.isEmpty())

        val result = controller.reserveNights(duringBooking)
        assertTrue(result is ReserveResult.Error)
        assertEquals("Please pick at least one night.", controller.reserveError)
        assertNull(controller.confirmedReservation)
    }

    @Test
    fun reserveNights_conflict_refreshesCalendarWhenAnotherWinnerBooksSameNights() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        campsites.saveAvailability(hintonCalendar)
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val reservations = FakeReservationRepository(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
        )
        controller.fetchData(duringBooking)

        controller.onNightTap("2027-05-22")
        controller.onNightTap("2027-05-23")

        // Another winner books May 22 while Sam was viewing the screen
        val cal = campsites.getAvailability("hinton")
        campsites.saveAvailability(cal.copy(heldNights = cal.heldNights + "2027-05-22"))

        val result = controller.reserveNights(duringBooking)
        assertEquals(ReserveResult.Conflict, result)
        assertNotNull(controller.reserveError)
        assertTrue(
            controller.reserveError!!.contains("These nights were just booked by another winner"),
        )

        // Calendar refreshed: May 22 is no longer free, pick was cleared
        assertFalse("2027-05-22" in controller.freeNights)
        assertTrue(controller.selectedNights.isEmpty())
    }

    @Test
    fun reserveNights_failsWhenOfferNotOpen() = runBlocking {
        val controller = loaded(status = EntryStatus.LOST)
        val result = controller.reserveNights(duringBooking)
        assertEquals(ReserveResult.NotEligible, result)
        assertEquals(
            "Only winners with an open booking period can reserve.",
            controller.reserveError,
        )
    }

    @Test
    fun dismissConfirmation_clearsConfirmedReservation() = runBlocking {
        val controller = loaded()
        controller.onNightTap("2027-05-22")
        controller.reserveNights(duringBooking)
        assertNotNull(controller.confirmedReservation)

        controller.dismissConfirmation()
        assertNull(controller.confirmedReservation)
    }

    @Test
    fun clearReserveError_clearsError() = runBlocking {
        val controller = loaded()
        controller.reserveNights(duringBooking)
        assertEquals("Please pick at least one night.", controller.reserveError)

        controller.clearReserveError()
        assertNull(controller.reserveError)
    }

    @Test
    fun totalPrice_formatsCentsCorrectly() = runBlocking {
        val oddCampsite = hinton.copy(pricePerNightCents = 6550) // $65.50
        val oddCampsites = FakeCampsiteRepository(listOf(oddCampsite))
        oddCampsites.saveAvailability(hintonCalendar)
        val oddLotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val controller = ReserveController(
            campsiteRepository = oddCampsites,
            lotteryRepository = oddLotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(oddCampsites, oddLotteries),
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        assertEquals("Total $65.50", controller.totalText)

        val result = controller.reserveNights(duringBooking)
        assertTrue(result is ReserveResult.Success)
        val res = (result as ReserveResult.Success).reservation
        assertEquals(6550L, res.totalPriceCents)
        assertEquals("$65.50", res.totalPriceText)
    }

    @Test
    fun reserveNights_sendsReservationConfirmedNotificationToWinner() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val notifications = FakeNotificationRepository()
        val reservations = FakeReservationRepository(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            notificationRepository = notifications,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = reservations,
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        val result = controller.reserveNights(duringBooking)
        assertTrue(result is ReserveResult.Success)

        val inbox = notifications.getNotificationsFor("user-sam")
        assertEquals(1, inbox.size)
        val notice = inbox.first()
        assertEquals(NotificationType.RESERVATION_CONFIRMED, notice.type)
        assertEquals("Reservation confirmed", notice.title)
        assertTrue(notice.message.contains(hinton.name))
        assertEquals((result as ReserveResult.Success).reservation.id, notice.reservationId)
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
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
        )
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
        // and Reserve (T-03) refuses the declined win
        assertEquals(ReserveResult.NotEligible, controller.reserveNights(duringBooking))
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
