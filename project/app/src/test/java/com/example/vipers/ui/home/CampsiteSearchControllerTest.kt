/*
 * CampsiteSearchControllerTest.kt
 * Unit tests for CampsiteSearchController: searching by name (US 01.01.01), filtering by region,
 * date range, and guests (US 01.01.02), result count formatting (US 01.01.03), and marking which campsites have an open lottery (US 01.01.04),
 * using the fake campsite and lottery repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CampsiteSearchControllerTest {

    private val campsites = listOf(
        Campsite(id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 6),
        Campsite(id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 4),
        Campsite(id = "3", name = "Bow Valley Campground", address = "Banff, AB", region = Region.BANFF, maxOccupancy = 2),
        Campsite(id = "4", name = "Hinton Old Mill", address = "Hinton, AB", region = Region.HINTON, removed = true),
    )

    /** A date and time in Edmonton, in milliseconds. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.of("America/Edmonton")).toInstant().toEpochMilli()

    // the tests run as if it is noon on April 28, 2027
    private val now = at("2027-04-28T12:00")

    private val lotteries = listOf(
        // Hinton Ridge: registration open until May 1
        Lottery(
            id = "open", campsiteIds = listOf("1"),
            registrationStart = at("2027-04-20T09:00"), registrationEnd = at("2027-05-01T15:00"),
            bookingStart = at("2027-05-03T00:00"), bookingEnd = at("2027-05-31T23:59"),
        ),
        // Hinton Lakeside: registration only starts on May 5
        Lottery(
            id = "upcoming", campsiteIds = listOf("2"),
            registrationStart = at("2027-05-05T09:00"), registrationEnd = at("2027-05-10T15:00"),
            bookingStart = at("2027-05-12T00:00"), bookingEnd = at("2027-05-31T23:59"),
        ),
        // Bow Valley: already drawn, winners are booking now
        Lottery(
            id = "drawn", campsiteIds = listOf("3"),
            registrationStart = at("2027-04-01T09:00"), registrationEnd = at("2027-04-10T15:00"),
            bookingStart = at("2027-04-12T00:00"), bookingEnd = at("2027-05-31T23:59"),
            drawnAt = at("2027-04-10T15:15"),
        ),
    )

    private fun loadedController(
        lotteryRepository: LotteryRepository = FakeLotteryRepository(lotteries),
        campsiteRepository: CampsiteRepository? = null,
        calendars: List<AvailabilityCalendar> = emptyList(),
    ): CampsiteSearchController = runBlocking {
        val repo = campsiteRepository ?: FakeCampsiteRepository(campsites).also { fake ->
            calendars.forEach { fake.saveAvailability(it) }
        }
        val controller = CampsiteSearchController(repo, lotteryRepository)
        controller.fetchCampsites(now)
        controller
    }

    private fun campsite(id: String): Campsite = campsites.first { it.id == id }

    @Test
    fun results_emptySearchShowsEveryCampsiteSortedByName() {
        val controller = loadedController()
        assertEquals(
            listOf("Bow Valley Campground", "Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun results_showOnlyNamesStartingWithTheText() {
        val controller = loadedController()
        controller.onSearchTextChange("hin")
        assertEquals(
            listOf("Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun results_updateAsTheUserKeepsTyping() {
        val controller = loadedController()
        controller.onSearchTextChange("Hinton")
        assertEquals(2, controller.results.size)
        controller.onSearchTextChange("Hinton R")
        assertEquals(listOf("Hinton Ridge Campground"), controller.results.map { it.name })
    }

    @Test
    fun results_areEmptyWhenNothingMatches() {
        val controller = loadedController()
        controller.onSearchTextChange("lake louise")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun results_neverIncludeRemovedCampsites() {
        val controller = loadedController()
        controller.onSearchTextChange("Hinton Old")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun filter_byRegionShowsOnlyCampsitesInThatRegion() {
        val controller = loadedController()
        controller.applyFilters(region = Region.BANFF)
        assertEquals(listOf("Bow Valley Campground"), controller.results.map { it.name })

        controller.applyFilters(region = Region.HINTON)
        assertEquals(
            listOf("Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun filter_byMinGuestsHidesCampsitesBelowOccupancy() {
        val controller = loadedController()
        controller.applyFilters(minGuests = 5)
        assertEquals(listOf("Hinton Ridge Campground"), controller.results.map { it.name })

        controller.applyFilters(minGuests = 3)
        assertEquals(
            listOf("Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )

        controller.applyFilters(minGuests = 10)
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun filter_byDateRangeShowsOnlyCampsitesWithAvailableNight() {
        val calendars = listOf(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01", "2027-06-02")),
            AvailabilityCalendar(campsiteId = "2", openNights = listOf("2027-06-05"), heldNights = listOf("2027-06-05")),
            AvailabilityCalendar(campsiteId = "3", openNights = listOf("2027-06-10", "2027-06-11")),
        )
        val controller = loadedController(calendars = calendars)

        // Range covering only campsite 1
        controller.applyFilters(startDate = "2027-06-01", endDate = "2027-06-03")
        assertEquals(listOf("Hinton Ridge Campground"), controller.results.map { it.name })

        // Range where campsite 2 has an open night, but it is held by confirmed reservation
        controller.applyFilters(startDate = "2027-06-04", endDate = "2027-06-06")
        assertTrue(controller.results.isEmpty())

        // Range covering both campsite 1 and campsite 3
        controller.applyFilters(startDate = "2027-06-01", endDate = "2027-06-15")
        assertEquals(
            listOf("Bow Valley Campground", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun filter_byDateRangeHandlesInvalidDateGracefully() {
        val calendars = listOf(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01", "2027-06-02")),
        )
        val controller = loadedController(calendars = calendars)

        // Invalid dates (e.g. "May 8" or "2027-13-45") do not crash results
        controller.applyFilters(startDate = "May 8", endDate = "2027-13-45")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun filter_byDateRangeCapsRangeAtOneYear() {
        val calendars = listOf(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01")),
        )
        val controller = loadedController(calendars = calendars)

        // Extremely wide range (e.g. 2027 to 2099) is safely rejected to avoid long loops
        controller.applyFilters(startDate = "2027-01-01", endDate = "2099-01-01")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun filter_combinesRegionGuestsAndDateRange() {
        val calendars = listOf(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01", "2027-06-02")),
            AvailabilityCalendar(campsiteId = "2", openNights = listOf("2027-06-01", "2027-06-02")),
            AvailabilityCalendar(campsiteId = "3", openNights = listOf("2027-06-01", "2027-06-02")),
        )
        val controller = loadedController(calendars = calendars)

        // Hinton campsites with at least 5 guests and available on June 1
        controller.applyFilters(
            region = Region.HINTON,
            startDate = "2027-06-01",
            endDate = "2027-06-02",
            minGuests = 5,
        )
        assertEquals(listOf("Hinton Ridge Campground"), controller.results.map { it.name })
    }

    @Test
    fun filter_combinesFiltersWithSearchText() {
        val calendars = listOf(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01")),
            AvailabilityCalendar(campsiteId = "2", openNights = listOf("2027-06-01")),
        )
        val controller = loadedController(calendars = calendars)

        controller.applyFilters(region = Region.HINTON, minGuests = 2)
        assertEquals(2, controller.results.size)

        // Typing search text narrows the filtered results
        controller.onSearchTextChange("Hinton L")
        assertEquals(listOf("Hinton Lakeside Sites"), controller.results.map { it.name })
    }

    @Test
    fun clearFilters_restoresFullListPreservingSearchText() {
        val controller = loadedController()
        controller.applyFilters(region = Region.BANFF, minGuests = 2)
        assertEquals(1, controller.results.size)

        controller.onSearchTextChange("Bow")
        controller.clearFilters()

        assertFalse(controller.hasActiveFilters)
        assertEquals("Bow", controller.searchText)
        assertEquals(listOf("Bow Valley Campground"), controller.results.map { it.name })

        controller.onSearchTextChange("")
        assertEquals(3, controller.results.size)
    }

    @Test
    fun hasActiveFilters_isTrueWhenAnyFilterIsActive() {
        val controller = loadedController()
        assertFalse(controller.hasActiveFilters)

        controller.onRegionChange(Region.BANFF)
        assertTrue(controller.hasActiveFilters)
        controller.clearFilters()
        assertFalse(controller.hasActiveFilters)

        controller.onDateRangeChange("2027-06-01", "2027-06-05")
        assertTrue(controller.hasActiveFilters)
        controller.clearFilters()
        assertFalse(controller.hasActiveFilters)

        controller.onMinGuestsChange(4)
        assertTrue(controller.hasActiveFilters)
        controller.clearFilters()
        assertFalse(controller.hasActiveFilters)
    }

    @Test
    fun fetchCampsites_stopsLoadingWhenDone() {
        val controller = loadedController()
        assertFalse(controller.isLoading)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchCampsites_setsLoadFailedWhenTheRepositoryFails() = runBlocking {
        // a repository that always fails, like Firestore when the phone is offline
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> = throw Exception("offline")
        }
        val controller = CampsiteSearchController(failing, FakeLotteryRepository())
        controller.fetchCampsites()
        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun fetchCampsites_doesNotSetLoadFailedWhenCancelled() = runBlocking {
        // a repository that never answers, so the load is still running when it's cancelled
        val slow = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> = awaitCancellation()
        }
        val controller = CampsiteSearchController(slow, FakeLotteryRepository())
        val load = launch { controller.fetchCampsites() }
        yield() // let the load start
        load.cancelAndJoin()
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
    }

    @Test
    fun fetchCampsites_stillShowsCampsitesWhenAvailabilityFails() {
        val failing = object : CampsiteRepository by FakeCampsiteRepository(campsites) {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                throw Exception("offline")
        }
        val controller = loadedController(campsiteRepository = failing)
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertEquals(3, controller.results.size)
    }

    @Test
    fun hasOpenLottery_isTrueWhileRegistrationIsOpen() {
        assertTrue(loadedController().hasOpenLottery(campsite("1")))
    }

    @Test
    fun hasOpenLottery_isFalseBeforeRegistrationStarts() {
        assertFalse(loadedController().hasOpenLottery(campsite("2")))
    }

    @Test
    fun hasOpenLottery_isFalseOnceTheDrawHasRun() {
        assertFalse(loadedController().hasOpenLottery(campsite("3")))
    }

    @Test
    fun hasOpenLottery_isFalseWithNoLotteries() {
        val controller = loadedController(FakeLotteryRepository())
        assertTrue(controller.results.none { controller.hasOpenLottery(it) })
    }

    @Test
    fun hasOpenLottery_countsALotteryCoveringSeveralCampsites() {
        // one open lottery for both Hinton campsites (US 02.04.02)
        val shared = lotteries[0].copy(campsiteIds = listOf("1", "2"))
        val controller = loadedController(FakeLotteryRepository(listOf(shared)))
        assertTrue(controller.hasOpenLottery(campsite("1")))
        assertTrue(controller.hasOpenLottery(campsite("2")))
        assertFalse(controller.hasOpenLottery(campsite("3")))
    }

    @Test
    fun fetchCampsites_stillShowsCampsitesWhenLotteriesFail() {
        // the lotteries can't be loaded, like Firestore when the phone goes offline mid-load
        val failing = object : LotteryRepository by FakeLotteryRepository() {
            override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> =
                throw Exception("offline")
        }
        val controller = loadedController(failing)
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertEquals(3, controller.results.size)
        assertTrue(controller.results.none { controller.hasOpenLottery(it) })
    }

    @Test
    fun resultCountText_showsPluralForZeroOrMultipleCampsites() {
        val controller = loadedController()
        assertEquals("3 campsites", controller.resultCountText)
        controller.onSearchTextChange("lake louise")
        assertEquals("0 campsites", controller.resultCountText)
    }

    @Test
    fun resultCountText_showsSingularForOneCampsite() {
        val controller = loadedController()
        controller.onSearchTextChange("Bow")
        assertEquals("1 campsite", controller.resultCountText)
    }
}
