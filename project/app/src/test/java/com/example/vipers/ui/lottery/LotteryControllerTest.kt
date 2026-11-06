/*
 * LotteryControllerTest.kt
 * Unit tests for LotteryController creation logic, date ranges, and validation.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    }

    private var saveCalled = false

    private suspend fun fillValidLotteryForm() {
        setupCampsiteAndController()
        saveCalled = false
        val trackingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun addLottery(lottery: Lottery): String {
                saveCalled = true
                return lotteryRepository.addLottery(lottery)
            }
        }
        controller = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = trackingRepo,
            ownerId = ownerId,
            campsiteId = campsiteId
        )

        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        controller.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)
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
    fun parseStrictDate_withLenientOrPartialDates_returnsNull() = runTest {
        setupCampsiteAndController()

        // Lenient rollover date
        assertNull(controller.parseStrictDate("2027-02-30"))

        // Partial string
        assertNull(controller.parseStrictDate("2026-0"))

        // Trailing unparsed text
        assertNull(controller.parseStrictDate("2026-01-01abc"))

        // Valid date
        assertNotNull(controller.parseStrictDate("2026-01-01"))
    }

    @Test
    fun waitlistLimitValidation_nonNumericOrLessThanOne_showsError() = runTest {
        setupCampsiteAndController()

        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        controller.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)

        // Non-numeric text
        controller.onWaitlistCapacityChange("abc")
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Zero
        controller.onWaitlistCapacityChange("0")
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Negative
        controller.onWaitlistCapacityChange("-5")
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Blank (valid, no limit)
        controller.onWaitlistCapacityChange("")
        controller.saveLottery()
        assertTrue(controller.saved)
        assertNull(lotteryRepository.getLotteriesForCampsite(campsiteId).first().waitlistCapacity)
    }

    @Test
    fun saveLottery_inFlightDuplicateSubmission_ignoresSecondCall() = runTest {
        val saveDeferred = CompletableDeferred<Unit>()
        var addCallCount = 0

        val delayedRepo = object : LotteryRepository {
            override suspend fun addLottery(lottery: Lottery): String {
                addCallCount++
                saveDeferred.await()
                return "lottery-delayed"
            }
            override suspend fun getLottery(id: String): Lottery? = null
            override suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> = emptyList()
            override suspend fun saveEntry(entry: LotteryEntry) {}
            override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? = null
            override suspend fun getEntries(lotteryId: String): List<LotteryEntry> = emptyList()
        }

        val camp = Campsite(id = campsiteId, ownerId = ownerId, name = "Test Camp")
        campsiteRepository.addCampsite(camp)
        val cal = AvailabilityCalendar(campsiteId = campsiteId, openNights = listOf("2026-01-01", "2026-01-02"))
        campsiteRepository.saveAvailability(cal)

        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = delayedRepo,
            ownerId = ownerId,
            campsiteId = campsiteId
        )

        customController.onRangeStartChange("2026-01-01")
        customController.onRangeEndChange("2026-01-02")
        customController.loadSelectedRange()
        customController.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        customController.setRegistrationStart(now + 100_000)
        customController.setRegistrationEnd(now + 200_000)
        customController.setBookingStart(now + 200_000)
        customController.setBookingEnd(now + 300_000)

        // First save call suspends at delayedRepo.addLottery
        customController.saveLottery()
        assertTrue(customController.saving)
        assertEquals(1, addCallCount)

        // Duplicate save call while saving == true
        customController.saveLottery()
        assertEquals(1, addCallCount) // Call count remains 1!

        // Complete delayed save
        saveDeferred.complete(Unit)
        assertFalse(customController.saving)
        assertTrue(customController.saved)
    }

    @Test
    fun saveLottery_descriptionEnteredButNoNightsSelected_showsErrorAndRejectsSave() = runTest {
        setupCampsiteAndController()

        controller.onNightsOfferedTextChange("May 2027 weekends")
        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)

        // No calendar nights selected!
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("Select at least one night", controller.error)
    }

    @Test
    fun saveLottery_nightsSelectedWithoutDescription_savesConcreteIsoDates() = runTest {
        setupCampsiteAndController()

        controller.onNightsOfferedTextChange("") // Blank description
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        controller.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)

        controller.saveLottery()

        assertTrue(controller.saved)
        val savedLottery = lotteryRepository.getLotteriesForCampsite(campsiteId).first()
        assertEquals(listOf("2026-01-01"), savedLottery.nightsOffered)
        assertNull(savedLottery.nightsDescription)
    }

    @Test
    fun saveLottery_bothNightsAndDescriptionSelected_savesBothIndependently() = runTest {
        setupCampsiteAndController()

        controller.onNightsOfferedTextChange("May 2027 weekends")
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()
        controller.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)
        controller.onWinnersCountChange("3")
        controller.onWaitlistCapacityChange("20")

        controller.saveLottery()

        assertTrue(controller.saved)

        val lotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
        assertEquals(1, lotteries.size)
        val savedLottery = lotteries.first()
        assertEquals(listOf("2026-01-01"), savedLottery.nightsOffered)
        assertEquals("May 2027 weekends", savedLottery.nightsDescription)
        assertEquals(3, savedLottery.winnersCount)
        assertEquals(20, savedLottery.waitlistCapacity)
        assertEquals(now + 100_000, savedLottery.registrationStart)
        assertEquals(now + 200_000, savedLottery.registrationEnd)
        assertEquals(now + 200_000, savedLottery.bookingStart)
        assertEquals(now + 300_000, savedLottery.bookingEnd)
    }

    @Test
    fun availabilityChangesBetweenSelectionAndSavingHandledCorrectly() = runTest {
        setupCampsiteAndController()
        controller.onRangeStartChange("2026-01-01")
        controller.onRangeEndChange("2026-01-02")
        controller.loadSelectedRange()

        controller.toggleNight("2026-01-01", true)

        val now = System.currentTimeMillis()
        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(now + 200_000)
        controller.setBookingEnd(now + 300_000)

        // Before saving, simulate another user holding the night
        val cal = campsiteRepository.getAvailability(campsiteId)
        campsiteRepository.saveAvailability(cal.copy(heldNights = listOf("2026-01-01")))

        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("One or more selected nights are no longer available. Please reload the nights.", controller.error)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
    }

    @Test
    fun saveLottery_zeroWinnersCount_preventsSavingAndReportsError() = runTest {
        fillValidLotteryForm()
        controller.onWinnersCountChange("0")
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("At least one winner is required", controller.error)
        assertFalse(saveCalled)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
    }

    @Test
    fun saveLottery_negativeWinnersCount_preventsSavingAndReportsError() = runTest {
        fillValidLotteryForm()
        controller.onWinnersCountChange("-1")
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("At least one winner is required", controller.error)
        assertFalse(saveCalled)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
    }

    @Test
    fun saveLottery_alphabeticWinnersCount_preventsSavingAndReportsError() = runTest {
        fillValidLotteryForm()
        controller.onWinnersCountChange("abc")
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("At least one winner is required", controller.error)
        assertFalse(saveCalled)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
    }

    @Test
    fun saveLottery_emptyWinnersCount_preventsSavingAndReportsError() = runTest {
        fillValidLotteryForm()
        controller.onWinnersCountChange("")
        controller.saveLottery()

        assertFalse(controller.saved)
        assertEquals("At least one winner is required", controller.error)
        assertFalse(saveCalled)
        assertTrue(lotteryRepository.getLotteriesForCampsite(campsiteId).isEmpty())
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
    fun fetchCampsiteData_loadsCampsiteAndAvailability() = runTest {
        setupCampsiteAndController()
        controller.fetchCampsiteData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertNotNull(controller.campsite)
        assertEquals("Test Camp", controller.campsite!!.name)
        assertNotNull(controller.availability)
        assertEquals(4, controller.availability!!.openNights.size)
    }

    @Test
    fun fetchCampsiteData_whenCampsiteNotFoundOrNotOwned_setsError() = runTest {
        val otherOwnerCamp = Campsite(id = "other-camp", ownerId = "someone-else", name = "Other Camp")
        campsiteRepository.addCampsite(otherOwnerCamp)

        val unownedController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = lotteryRepository,
            ownerId = ownerId,
            campsiteId = "other-camp"
        )
        unownedController.fetchCampsiteData()

        assertFalse(unownedController.loading)
        assertEquals("Campsite not found or you do not own it.", unownedController.error)
        assertNull(unownedController.campsite)
    }

    @Test
    fun fetchCampsiteData_whenRepositoryFails_setsError() = runTest {
        val failingRepo = object : com.example.vipers.domain.repository.CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = throw RuntimeException("Firestore offline")
        }
        val failingController = LotteryController(
            campsiteRepository = failingRepo,
            lotteryRepository = lotteryRepository,
            ownerId = ownerId,
            campsiteId = campsiteId
        )
        failingController.fetchCampsiteData()

        assertFalse(failingController.loading)
        assertEquals("Unable to load campsite availability.", failingController.error)
    }
}
