/*
 * CampsiteDetailsControllerTest.kt
 * Unit tests for CampsiteDetailsController (US 01.01.05): loading a campsite for an entrant and
 * finding the lottery they can join and the lottery they won (US 01.04.01), using the fake
 * campsite and lottery repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CampsiteDetailsControllerTest {

    /** A date and time in Edmonton, in milliseconds. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.of("America/Edmonton")).toInstant().toEpochMilli()

    // the tests run as if it is noon on April 28, 2027
    private val now = at("2027-04-28T12:00")

    // the Hinton campsite from scenario 2 in the project description
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse and an on-site tennis court.",
        maxOccupancy = 8,
        tags = listOf("Cookhouse", "Tennis court"),
        pricePerNightCents = 6500,
    )
    private val removed =
        Campsite(id = "old", name = "Hinton Old Mill", address = "Hinton, AB", removed = true)

    /** A lottery for [campsiteId] whose registration runs from [start] to [end]. */
    private fun lottery(
        id: String,
        start: String,
        end: String,
        campsiteId: String = "hinton",
    ) = Lottery(
        id = id,
        campsiteIds = listOf(campsiteId),
        nightsDescription = "May 2027 weekends",
        registrationStart = at(start),
        registrationEnd = at(end),
        bookingStart = at("2027-05-12T00:00"),
        bookingEnd = at("2027-05-31T23:59"),
    )

    private fun loadedController(
        campsiteId: String = "hinton",
        lotteries: List<Lottery> = emptyList(),
        entries: List<LotteryEntry> = emptyList(),
        userId: String = "",
        at: Long = now,
    ): CampsiteDetailsController = runBlocking {
        val controller = CampsiteDetailsController(
            FakeCampsiteRepository(listOf(hinton, removed)),
            FakeLotteryRepository(lotteries, entries),
            campsiteId,
            userId,
        )
        controller.fetchData(at)
        controller
    }

    /** Sam's entry in [lotteryId] with [status]. */
    private fun samsEntry(lotteryId: String, status: EntryStatus) = LotteryEntry(
        id = LotteryEntry.idFor(lotteryId, "user-sam"),
        lotteryId = lotteryId,
        userId = "user-sam",
        groupSize = 4,
        status = status,
    )

    @Test
    fun fetchData_loadsTheCampsite() {
        val controller = loadedController()
        assertEquals(hinton, controller.campsite)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_findsTheLotteryWithOpenRegistration() {
        val open = lottery("open", "2027-04-20T09:00", "2027-05-01T15:00")
        val upcoming = lottery("upcoming", "2027-05-05T09:00", "2027-05-10T15:00")
        val controller = loadedController(lotteries = listOf(upcoming, open))
        assertEquals("open", controller.activeLottery?.id)
    }

    @Test
    fun fetchData_picksTheOneClosingFirstWhenSeveralAreOpen() {
        val later = lottery("later", "2027-04-20T09:00", "2027-05-08T15:00")
        val sooner = lottery("sooner", "2027-04-25T09:00", "2027-05-01T15:00")
        val controller = loadedController(lotteries = listOf(later, sooner))
        assertEquals("sooner", controller.activeLottery?.id)
    }

    @Test
    fun fetchData_hasNoActiveLotteryWhenNoneIsOpen() {
        val upcoming = lottery("upcoming", "2027-05-05T09:00", "2027-05-10T15:00")
        val closed = lottery("closed", "2027-04-01T09:00", "2027-04-10T15:00")
        val controller = loadedController(lotteries = listOf(upcoming, closed))
        assertNull(controller.activeLottery)
    }

    @Test
    fun fetchData_ignoresOtherCampsitesLotteries() {
        val elsewhere = lottery("banff", "2027-04-20T09:00", "2027-05-01T15:00", campsiteId = "banff")
        val controller = loadedController(lotteries = listOf(elsewhere))
        assertNull(controller.activeLottery)
    }

    @Test
    fun fetchData_treatsARemovedCampsiteAsNotFound() {
        val controller = loadedController(campsiteId = "old")
        assertNull(controller.campsite)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_leavesCampsiteEmptyWhenNotFound() {
        val controller = loadedController(campsiteId = "missing")
        assertNull(controller.campsite)
        assertNull(controller.activeLottery)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_setsErrorWhenTheRepositoryFails() = runBlocking {
        // a repository that always fails, like Firestore when the phone is offline
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = throw Exception("offline")
        }
        val controller = CampsiteDetailsController(failing, FakeLotteryRepository(), "hinton")
        controller.fetchData(now)
        assertEquals("Unable to load details.", controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun fetchData_doesNotSetErrorWhenCancelled() = runBlocking {
        // a repository that never answers, so the load is still running when it's cancelled
        val slow = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = awaitCancellation()
        }
        val controller = CampsiteDetailsController(slow, FakeLotteryRepository(), "hinton")
        val load = launch { controller.fetchData(now) }
        yield() // let the load start
        load.cancelAndJoin()
        assertNull(controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun spotsLeft_withCapacityAndWaitingEntrants_calculatesRemainingSpots() {
        val open = lottery("open", "2027-04-20T09:00", "2027-05-01T15:00").copy(
            waitlistCapacity = 10,
            waitingUserIds = listOf("user-1", "user-2", "user-3"),
        )
        val controller = loadedController(lotteries = listOf(open))
        assertEquals(7, controller.spotsLeft)
    }

    @Test
    fun spotsLeft_whenCapacityIsUnlimited_returnsNull() {
        val open = lottery("open", "2027-04-20T09:00", "2027-05-01T15:00").copy(
            waitlistCapacity = null,
            waitingUserIds = listOf("user-1", "user-2"),
        )
        val controller = loadedController(lotteries = listOf(open))
        assertNull(controller.spotsLeft)
    }

    @Test
    fun spotsLeft_whenWaitingListAtOrOverCapacity_returnsZero() {
        val full = lottery("open", "2027-04-20T09:00", "2027-05-01T15:00").copy(
            waitlistCapacity = 5,
            waitingUserIds = listOf("u1", "u2", "u3", "u4", "u5"),
        )
        val controller = loadedController(lotteries = listOf(full))
        assertEquals(0, controller.spotsLeft)

        val overCapacity = full.copy(
            waitingUserIds = listOf("u1", "u2", "u3", "u4", "u5", "u6"),
        )
        val overController = loadedController(lotteries = listOf(overCapacity))
        assertEquals(0, overController.spotsLeft)
    }

    // a lottery whose registration closed in April and whose booking runs May 12 to May 31
    private val drawn = lottery("drawn", "2027-04-01T09:00", "2027-04-10T15:00")
        .copy(drawnAt = at("2027-04-10T15:15"))

    @Test
    fun fetchData_findsTheLotteryTheUserWonHere() {
        val controller = loadedController(
            lotteries = listOf(drawn),
            entries = listOf(samsEntry("drawn", EntryStatus.WON)),
            userId = "user-sam",
        )
        assertEquals("drawn", controller.wonLottery?.id)
    }

    @Test
    fun fetchData_hasNoWonLotteryWhenTheUserLostOrAlreadyReserved() {
        for (status in listOf(EntryStatus.LOST, EntryStatus.RESERVED, EntryStatus.DECLINED)) {
            val controller = loadedController(
                lotteries = listOf(drawn),
                entries = listOf(samsEntry("drawn", status)),
                userId = "user-sam",
            )
            assertNull(controller.wonLottery)
        }
    }

    @Test
    fun fetchData_hasNoWonLotteryAfterTheBookingPeriod() {
        val controller = loadedController(
            lotteries = listOf(drawn),
            entries = listOf(samsEntry("drawn", EntryStatus.WON)),
            userId = "user-sam",
            at = at("2027-06-01T08:00"),
        )
        assertNull(controller.wonLottery)
    }

    @Test
    fun fetchData_onlyShowsTheViewersOwnWin() {
        val controller = loadedController(
            lotteries = listOf(drawn),
            entries = listOf(samsEntry("drawn", EntryStatus.WON)),
            userId = "user-alex",
        )
        assertNull(controller.wonLottery)
    }
}
