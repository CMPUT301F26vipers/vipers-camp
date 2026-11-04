package com.example.vipers.ui.lottery

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LotteryControllerTest {

    private lateinit var campsiteRepository: FakeCampsiteRepository
    private lateinit var lotteryRepository: FakeLotteryRepository
    private lateinit var controller: LotteryController

    private val ownerId = "owner-1"
    private val campsiteId = "camp-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        campsiteRepository = FakeCampsiteRepository()
        lotteryRepository = FakeLotteryRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun setupCampsiteAndController(
        openNights: List<String> = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
        heldNights: List<String> = emptyList()
    ) {
        val camp = Campsite(id = campsiteId, ownerId = ownerId, name = "Test Camp")
        campsiteRepository.addCampsite(camp)

        val calendar = AvailabilityCalendar(
            campsiteId = campsiteId,
            openNights = openNights,
            heldNights = heldNights
        )
        campsiteRepository.saveAvailability(calendar)

        controller = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = lotteryRepository,
            ownerId = ownerId,
            campsiteId = campsiteId
        )
        // Controller's init calls loadCampsiteData which runs synchronously with UnconfinedTestDispatcher
    }

    @Test
    fun loadingCampsiteAvailability() = runTest {
        setupCampsiteAndController()
        assertNotNull(controller.availability)
        assertEquals(4, controller.availability!!.openNights.size)
    }

    @Test
    fun rejectingEndDateEarlierThanStartDate() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-03")
        controller.onRangeEndChange("2026-01-01")
        controller.loadSelectedRange()

        assertEquals("End date must be on or after the start date.", controller.error)
    }

    @Test
    fun excludingUnavailableAndHeldNights() = runTest {
        // 02 is held, 05 is unavailable (not in openNights)
        setupCampsiteAndController(
            openNights = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
            heldNights = listOf("2026-01-02")
        )

        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-05")
        controller.loadSelectedRange()

        assertEquals(listOf("2026-01-01", "2026-01-03", "2026-01-04"), controller.availableNights)
    }

    @Test
    fun rangeAccumulationPreservesPreviousNightsAndDeduplicates() = runTest {
        setupCampsiteAndController(
            openNights = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
            heldNights = emptyList()
        )

        // Range A: 2026-01-01 to 2026-01-02
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        assertEquals(listOf("2026-01-01", "2026-01-02"), controller.availableNights)

        // Select 2026-01-01
        controller.toggleNight("2026-01-01", true)
        assertTrue(controller.selectedNights.contains("2026-01-01"))

        // Range B: 2026-01-03 to 2026-01-04
        controller.onRangeStartChange("2026-01-03")
        controller.onRangeEndChange("2026-01-04")
        controller.loadSelectedRange()

        // Assert that all four nights remain in availableNights
        assertEquals(
            listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
            controller.availableNights
        )

        // Assert that 2026-01-01 remains selected
        assertTrue(controller.selectedNights.contains("2026-01-01"))

        // Load an overlapping range: 2026-01-02 to 2026-01-03
        controller.onRangeStartChange("2026-01-02")
        controller.onRangeEndChange("2026-01-03")
        controller.loadSelectedRange()

        // Assert that there are still exactly four unique nights
        assertEquals(
            listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"),
            controller.availableNights
        )
    }

    @Test
    fun emptyNewRangeDisplaysErrorWithoutClearingPreviousNights() = runTest {
        setupCampsiteAndController(
            openNights = listOf("2026-01-01", "2026-01-02"),
            heldNights = listOf("2026-01-05")
        )

        // Load Range A
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        assertEquals(listOf("2026-01-01", "2026-01-02"), controller.availableNights)

        controller.toggleNight("2026-01-01", true)

        // Load Range B containing only held/unavailable night
        controller.onRangeStartChange("2026-01-05")
        controller.onRangeEndChange("2026-01-05")
        controller.loadSelectedRange()

        // Assert error is shown
        assertEquals("No available nights in this date range.", controller.error)

        // Assert previously accumulated nights and selections remain
        assertEquals(listOf("2026-01-01", "2026-01-02"), controller.availableNights)
        assertTrue(controller.selectedNights.contains("2026-01-01"))
    }

    @Test
    fun selectingAndDeselectingNights() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()

        controller.toggleNight("2026-01-01", true)
        assertTrue(controller.selectedNights.contains("2026-01-01"))

        controller.toggleNight("2026-01-01", false)
        assertFalse(controller.selectedNights.contains("2026-01-01"))
    }

    @Test
    fun selectAllAndDeselectAll() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-03")
        controller.loadSelectedRange()

        controller.toggleSelectAll() // Select all
        assertEquals(3, controller.selectedNights.size)

        controller.toggleSelectAll() // Deselect all
        assertTrue(controller.selectedNights.isEmpty())
    }

    @Test
    fun savingLotteryStoresCorrectlyAndDoesNotModifyAvailability() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()

        controller.toggleNight("2026-01-01", true)
        controller.saveLottery()

        assertTrue(controller.saved)

        val lotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
        assertEquals(1, lotteries.size)
        assertEquals(listOf("2026-01-01"), lotteries.first().nightsOffered)
        assertEquals(ownerId, lotteries.first().ownerId)

        val availability = campsiteRepository.getAvailability(campsiteId)
        assertEquals(listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"), availability.openNights)
    }

    @Test
    fun invalidSelectionsCannotBeSaved() = runTest {
        setupCampsiteAndController()
        // Save without selecting any nights
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("Select at least one night", controller.error)
    }

    @Test
    fun availabilityChangesBetweenSelectionAndSavingHandledCorrectly() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()

        controller.toggleNight("2026-01-01", true)

        // Before saving, simulate another user holding the night
        val cal = campsiteRepository.getAvailability(campsiteId)
        campsiteRepository.saveAvailability(cal.copy(heldNights = listOf("2026-01-01")))

        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("Your selected nights are no longer available.", controller.error)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
    }
}
