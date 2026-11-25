/*
 * ReserveControllerTest.kt
 * Unit tests for ReserveController (US 01.04.01, US 01.04.02, T-03, US 01.04.03): which nights a
 * winner can pick, single-site vs multi-site campsite selection and switching races, where the win
 * stands during and after the booking period, picking nights in a row, group size limits,
 * reserving nights and handling conflicts, and declining the win, using fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

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
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.DeclineTransactionResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ReserveResult
import com.example.vipers.ui.lottery.LotteryDrawer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

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

    private fun drawerFor(
        lotteries: LotteryRepository,
        campsites: CampsiteRepository,
        notifications: NotificationRepository = FakeNotificationRepository(),
        random: Random = Random(42),
    ) = LotteryDrawer(
        lotteryRepository = lotteries,
        notificationRepository = notifications,
        campsiteRepository = campsites,
        profileRepository = FakeProfileRepository(),
        random = random,
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
            lotteryDrawer = drawerFor(lotteries, campsites),
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
    fun fetchData_usesSoleActiveSiteOrValidRouteHint_andLeavesMultiSiteUnselectedWithoutHint() = runBlocking {
        // Multi-site lottery with valid hint preselects it
        val hinted = loaded(campsiteId = "jasper")
        assertTrue(hinted.isMultiSite)
        assertEquals(listOf("hinton", "jasper"), hinted.availableCampsites.map { it.id })
        assertEquals("jasper", hinted.campsite?.id)

        // Multi-site lottery without hint or with invalid hint leaves campsite unselected
        val unhinted = loaded(campsiteId = null)
        assertTrue(unhinted.isMultiSite)
        assertNull(unhinted.campsite)
        assertTrue(unhinted.freeNights.isEmpty())

        val invalidHint = loaded(campsiteId = "banff")
        assertNull(invalidHint.campsite)
        assertTrue(invalidHint.freeNights.isEmpty())

        // Single-site lottery auto-selects its sole active campsite even without a valid hint
        val singleSiteLottery = lottery.copy(campsiteIds = listOf("hinton"))
        val single = loaded(campsiteId = null, theLottery = singleSiteLottery)
        assertFalse(single.isMultiSite)
        assertEquals("hinton", single.campsite?.id)
        assertEquals("hinton", loaded(campsiteId = "banff", theLottery = singleSiteLottery).campsite?.id)

        // Multi-site lottery with only one remaining active campsite falls back to single-site layout
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper.copy(removed = true)))
        campsites.saveAvailability(hintonCalendar)
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val singleRemaining = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites),
        )
        singleRemaining.fetchData(duringBooking)
        assertFalse(singleRemaining.isMultiSite)
        assertEquals("hinton", singleRemaining.campsite?.id)
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
        val campsites = FakeCampsiteRepository(listOf(hinton))
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = failing,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
            lotteryDrawer = drawerFor(failing, campsites),
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
        val lotteries = FakeLotteryRepository(
            listOf(lottery),
            listOf(entry(EntryStatus.WON)),
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
            lotteryDrawer = drawerFor(lotteries, campsites),
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
        val lotteries = FakeLotteryRepository(
            listOf(lottery),
            listOf(entry(EntryStatus.WON)),
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
            lotteryDrawer = drawerFor(lotteries, campsites),
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
            lotteryDrawer = drawerFor(lotteries, campsites),
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
            lotteryDrawer = drawerFor(oddLotteries, oddCampsites),
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
            lotteryDrawer = drawerFor(lotteries, campsites, notifications),
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

    // US 01.04.03 / T-12: declining the win and drawing a replacement

    /** Counts declineTransaction calls on [lotteries], so a test can see a second tap made none. */
    private class CountingLotteryRepository(val lotteries: LotteryRepository) :
        LotteryRepository by lotteries {
        var declineCalls = 0

        override suspend fun declineTransaction(
            lotteryId: String,
            userId: String,
            candidateUserId: String?,
            replacementNotice: Notification?,
            now: Long,
        ): DeclineTransactionResult {
            declineCalls++
            return lotteries.declineTransaction(lotteryId, userId, candidateUserId, replacementNotice, now)
        }
    }

    /** Sam's [status] entry in [lottery] plus [extraEntries], held in a counting fake. */
    private fun lotteriesWith(
        status: EntryStatus,
        extraEntries: List<LotteryEntry> = emptyList(),
        notifications: NotificationRepository? = null,
    ) = CountingLotteryRepository(
        FakeLotteryRepository(
            listOf(lottery),
            listOf(entry(status)) + extraEntries,
            notificationRepository = notifications,
        ),
    )

    /** A controller for Sam's win, loaded from [lotteries] during booking. */
    private suspend fun loadedFrom(
        lotteries: LotteryRepository,
        notifications: NotificationRepository = FakeNotificationRepository(),
        random: Random = Random(42),
    ): ReserveController {
        val sharedNotifications = (lotteries as? CountingLotteryRepository)?.let {
            (it.lotteries as? FakeLotteryRepository)?.notificationRepository
        } ?: (lotteries as? FakeLotteryRepository)?.notificationRepository ?: notifications.also { shared ->
            (lotteries as? CountingLotteryRepository)?.let {
                (it.lotteries as? FakeLotteryRepository)?.let { fake -> fake.notificationRepository = shared }
            }
            (lotteries as? FakeLotteryRepository)?.let { it.notificationRepository = shared }
        }
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(),
            lotteryDrawer = drawerFor(lotteries, campsites, sharedNotifications, random),
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
            override suspend fun declineTransaction(
                lotteryId: String,
                userId: String,
                candidateUserId: String?,
                replacementNotice: Notification?,
                now: Long,
            ): DeclineTransactionResult = throw IllegalStateException("offline")
        }
        val controller = loadedFrom(failing)

        controller.decline(duringBooking)

        assertEquals("Unable to decline your win. Please try again.", controller.declineError)
        assertEquals(EntryStatus.WON, failing.getEntry("lot-may", "user-sam")?.status)
        assertTrue(controller.canDecline)
        assertFalse(controller.isDeclining)
        assertNull(controller.error) // the rest of the screen stays on show
    }

    @Test
    fun decline_withLostEntrant_promotesReplacementAndDeliversWonNotice() = runBlocking {
        val notifications = FakeNotificationRepository()
        val lotteries = lotteriesWith(
            EntryStatus.WON,
            extraEntries = listOf(entry(EntryStatus.LOST, userId = "user-jo")),
            notifications = notifications,
        )
        val controller = loadedFrom(lotteries, notifications = notifications)

        controller.decline(duringBooking)

        assertEquals(EntryStatus.DECLINED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(EntryStatus.WON, lotteries.getEntry("lot-may", "user-jo")?.status)
        val joNotices = notifications.getNotificationsFor("user-jo")
        assertEquals(1, joNotices.size)
        assertEquals("replacement_lot-may_user-jo", joNotices[0].id)
        assertEquals(NotificationType.WON, joNotices[0].type)
        assertEquals(duringBooking, joNotices[0].createdAt)
    }

    @Test
    fun decline_whenNoticePersistenceFails_keepsWinWithErrorAndRetrySucceedsOnce() = runBlocking {
        val realNotifications = FakeNotificationRepository()
        var failSend = true
        val flakyNotifications = object : NotificationRepository by realNotifications {
            override suspend fun addAll(notifications: List<Notification>) {
                if (failSend) throw IllegalStateException("notification write failed")
                realNotifications.addAll(notifications)
            }
        }
        val lotteries = lotteriesWith(
            EntryStatus.WON,
            extraEntries = listOf(
                entry(EntryStatus.LOST, userId = "user-jo"),
                entry(EntryStatus.LOST, userId = "user-kim"),
            ),
            notifications = flakyNotifications,
        )

        val firstController = loadedFrom(lotteries, notifications = flakyNotifications, random = Random(42))
        firstController.decline(duringBooking)

        assertEquals(1, lotteries.declineCalls)
        assertEquals("Unable to decline your win. Please try again.", firstController.declineError)
        assertEquals(EntryStatus.WON, firstController.entry?.status)
        assertEquals(OfferState.OPEN, firstController.offerState)
        assertTrue(firstController.canDecline)
        assertEquals(EntryStatus.WON, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(EntryStatus.LOST, lotteries.getEntry("lot-may", "user-jo")?.status)
        assertEquals(EntryStatus.LOST, lotteries.getEntry("lot-may", "user-kim")?.status)
        assertTrue(realNotifications.getNotificationsFor("user-jo").isEmpty())
        assertTrue(realNotifications.getNotificationsFor("user-kim").isEmpty())

        // Network returns: retrying decline commits the decline, promotion, and inbox notice together.
        failSend = false
        val secondController = loadedFrom(lotteries, notifications = flakyNotifications, random = Random(99))
        secondController.decline(duringBooking)
        val callsAfterSuccess = lotteries.declineCalls
        secondController.decline(duringBooking)

        assertEquals(callsAfterSuccess, lotteries.declineCalls)
        assertNull(secondController.declineError)
        assertEquals(EntryStatus.DECLINED, secondController.entry?.status)
        assertEquals(OfferState.USED, secondController.offerState)
        assertFalse(secondController.canDecline)

        val entries = lotteries.getEntries("lot-may").associateBy { it.userId }
        val promotedId = listOf("user-jo", "user-kim").single { entries[it]?.status == EntryStatus.WON }
        val notices = realNotifications.getNotificationsFor(promotedId)
        assertEquals(1, notices.size)
        assertEquals("replacement_lot-may_$promotedId", notices[0].id)
        assertEquals(duringBooking, notices[0].createdAt)
        val otherLost = if (promotedId == "user-jo") "user-kim" else "user-jo"
        assertEquals(EntryStatus.LOST, entries[otherLost]?.status)
        assertTrue(realNotifications.getNotificationsFor(otherLost).isEmpty())
    }

    // US 01.04.02: choosing campsite and dates in a multi-campsite lottery

    @Test
    fun fetchData_loadsDistinctActiveCampsitesInStoredOrder_skippingMissingAndRemoved() = runBlocking {
        val removed = Campsite(id = "banff", name = "Banff Creek", removed = true)
        val multiLottery = lottery.copy(
            campsiteIds = listOf("removed-id", "jasper", "banff", "hinton", "jasper"),
        )
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper, removed))
        val lotteries = FakeLotteryRepository(listOf(multiLottery), listOf(entry(EntryStatus.WON)))
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites),
        )

        controller.fetchData(duringBooking)

        assertEquals(listOf("jasper", "hinton"), controller.availableCampsites.map { it.id })
        assertTrue(controller.isMultiSite)
        assertNull(controller.campsite)

        // When all covered campsites are missing or removed, no sites are available
        val emptyCampsites = FakeCampsiteRepository(listOf(removed))
        val emptyController = ReserveController(
            campsiteRepository = emptyCampsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(emptyCampsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            lotteryDrawer = drawerFor(lotteries, emptyCampsites),
        )
        emptyController.fetchData(duringBooking)
        assertTrue(emptyController.availableCampsites.isEmpty())
        assertNull(emptyController.campsite)
    }

    @Test
    fun fetchData_setsErrorWhenReadingCampsiteFails() = runBlocking {
        val failingCampsites = object : CampsiteRepository by FakeCampsiteRepository(listOf(hinton)) {
            override suspend fun getCampsite(id: String): Campsite? =
                throw IllegalStateException("offline")
        }
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val controller = ReserveController(
            campsiteRepository = failingCampsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(failingCampsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, failingCampsites),
        )

        controller.fetchData(duringBooking)

        assertEquals("Unable to load your win.", controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun selectCampsite_switchesDisjointCalendarsRulesAndPrice_andClearsPickedNightsAndError() = runBlocking {
        val jasperWithRules = jasper.copy(
            maxOccupancy = 6,
            pricePerNightCents = 6500,
            rules = "No campfires.",
            cancellationPolicy = "No refunds within 48 hours.",
        )
        val jasperCalendar = AvailabilityCalendar(
            campsiteId = "jasper",
            openNights = (24..27).map { "2027-05-$it" },
        )
        val campsites = FakeCampsiteRepository(listOf(hinton, jasperWithRules))
        campsites.saveAvailability(hintonCalendar.copy(openNights = (17..23).map { "2027-05-$it" }))
        campsites.saveAvailability(jasperCalendar)
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites),
        )

        controller.fetchData(duringBooking)
        assertNull(controller.campsite)
        assertNull(controller.nightsTimesPriceText)
        assertNull(controller.totalText)
        assertFalse(controller.noNightsLeft)

        // Selecting Hinton shows Hinton's free nights, pricing, and rules
        controller.selectCampsite("hinton", duringBooking)
        assertEquals("hinton", controller.campsite?.id)
        assertEquals("Quiet hours 10 pm to 7 am.", controller.campsite?.rules)
        assertEquals(listOf("2027-05-17", "2027-05-18", "2027-05-19", "2027-05-22", "2027-05-23"), controller.freeNights)
        controller.onNightTap("2027-05-18")
        controller.onNightTap("2027-05-19")
        assertEquals("2 nights × $50", controller.nightsTimesPriceText)
        assertEquals("Total $100", controller.totalText)

        // Trigger a campsite-specific error while nights are picked, then switch sites
        controller.onNightTap("2027-05-19")
        controller.onNightTap("2027-05-18")
        controller.reserveNights(duringBooking)
        assertEquals("Please pick at least one night.", controller.reserveError)
        controller.onNightTap("2027-05-18")

        // Switching to Jasper clears picked nights and reserveError, and loads Jasper's calendar/price/rules
        controller.selectCampsite("jasper", duringBooking)
        assertEquals("jasper", controller.campsite?.id)
        assertEquals("No campfires.", controller.campsite?.rules)
        assertTrue(controller.selectedNights.isEmpty())
        assertNull(controller.reserveError)
        assertEquals((24..27).map { "2027-05-$it" }, controller.freeNights)
        assertEquals("0 nights × $65", controller.nightsTimesPriceText)
        assertEquals("Total $0", controller.totalText)

        // Tapping the already-selected site does not clear picked nights
        controller.onNightTap("2027-05-25")
        controller.selectCampsite("jasper", duringBooking)
        assertEquals(listOf("2027-05-25"), controller.selectedNights)
    }

    @Test
    fun selectCampsite_preservesGroupSize_andBlocksOversizedGroupWithClearFeedback() = runBlocking {
        val smallJasper = jasper.copy(maxOccupancy = 2)
        val campsites = FakeCampsiteRepository(listOf(hinton, smallJasper))
        campsites.saveAvailability(hintonCalendar)
        campsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "jasper", openNights = listOf("2027-05-24", "2027-05-25")),
        )
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val reservations = FakeReservationRepository(campsites, lotteries)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            lotteryDrawer = drawerFor(lotteries, campsites),
        )

        controller.fetchData(duringBooking)
        controller.incrementGroupSize()
        assertEquals(5, controller.groupSize)

        // Switching to smaller Jasper preserves groupSize = 5
        controller.selectCampsite("jasper", duringBooking)
        assertEquals(5, controller.groupSize)
        controller.onNightTap("2027-05-24")

        val blocked = controller.reserveNights(duringBooking)
        assertEquals(ReserveResult.CapacityExceeded, blocked)
        assertEquals("Group size cannot exceed the maximum occupancy of 2.", controller.reserveError)
        assertNull(controller.confirmedReservation)
        assertTrue(reservations.getReservationsForUser("user-sam").isEmpty())

        // Reducing guest count down to maxOccupancy = 2 allows reserving
        repeat(3) { controller.decrementGroupSize() }
        assertEquals(2, controller.groupSize)
        val succeeded = controller.reserveNights(duringBooking)
        assertTrue(succeeded is ReserveResult.Success)
        val res = (succeeded as ReserveResult.Success).reservation
        assertEquals("jasper", res.campsiteId)
        assertEquals(2, res.groupSize)
    }

    @Test
    fun selectCampsite_ignoresStaleCalendarResponse_andKeepsSelectorUsableOnFailure() = runBlocking {
        val slowHinton = CompletableDeferred<AvailabilityCalendar>()
        var failJasper = false
        val baseCampsites = FakeCampsiteRepository(listOf(hinton, jasper))
        baseCampsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "jasper", openNights = listOf("2027-05-25", "2027-05-26")),
        )
        val racingCampsites = object : CampsiteRepository by baseCampsites {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                when (campsiteId) {
                    "hinton" -> slowHinton.await()
                    "jasper" -> {
                        if (failJasper) throw IllegalStateException("network down")
                        baseCampsites.getAvailability("jasper")
                    }
                    else -> baseCampsites.getAvailability(campsiteId)
                }
        }
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val controller = ReserveController(
            campsiteRepository = racingCampsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(racingCampsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, racingCampsites),
        )
        controller.fetchData(duringBooking)

        // Start loading Hinton (suspends), then switch to Jasper before Hinton finishes
        val hintonSwitch = async { controller.selectCampsite("hinton", duringBooking) }
        yield()
        assertTrue(controller.loadingAvailability)

        controller.selectCampsite("jasper", duringBooking)
        assertEquals("jasper", controller.campsite?.id)
        assertFalse(controller.loadingAvailability)
        assertEquals(listOf("2027-05-25", "2027-05-26"), controller.freeNights)
        controller.onNightTap("2027-05-25")

        // Stale Hinton response arrives late: must not overwrite Jasper's state
        slowHinton.complete(hintonCalendar)
        hintonSwitch.await()
        assertEquals("jasper", controller.campsite?.id)
        assertEquals(listOf("2027-05-25", "2027-05-26"), controller.freeNights)
        assertEquals(listOf("2027-05-25"), controller.selectedNights)
        assertNull(controller.availabilityError)
        assertFalse(controller.loadingAvailability)

        // Switching to a site whose availability fails isolates the error and recovers on retry
        controller.selectCampsite("hinton", duringBooking)
        assertEquals("hinton", controller.campsite?.id)
        failJasper = true
        controller.selectCampsite("jasper", duringBooking)
        assertEquals(
            "Unable to load campsite availability. Please try again.",
            controller.availabilityError,
        )
        assertNull(controller.error)
        assertFalse(controller.noNightsLeft)
        assertEquals(2, controller.availableCampsites.size)

        failJasper = false
        controller.selectCampsite("jasper", duringBooking)
        assertNull(controller.availabilityError)
        assertEquals(listOf("2027-05-25", "2027-05-26"), controller.freeNights)
    }

    @Test
    fun reserveInFlight_blocksCampsiteSwitchingAndDecline() = runBlocking {
        val gate = CompletableDeferred<ReserveResult>()
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        campsites.saveAvailability(hintonCalendar)
        campsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "jasper", openNights = listOf("2027-05-25")),
        )
        val lotteries = lotteriesWith(EntryStatus.WON)
        val baseReservations = FakeReservationRepository(campsites, lotteries)
        val gatedReservations = object : com.example.vipers.domain.repository.ReservationRepository by baseReservations {
            override suspend fun createReservation(
                lotteryId: String,
                campsiteId: String,
                userId: String,
                nights: List<String>,
                groupSize: Int,
                now: Long,
            ): ReserveResult {
                gate.await()
                return baseReservations.createReservation(
                    lotteryId,
                    campsiteId,
                    userId,
                    nights,
                    groupSize,
                    now,
                )
            }
        }
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = gatedReservations,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            lotteryDrawer = drawerFor(lotteries, campsites),
        )
        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        val inFlight = async { controller.reserveNights(duringBooking) }
        yield()
        assertTrue(controller.isReserving)

        // Switching and declining must be ignored while reserving
        controller.selectCampsite("jasper", duringBooking)
        controller.decline(duringBooking)
        assertEquals("hinton", controller.campsite?.id)
        assertEquals(0, lotteries.declineCalls)

        gate.complete(ReserveResult.NotEligible)
        val result = inFlight.await()
        assertTrue(result is ReserveResult.Success)
        assertFalse(controller.isReserving)
        assertEquals("hinton", controller.confirmedReservation?.campsiteId)
    }

    @Test
    fun decline_multiSiteBeforeSelectingCampsite_declinesAndPromotesReplacement() = runBlocking {
        val notifications = FakeNotificationRepository()
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        val lotteries = lotteriesWith(
            EntryStatus.WON,
            extraEntries = listOf(entry(EntryStatus.LOST, userId = "user-jo")),
            notifications = notifications,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites, notifications),
        )
        controller.fetchData(duringBooking)
        assertNull(controller.campsite)
        assertTrue(controller.canDecline)

        controller.decline(duringBooking)

        assertEquals(EntryStatus.DECLINED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(OfferState.USED, controller.offerState)
        assertFalse(controller.canDecline)
        assertEquals(EntryStatus.WON, lotteries.getEntry("lot-may", "user-jo")?.status)
        val notices = notifications.getNotificationsFor("user-jo")
        assertEquals(1, notices.size)
        assertEquals("replacement_lot-may_user-jo", notices[0].id)
    }

    @Test
    fun reserveNights_conflictRefreshPreservesChosenSite_andOneWinCannotReserveTwoCampsites() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper.copy(maxOccupancy = 6)))
        campsites.saveAvailability(hintonCalendar)
        campsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "jasper", openNights = listOf("2027-05-25", "2027-05-26")),
        )
        val lotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val reservations = FakeReservationRepository(campsites, lotteries)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = reservations,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites),
        )

        controller.fetchData(duringBooking)
        controller.selectCampsite("jasper", duringBooking)
        controller.onNightTap("2027-05-25")

        // Another winner takes May 25 at Jasper first
        val cal = campsites.getAvailability("jasper")
        campsites.saveAvailability(cal.copy(heldNights = cal.heldNights + "2027-05-25"))

        val conflict = controller.reserveNights(duringBooking)
        assertEquals(ReserveResult.Conflict, conflict)
        assertEquals("jasper", controller.campsite?.id)
        assertEquals(listOf("2027-05-26"), controller.freeNights)
        assertTrue(controller.selectedNights.isEmpty())

        // Now book May 26 at Jasper
        controller.onNightTap("2027-05-26")
        val success = controller.reserveNights(duringBooking)
        assertTrue(success is ReserveResult.Success)
        assertEquals("jasper", (success as ReserveResult.Success).reservation.campsiteId)

        // Same win cannot produce a second reservation at Hinton
        val secondAttempt = reservations.createReservation(
            lotteryId = "lot-may",
            campsiteId = "hinton",
            userId = "user-sam",
            nights = listOf("2027-05-18"),
            groupSize = 2,
            now = duringBooking,
        )
        assertEquals(ReserveResult.NotEligible, secondAttempt)
        assertEquals(1, reservations.getReservationsForUser("user-sam").size)
    }

    @Test
    fun decline_withZeroActiveCampsitesAndOpenWin_keepsLotteryAndDeclinesSuccessfully() = runBlocking {
        val removedHinton = hinton.copy(removed = true)
        val removedJasper = jasper.copy(removed = true)
        val campsites = FakeCampsiteRepository(listOf(removedHinton, removedJasper))
        val notifications = FakeNotificationRepository()
        val lotteries = lotteriesWith(
            EntryStatus.WON,
            extraEntries = listOf(entry(EntryStatus.LOST, userId = "user-jo")),
            notifications = notifications,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = null,
            lotteryDrawer = drawerFor(lotteries, campsites, notifications),
        )

        controller.fetchData(duringBooking)
        assertNotNull(controller.lottery)
        assertTrue(controller.availableCampsites.isEmpty())
        assertNull(controller.campsite)
        assertEquals(OfferState.OPEN, controller.offerState)
        assertTrue(controller.canDecline)

        controller.decline(duringBooking)
        assertEquals(EntryStatus.DECLINED, lotteries.getEntry("lot-may", "user-sam")?.status)
        assertEquals(OfferState.USED, controller.offerState)
        assertFalse(controller.canDecline)
        assertEquals(EntryStatus.WON, lotteries.getEntry("lot-may", "user-jo")?.status)
    }

    @Test
    fun decline_whilePostDeclineRefreshSuspended_blocksSwitchReserveAndRepeatDecline() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshGate = CompletableDeferred<Lottery?>()
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper.copy(maxOccupancy = 6)))
        campsites.saveAvailability(hintonCalendar)
        campsites.saveAvailability(
            AvailabilityCalendar(campsiteId = "jasper", openNights = listOf("2027-05-25")),
        )
        val baseLotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        val countingLotteries = CountingLotteryRepository(baseLotteries)
        val gatedLotteries = object : LotteryRepository by countingLotteries {
            override suspend fun getLottery(id: String): Lottery? {
                if (countingLotteries.declineCalls >= 1) {
                    refreshStarted.complete(Unit)
                    return refreshGate.await()
                }
                return countingLotteries.getLottery(id)
            }
        }
        val reservations = FakeReservationRepository(campsites, gatedLotteries)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = gatedLotteries,
            reservationRepository = reservations,
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            lotteryDrawer = drawerFor(gatedLotteries, campsites),
        )

        controller.fetchData(duringBooking)
        controller.onNightTap("2027-05-22")

        val inFlightDecline = async { controller.decline(duringBooking) }
        refreshStarted.await()

        assertTrue(controller.isDeclining)
        assertEquals(1, countingLotteries.declineCalls)

        // Switching, Reserve, and repeat Decline must all be rejected while post-decline refresh is suspended
        controller.selectCampsite("jasper", duringBooking)
        assertEquals("hinton", controller.campsite?.id)

        val reserveAttempt = controller.reserveNights(duringBooking)
        assertEquals(ReserveResult.Error("A request is already in progress."), reserveAttempt)
        assertTrue(reservations.getReservationsForUser("user-sam").isEmpty())

        controller.decline(duringBooking)
        assertEquals(1, countingLotteries.declineCalls)

        // Unblock refresh and verify refreshed offer state is applied
        refreshGate.complete(lottery)
        inFlightDecline.await()

        assertFalse(controller.isDeclining)
        assertEquals(OfferState.USED, controller.offerState)
        assertEquals(EntryStatus.DECLINED, controller.entry?.status)
        assertFalse(controller.canDecline)
    }

    @Test
    fun decline_whenNotDeclinedAndRefreshFails_surfacesRefreshFailureWithoutInventingDeclinedStatus() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        campsites.saveAvailability(hintonCalendar)
        val baseLotteries = FakeLotteryRepository(listOf(lottery), listOf(entry(EntryStatus.WON)))
        var failRefresh = false
        val lotteries = object : LotteryRepository by baseLotteries {
            override suspend fun declineTransaction(
                lotteryId: String,
                userId: String,
                candidateUserId: String?,
                replacementNotice: Notification?,
                now: Long,
            ): DeclineTransactionResult {
                failRefresh = true
                return DeclineTransactionResult.NotDeclined
            }

            override suspend fun getLottery(id: String): Lottery? {
                if (failRefresh) throw IllegalStateException("refresh failed")
                return baseLotteries.getLottery(id)
            }
        }
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            userId = "user-sam",
            lotteryId = "lot-may",
            campsiteId = "hinton",
            lotteryDrawer = drawerFor(lotteries, campsites),
        )

        controller.fetchData(duringBooking)
        controller.decline(duringBooking)

        assertFalse(controller.isDeclining)
        assertNull(controller.declineError)
        assertEquals("Unable to load your win.", controller.error)
        assertEquals(EntryStatus.WON, controller.entry?.status)
        assertEquals(OfferState.OPEN, controller.offerState)
    }
}
