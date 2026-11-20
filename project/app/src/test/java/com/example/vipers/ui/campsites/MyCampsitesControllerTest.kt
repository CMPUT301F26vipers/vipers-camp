/*
 * MyCampsitesControllerTest.kt
 * Unit tests for MyCampsitesController (US 02.03.01, Mockup O01): loads owner's campsites
 * and calculates real lottery status labels for each campsite.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyCampsitesControllerTest {

    private val myUserId = "user12"
    private val campsite1 = Campsite(id = "c1", ownerId = myUserId, name = "Campsite 1")
    private val campsite2 = Campsite(id = "c2", ownerId = myUserId, name = "Campsite 2")
    private val otherCampsite = Campsite(id = "c3", ownerId = "other", name = "Other Camp")

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun fetchCampsites_withNoCampsites_returnsEmptyList() = runBlocking {
        val fakeRepo = FakeCampsiteRepository(initial = listOf(otherCampsite))
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        viewModel.fetchCampsites(now = fixedNow)

        assertEquals(0, viewModel.campsites.size)
        assertFalse(viewModel.loading)
        assertNull(viewModel.error)
    }

    @Test
    fun fetchCampsites_withCampsites_returnsPopulatedList() = runBlocking {
        val fakeRepo = FakeCampsiteRepository(initial = listOf(campsite1, campsite2))
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        viewModel.fetchCampsites(now = fixedNow)

        assertEquals(2, viewModel.campsites.size)
        assertEquals("Campsite 1", viewModel.campsites[0].name)
        assertFalse(viewModel.loading)
        assertNull(viewModel.error)
    }

    @Test
    fun fetchCampsites_withCampsitesButNothingUnderUser_returnsEmptyList() = runBlocking {
        val fakeRepo = FakeCampsiteRepository(initial = listOf(otherCampsite))
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        viewModel.fetchCampsites(now = fixedNow)

        assertEquals(0, viewModel.campsites.size)
    }

    @Test
    fun fetchCampsites_withNoLotteries_statusIsNone() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val lotteryRepo = FakeLotteryRepository()
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals(1, controller.campsites.size)
        assertEquals("None", controller.lotteryStatuses["c1"])
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchCampsites_withOpenLottery_statusIsOpen() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val lottery = Lottery(
            id = "lot1",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 1000L,
            registrationEnd = fixedNow + 1000L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Open", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_withUpcomingLottery_statusIsUpcoming() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val lottery = Lottery(
            id = "lot1",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow + 1000L,
            registrationEnd = fixedNow + 5000L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Upcoming", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_withDrawnLottery_statusIsBookingPeriod() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val lottery = Lottery(
            id = "lot1",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 5000L,
            registrationEnd = fixedNow - 1000L,
            bookingStart = fixedNow - 500L,
            bookingEnd = fixedNow + 5000L,
            drawnAt = fixedNow - 500L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Booking period", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_withClosedLottery_statusIsClosed() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val lottery = Lottery(
            id = "lot1",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 5000L,
            registrationEnd = fixedNow - 2000L,
            bookingStart = fixedNow - 2000L,
            bookingEnd = fixedNow - 500L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(lottery))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Closed", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_multipleLotteries_prioritizesOpenStatus() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val closed = Lottery(
            id = "lot-closed",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 5000L,
            registrationEnd = fixedNow - 2000L,
            bookingEnd = fixedNow - 500L,
        )
        val upcoming = Lottery(
            id = "lot-upcoming",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow + 1000L,
            registrationEnd = fixedNow + 5000L,
        )
        val open = Lottery(
            id = "lot-open",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 1000L,
            registrationEnd = fixedNow + 1000L,
        )
        val lotteryRepo = FakeLotteryRepository(
            initialLotteries = listOf(closed, upcoming, open),
        )
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Open", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_multipleLotteries_prioritizesBookingPeriodOverUpcoming() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val drawn = Lottery(
            id = "lot-drawn",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 5000L,
            registrationEnd = fixedNow - 1000L,
            bookingStart = fixedNow - 500L,
            bookingEnd = fixedNow + 5000L,
            drawnAt = fixedNow - 500L,
        )
        val upcoming = Lottery(
            id = "lot-upcoming",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow + 1000L,
            registrationEnd = fixedNow + 5000L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(drawn, upcoming))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Booking period", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_multipleLotteries_prioritizesUpcomingOverClosed() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val closed = Lottery(
            id = "lot-closed",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 5000L,
            registrationEnd = fixedNow - 2000L,
            bookingEnd = fixedNow - 500L,
        )
        val upcoming = Lottery(
            id = "lot-upcoming",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow + 1000L,
            registrationEnd = fixedNow + 5000L,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(closed, upcoming))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("Upcoming", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_ignoresRemovedLotteries() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val removedOpen = Lottery(
            id = "lot-removed",
            campsiteIds = listOf("c1"),
            registrationStart = fixedNow - 1000L,
            registrationEnd = fixedNow + 1000L,
            removed = true,
        )
        val lotteryRepo = FakeLotteryRepository(initialLotteries = listOf(removedOpen))
        val controller = MyCampsitesController(campsiteRepo, lotteryRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals("None", controller.lotteryStatuses["c1"])
    }

    @Test
    fun fetchCampsites_nullLotteryRepository_leavesStatusesEmpty() = runBlocking {
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1))
        val controller = MyCampsitesController(
            campsiteRepository = campsiteRepo,
            ownerId = myUserId,
            lotteryRepository = null,
        )

        controller.fetchCampsites(now = fixedNow)

        assertEquals(1, controller.campsites.size)
        assertTrue(controller.lotteryStatuses.isEmpty())
    }

    @Test
    fun fetchCampsites_repositoryThrows_setsErrorAndStopsLoading() = runBlocking {
        val failingRepo = object : CampsiteRepository {
            override suspend fun addCampsite(campsite: Campsite): String = ""
            override suspend fun getCampsite(id: String): Campsite? = null
            override suspend fun updateCampsite(campsite: Campsite) {}
            override suspend fun getCampsitesByOwner(ownerId: String): List<Campsite> {
                throw RuntimeException("Network disconnected")
            }
            override suspend fun getAllCampsites(): List<Campsite> = emptyList()
            override suspend fun getAvailability(campsiteId: String) = AvailabilityCalendar()
            override suspend fun saveAvailability(calendar: AvailabilityCalendar) {}
        }
        val controller = MyCampsitesController(failingRepo, myUserId)

        controller.fetchCampsites(now = fixedNow)

        assertEquals(
            "Couldn't load campsites. Check your connection and try again.",
            controller.error,
        )
        assertFalse(controller.loading)
    }

    @Test
    fun companion_formatStatus_mapsAllStatuses() {
        assertEquals("Open", MyCampsitesController.formatStatus(LotteryStatus.OPEN))
        assertEquals("Upcoming", MyCampsitesController.formatStatus(LotteryStatus.UPCOMING))
        assertEquals("Booking period", MyCampsitesController.formatStatus(LotteryStatus.DRAWN))
        assertEquals("Closed", MyCampsitesController.formatStatus(LotteryStatus.CLOSED))
    }

    @Test
    fun companion_calculateLotteryStatus_emptyListReturnsNone() {
        assertEquals("None", MyCampsitesController.calculateLotteryStatus(emptyList()))
    }
}
