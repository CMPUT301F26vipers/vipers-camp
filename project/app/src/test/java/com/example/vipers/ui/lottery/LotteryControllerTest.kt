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
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.UpdateLotteryResult
import com.example.vipers.ui.campsites.FormMode
import java.time.Instant
import java.time.ZoneId
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

    private fun datesStartingAt(millis: Long, count: Int = 4, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val startDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return (0 until count).map { startDate.plusDays(it.toLong()).toString() }
    }

    private var saveCalled = false

    private suspend fun fillValidLotteryForm() {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)
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

        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()
        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
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
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)

        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()
        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
        controller.setBookingEnd(now + 300_000)

        // Non-numeric text
        controller.onWaitlistCapacityChange("abc")
        assertEquals("Waitlist limit must be a whole number at least 1", controller.waitlistCapacityError)
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Decimal
        controller.onWaitlistCapacityChange("3.14")
        assertEquals("Waitlist limit must be a whole number at least 1", controller.waitlistCapacityError)
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Zero
        controller.onWaitlistCapacityChange("0")
        assertEquals("Waitlist limit must be a whole number at least 1", controller.waitlistCapacityError)
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Negative
        controller.onWaitlistCapacityChange("-5")
        assertEquals("Waitlist limit must be a whole number at least 1", controller.waitlistCapacityError)
        controller.saveLottery()
        assertEquals("Waitlist limit must be a whole number at least 1", controller.error)
        assertFalse(controller.saved)

        // Blank (valid, no limit)
        controller.onWaitlistCapacityChange("")
        assertNull(controller.waitlistCapacityError)
        controller.saveLottery()
        assertTrue(controller.saved)
        assertNull(lotteryRepository.getLotteriesForCampsite(campsiteId).first().waitlistCapacity)
    }

    @Test
    fun waitlistCapacity_validPositiveInteger_savedCorrectly() = runTest {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)
        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()
        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
        controller.setBookingEnd(now + 300_000)

        controller.onWaitlistCapacityChange("50")
        assertNull(controller.waitlistCapacityError)
        controller.saveLottery()

        assertTrue(controller.saved)
        val saved = lotteryRepository.getLotteriesForCampsite(campsiteId).first()
        assertEquals(50, saved.waitlistCapacity)
    }

    @Test
    fun saveLottery_inFlightDuplicateSubmission_ignoresSecondCall() = runTest {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)

        val saveDeferred = CompletableDeferred<Unit>()
        var addCallCount = 0

        val delayedRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun addLottery(lottery: Lottery): String {
                addCallCount++
                saveDeferred.await()
                return "lottery-delayed"
            }
        }

        val camp = Campsite(id = campsiteId, ownerId = ownerId, name = "Test Camp")
        campsiteRepository.addCampsite(camp)
        val cal = AvailabilityCalendar(campsiteId = campsiteId, openNights = listOf(nights[0], nights[1]))
        campsiteRepository.saveAvailability(cal)

        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = delayedRepo,
            ownerId = ownerId,
            campsiteId = campsiteId
        )

        customController.onRangeStartChange(nights[0])
        customController.onRangeEndChange(nights[1])
        customController.loadSelectedRange()
        customController.toggleNight(nights[0], true)

        customController.setRegistrationStart(now + 100_000)
        customController.setRegistrationEnd(now + 200_000)
        customController.setBookingStart(bookingStart)
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
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)

        controller.onNightsOfferedTextChange("") // Blank description
        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()
        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
        controller.setBookingEnd(now + 300_000)

        controller.saveLottery()

        assertTrue(controller.saved)
        val savedLottery = lotteryRepository.getLotteriesForCampsite(campsiteId).first()
        assertEquals(listOf(nights[0]), savedLottery.nightsOffered)
        assertNull(savedLottery.nightsDescription)
    }

    @Test
    fun saveLottery_bothNightsAndDescriptionSelected_savesBothIndependently() = runTest {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)

        controller.onNightsOfferedTextChange("May 2027 weekends")
        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()
        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
        controller.setBookingEnd(now + 300_000)
        controller.onWinnersCountChange("3")
        controller.onWaitlistCapacityChange("20")

        controller.saveLottery()

        assertTrue(controller.saved)

        val lotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
        assertEquals(1, lotteries.size)
        val savedLottery = lotteries.first()
        assertEquals(listOf(nights[0]), savedLottery.nightsOffered)
        assertEquals("May 2027 weekends", savedLottery.nightsDescription)
        assertEquals(3, savedLottery.winnersCount)
        assertEquals(20, savedLottery.waitlistCapacity)
        assertEquals(now + 100_000, savedLottery.registrationStart)
        assertEquals(now + 200_000, savedLottery.registrationEnd)
        assertEquals(bookingStart, savedLottery.bookingStart)
        assertEquals(now + 300_000, savedLottery.bookingEnd)
    }

    @Test
    fun availabilityChangesBetweenSelectionAndSavingHandledCorrectly() = runTest {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        setupCampsiteAndController(openNights = nights)
        controller.onRangeStartChange(nights[0])
        controller.onRangeEndChange(nights[1])
        controller.loadSelectedRange()

        controller.toggleNight(nights[0], true)

        controller.setRegistrationStart(now + 100_000)
        controller.setRegistrationEnd(now + 200_000)
        controller.setBookingStart(bookingStart)
        controller.setBookingEnd(now + 300_000)

        // Before saving, simulate another user holding the night
        val cal = campsiteRepository.getAvailability(campsiteId)
        campsiteRepository.saveAvailability(cal.copy(heldNights = listOf(nights[0])))

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

    // --- US 02.04.06 Edit Mode Tests ---

    @Test
    fun mode_defaultIsCreate() = runTest {
        setupCampsiteAndController()
        assertEquals(FormMode.CREATE, controller.mode)
        assertNull(controller.loadedLottery)
        assertFalse(controller.isReadOnly)
        assertNull(controller.readOnlyReason)
        assertFalse(controller.notAllowed)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchLotteryForEdit_ownerUpcomingLottery_prefillsFieldsAndSetsModeEdit() = runTest {
        val now = 1_000L
        val original = Lottery(
            id = "lottery-1",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            nightsDescription = "Early June weekends",
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            winnersCount = 3,
            waitlistCapacity = 12,
            waitingUserIds = listOf("user-1", "user-2"),
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01", "2027-06-02"))
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        assertEquals(FormMode.EDIT, controller.mode)
        assertEquals(original.copy(id = id), controller.loadedLottery)
        assertFalse(controller.isReadOnly)
        assertNull(controller.readOnlyReason)
        assertFalse(controller.notAllowed)
        assertFalse(controller.loadFailed)

        // Verify prefilled form fields
        assertEquals(setOf("2027-06-01", "2027-06-02"), controller.selectedNights)
        assertEquals(listOf("2027-06-01", "2027-06-02"), controller.availableNights)
        assertEquals("Early June weekends", controller.nightsOfferedText)
        assertEquals(2_000L, controller.registrationStartMillis)
        assertEquals(3_000L, controller.registrationEndMillis)
        assertEquals(4_000L, controller.bookingStartMillis)
        assertEquals(5_000L, controller.bookingEndMillis)
        assertEquals("3", controller.winnersCountText)
        assertEquals("12", controller.waitlistCapacityText)
        assertNull(controller.waitlistCapacityError)
    }

    @Test
    fun fetchLotteryForEdit_ownerOpenLottery_prefillsFieldsAndRemainsEditable() = runTest {
        val now = 2_500L // during registration (2000..3000)
        val original = Lottery(
            id = "lottery-open",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            winnersCount = 2,
            waitlistCapacity = null,
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        assertEquals(FormMode.EDIT, controller.mode)
        assertFalse(controller.isReadOnly)
        assertNull(controller.readOnlyReason)
        assertEquals("", controller.waitlistCapacityText)
    }

    @Test
    fun fetchLotteryForEdit_closedLottery_marksReadOnlyWithExplanation() = runTest {
        val now = 3_500L // after registrationEnd (3000)
        val original = Lottery(
            id = "lottery-closed",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        setupCampsiteAndController()
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        assertEquals(FormMode.EDIT, controller.mode)
        assertTrue(controller.isReadOnly)
        assertEquals("Registration has closed; this lottery can no longer be edited.", controller.readOnlyReason)
    }

    @Test
    fun fetchLotteryForEdit_drawnLottery_marksReadOnlyWithExplanation() = runTest {
        val now = 2_500L
        val original = Lottery(
            id = "lottery-drawn",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            drawnAt = 2_500L,
        )
        setupCampsiteAndController()
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        assertEquals(FormMode.EDIT, controller.mode)
        assertTrue(controller.isReadOnly)
        assertEquals("Cannot edit a lottery that has already been drawn", controller.readOnlyReason)
    }

    @Test
    fun fetchLotteryForEdit_removedLottery_marksReadOnlyWithExplanation() = runTest {
        val now = 1_000L
        val original = Lottery(
            id = "lottery-removed",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            removed = true,
        )
        setupCampsiteAndController()
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        assertEquals(FormMode.EDIT, controller.mode)
        assertTrue(controller.isReadOnly)
        assertEquals("Cannot edit a removed lottery", controller.readOnlyReason)
    }

    @Test
    fun fetchLotteryForEdit_nonOwner_setsNotAllowedAndLeavesFormUnchanged() = runTest {
        val original = Lottery(
            id = "lottery-other",
            ownerId = "someone-else",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        setupCampsiteAndController()
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id)

        assertTrue(controller.notAllowed)
        assertEquals("Lottery not found or you do not own it.", controller.error)
        assertEquals(FormMode.CREATE, controller.mode)
        assertNull(controller.loadedLottery)
    }

    @Test
    fun fetchLotteryForEdit_missingLottery_setsNotAllowedAndLeavesFormUnchanged() = runTest {
        setupCampsiteAndController()

        controller.fetchLotteryForEdit("does-not-exist")

        assertTrue(controller.notAllowed)
        assertEquals("Lottery not found or you do not own it.", controller.error)
        assertEquals(FormMode.CREATE, controller.mode)
        assertNull(controller.loadedLottery)
    }

    @Test
    fun fetchLotteryForEdit_repositoryException_setsLoadFailed() = runTest {
        val failingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun getLottery(id: String): Lottery? = throw RuntimeException("Firestore offline")
        }
        setupCampsiteAndController()
        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = failingRepo,
            ownerId = ownerId,
            campsiteId = campsiteId,
        )

        customController.fetchLotteryForEdit("lottery-1")

        assertTrue(customController.loadFailed)
        assertFalse(customController.notAllowed)
        assertEquals("Couldn't load the lottery. Check your connection and try again.", customController.error)
    }

    @Test
    fun saveLottery_editMode_successUpdatesViaRepository() = runTest {
        val now = 1_000L
        val original = Lottery(
            id = "lottery-edit",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            winnersCount = 2,
            waitlistCapacity = 10,
            waitingUserIds = listOf("user-1"),
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01", "2027-06-02"))
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        // Modify editable fields
        controller.onWinnersCountChange("5")
        controller.onWaitlistCapacityChange("20")
        controller.onNightsOfferedTextChange("Updated notes")

        controller.saveLottery(now = now)

        assertTrue("Expected controller.saved to be true but error was '${controller.error}'", controller.saved)
        assertFalse(controller.saving)
        assertNull(controller.error)

        val updated = lotteryRepository.getLottery(id)
        assertNotNull(updated)
        assertEquals(5, updated!!.winnersCount)
        assertEquals(20, updated.waitlistCapacity)
        assertEquals("Updated notes", updated.nightsDescription)
        // Stored uneditable fields preserved
        assertEquals(listOf("user-1"), updated.waitingUserIds)
        assertEquals(ownerId, updated.ownerId)
    }

    @Test
    fun saveLottery_editMode_readOnlyBlocksSave() = runTest {
        val now = 3_500L // closed
        val original = Lottery(
            id = "lottery-closed-save",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        setupCampsiteAndController()
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)
        assertTrue(controller.isReadOnly)

        controller.saveLottery(now = now)

        assertFalse(controller.saved)
        assertFalse(controller.saving)
        assertEquals("Registration has closed; this lottery can no longer be edited.", controller.error)
    }

    @Test
    fun saveLottery_editMode_modelValidationFailureStopsSave() = runTest {
        val now = 1_000L
        val original = Lottery(
            id = "lottery-val-fail",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            winnersCount = 2,
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        // Set invalid winners count (0)
        controller.onWinnersCountChange("0")
        controller.saveLottery(now = now)

        assertFalse(controller.saved)
        assertFalse(controller.saving)
        assertEquals("At least one winner is required", controller.error)
    }

    @Test
    fun saveLottery_editMode_repositoryRejectionDueToStaleCapacityReportsError() = runTest {
        val now = 2_500L
        val original = Lottery(
            id = "lottery-stale-cap",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            waitlistCapacity = 10,
            waitingUserIds = listOf("user-1"),
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        controller.fetchLotteryForEdit(id, now = now)

        // While owner is editing, two new entrants join concurrently
        lotteryRepository.joinWaitingList(LotteryEntry(lotteryId = id, userId = "user-2"), now = now)
        lotteryRepository.joinWaitingList(LotteryEntry(lotteryId = id, userId = "user-3"), now = now)

        // Owner lowers capacity to 2 (below authoritative count 3)
        controller.onWaitlistCapacityChange("2")
        controller.saveLottery(now = now)

        assertFalse(controller.saved)
        assertFalse(controller.saving)
        assertEquals("Waitlist limit cannot be less than current entrant count (3)", controller.error)
    }

    @Test
    fun saveLottery_editMode_repositoryRejectionDueToStaleStatusClosedReportsError() = runTest {
        val nowOnOpen = 2_999L // just before registrationEnd (3000)
        val original = Lottery(
            id = "lottery-stale-status",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        // Mock repository rejecting edit if time has elapsed past registrationEnd
        val timeAwareRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun updateLottery(
                lottery: Lottery,
                callerId: String,
                now: Long,
                zone: java.time.ZoneId
            ): UpdateLotteryResult {
                // Simulate time advancing past registrationEnd during transactional save
                return lotteryRepository.updateLottery(lottery, callerId, now = 3_001L, zone = zone)
            }
        }

        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = timeAwareRepo,
            ownerId = ownerId,
            campsiteId = campsiteId,
        )
        customController.fetchLotteryForEdit(id, now = nowOnOpen)
        assertFalse(customController.isReadOnly)

        customController.onWinnersCountChange("5")
        customController.saveLottery(now = nowOnOpen)

        assertFalse("Expected saved to be false, but error was: '${customController.error}' and saved was: ${customController.saved}", customController.saved)
        assertFalse(customController.saving)
        assertEquals("Registration has closed; this lottery can no longer be edited.", customController.error)
    }

    @Test
    fun saveLottery_editMode_inFlightDuplicateSubmissionIgnored() = runTest {
        val now = 1_000L
        val original = Lottery(
            id = "lottery-dup",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        val saveGate = CompletableDeferred<Unit>()
        var updateCallCount = 0
        val slowRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun updateLottery(
                lottery: Lottery,
                callerId: String,
                now: Long,
                zone: java.time.ZoneId
            ): UpdateLotteryResult {
                updateCallCount++
                saveGate.await()
                return lotteryRepository.updateLottery(lottery, callerId, now, zone)
            }
        }

        val slowController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = slowRepo,
            ownerId = ownerId,
            campsiteId = campsiteId,
        )
        slowController.fetchLotteryForEdit(id, now = now)

        slowController.saveLottery(now = now)
        assertTrue(slowController.saving)
        assertEquals(1, updateCallCount)

        // Second tap while in-flight
        slowController.saveLottery(now = now)
        assertEquals(1, updateCallCount) // Duplicate call was ignored

        saveGate.complete(Unit)
        assertFalse(slowController.saving)
        assertTrue(slowController.saved)
    }

    @Test
    fun saveLottery_registrationClosesWhileFormOpen_setsReadOnlyAndUpdatesReason() = runTest {
        val original = Lottery(
            id = "lottery-race-close",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 4_000L,
            bookingStart = 5_000L,
            bookingEnd = 6_000L,
            winnersCount = 2,
        )
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val id = lotteryRepository.addLottery(original)

        // Loaded while open at t=3_000L
        controller.fetchLotteryForEdit(id, now = 3_000L)
        assertFalse(controller.isReadOnly)
        assertEquals(FormMode.EDIT, controller.mode)

        // Owner attempts to save at t=4_500L (registration has passed)
        controller.saveLottery(now = 4_500L)

        assertFalse(controller.saved)
        assertFalse(controller.saving)
        assertTrue(controller.isReadOnly)
        assertEquals("Registration has closed; this lottery can no longer be edited.", controller.error)
        assertEquals("Registration has closed; this lottery can no longer be edited.", controller.readOnlyReason)
    }

    @Test
    fun loadForEdit_loadsLotteryOnlyOnceWhenCalled() = runTest {
        val original = Lottery(
            id = "lottery-once",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        var getLotteryCallCount = 0
        val countingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun getLottery(id: String): Lottery? {
                getLotteryCallCount++
                return lotteryRepository.getLottery(id)
            }
        }
        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = countingRepo,
            ownerId = ownerId,
            campsiteId = campsiteId,
        )
        val id = countingRepo.addLottery(original)

        customController.fetchLotteryForEdit(id)
        assertEquals(1, getLotteryCallCount)
        assertEquals(FormMode.EDIT, customController.mode)

        // Calling load again on already loaded/EDIT controller is idempotent
        customController.fetchLotteryForEdit(id)
        assertEquals(2, getLotteryCallCount)
    }

    @Test
    fun saveEdits_whenRepositoryReturnsReadOnly_setsReadOnlyAndReasonDirectly() = runTest {
        campsiteRepository.addCampsite(Campsite(id = campsiteId, ownerId = ownerId, name = "Test Camp"))
        val original = Lottery(
            id = "lottery-readonly",
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        val readOnlyRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun updateLottery(
                lottery: Lottery,
                callerId: String,
                now: Long,
                zone: java.time.ZoneId
            ): UpdateLotteryResult {
                return UpdateLotteryResult.ReadOnly("Registration has closed; this lottery can no longer be edited.")
            }
        }
        val customController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = readOnlyRepo,
            ownerId = ownerId,
            campsiteId = campsiteId,
        )

        customController.saveEdits(original)

        assertFalse(customController.saved)
        assertTrue(customController.isReadOnly)
        assertEquals("Registration has closed; this lottery can no longer be edited.", customController.readOnlyReason)
        assertEquals("Registration has closed; this lottery can no longer be edited.", customController.error)
    }

    // --- US 02.04.02 Multi-Campsite Selection Tests ---

    @Test
    fun createMode_listsOnlyCurrentOwnersActiveCampsitesAndPreselectsLaunchingSite() = runTest {
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Maple Grove"))
        campsiteRepository.addCampsite(Campsite(id = "camp-removed", ownerId = ownerId, name = "Closed Site", removed = true))
        campsiteRepository.addCampsite(Campsite(id = "camp-foreign", ownerId = "other-owner", name = "Foreign Site"))

        val multiController = LotteryController(
            campsiteRepository = campsiteRepository,
            lotteryRepository = lotteryRepository,
            ownerId = ownerId,
            campsiteId = "camp-1",
        )

        assertFalse(multiController.loading)
        assertNull(multiController.error)
        assertEquals(setOf("camp-1", "camp-2"), multiController.ownedCampsites.map { it.id }.toSet())
        assertEquals(setOf("camp-1"), multiController.selectedCampsiteIds)
    }

    @Test
    fun ownerListOrCalendarReadError_setsLoadFailedInCreateAndEditModes() = runTest {
        val now = 1_000L
        val stored = Lottery(
            id = "lot-err",
            ownerId = ownerId,
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
        )
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Pine Ridge"))
        val savedId = lotteryRepository.addLottery(stored)

        val ownerListFailRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getCampsitesByOwner(ownerId: String): List<Campsite> =
                throw RuntimeException("Owner query failed")
        }
        val createFail = LotteryController(ownerListFailRepo, lotteryRepository, ownerId, "camp-1")
        assertTrue(createFail.loadFailed)
        assertEquals("Unable to load campsite availability.", createFail.error)

        val editOwnerFail = LotteryController(ownerListFailRepo, lotteryRepository, ownerId, "")
        editOwnerFail.fetchLotteryForEdit(savedId, now = now)
        assertTrue(editOwnerFail.loadFailed)
        assertEquals("Couldn't load the lottery. Check your connection and try again.", editOwnerFail.error)

        val calendarFailRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                throw RuntimeException("Calendar offline")
        }
        val editCalFail = LotteryController(calendarFailRepo, lotteryRepository, ownerId, "")
        editCalFail.fetchLotteryForEdit(savedId, now = now)
        assertTrue(editCalFail.loadFailed)
        assertEquals("Couldn't load the lottery. Check your connection and try again.", editCalFail.error)
    }

    @Test
    fun editMode_withIneligibleStoredCampsite_requiresConfirmationWithoutExposingForeignSite() = runTest {
        val now = 1_000L
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-removed", ownerId = ownerId, name = "Removed Site", removed = true))
        campsiteRepository.addCampsite(Campsite(id = "camp-foreign", ownerId = "other-owner", name = "Secret Foreign Site"))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-1", openNights = listOf("2027-06-01")))

        val id = lotteryRepository.addLottery(
            Lottery(
                ownerId = ownerId,
                campsiteIds = listOf("camp-1", "camp-removed", "camp-foreign"),
                nightsOffered = listOf("2027-06-01"),
                registrationStart = 2_000L,
                registrationEnd = 3_000L,
                bookingStart = 4_000L,
                bookingEnd = 5_000L,
            ),
        )

        val inspectedCampsites = mutableListOf<String>()
        val trackingCampsites = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getCampsite(id: String): Campsite? {
                inspectedCampsites.add(id)
                return campsiteRepository.getCampsite(id)
            }
        }

        val editController = LotteryController(trackingCampsites, lotteryRepository, ownerId, "")
        editController.fetchLotteryForEdit(id, now = now)

        assertTrue("camp-foreign" !in inspectedCampsites)
        assertEquals(listOf("camp-1"), editController.ownedCampsites.map { it.id })
        assertEquals(setOf("camp-1"), editController.selectedCampsiteIds)
        assertTrue(editController.hasIneligibleStoredCampsites)
        assertTrue(editController.requiresIneligibleRemovalConfirmation)

        // Saving before explicit confirmation is blocked and leaves stored IDs untouched.
        editController.saveLottery(now = now)
        assertFalse(editController.saved)
        assertEquals(
            "Please confirm removing the unavailable campsite(s) from this lottery before saving.",
            editController.error,
        )
        assertEquals(listOf("camp-1", "camp-removed", "camp-foreign"), lotteryRepository.getLottery(id)?.campsiteIds)

        // Confirming removal allows saving the cleaned campsite list.
        editController.onConfirmRemoveIneligibleChange(true)
        assertFalse(editController.requiresIneligibleRemovalConfirmation)
        editController.saveLottery(now = now)
        assertTrue(editController.saved)
        assertEquals(listOf("camp-1"), lotteryRepository.getLottery(id)?.campsiteIds)
    }

    @Test
    fun loadSelectedRange_withMultipleCampsites_offersIntersectionOnlyAndShowsConflictsOnChange() = runTest {
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Maple Grove"))
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(
                campsiteId = "camp-1",
                openNights = listOf("2026-06-01", "2026-06-02", "2026-06-03"),
                heldNights = listOf("2026-06-02"),
            ),
        )
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(
                campsiteId = "camp-2",
                openNights = listOf("2026-06-01", "2026-06-02", "2026-06-03"),
                heldNights = listOf("2026-06-03"),
            ),
        )

        val multiController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "camp-1")
        multiController.onRangeStartChange("2026-06-01")
        multiController.onRangeEndChange("2026-06-03")
        multiController.loadSelectedRange()
        assertEquals(listOf("2026-06-01", "2026-06-03"), multiController.availableNights)
        multiController.toggleNight("2026-06-01", true)
        multiController.toggleNight("2026-06-03", true)

        // Selecting camp-2 makes 2026-06-03 conflicting, but keeps both offered nights visible.
        multiController.toggleCampsite("camp-2", true)
        assertEquals(setOf("2026-06-03"), multiController.conflictingNights)
        assertNotNull(multiController.conflictMessage)
        assertEquals(listOf("2026-06-01", "2026-06-03"), multiController.availableNights)
        assertEquals(setOf("2026-06-01", "2026-06-03"), multiController.selectedNights)

        // Load nights shows only shared free night 2026-06-01 once conflict is unchecked.
        multiController.toggleNight("2026-06-03", false)
        assertTrue(multiController.conflictingNights.isEmpty())
        multiController.loadSelectedRange()
        assertEquals(listOf("2026-06-01"), multiController.availableNights)
    }

    @Test
    fun toggleCampsite_staleAsyncCalendarLoad_doesNotValidateNewerSelection() = runTest {
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Site 2"))
        campsiteRepository.addCampsite(Campsite(id = "camp-3", ownerId = ownerId, name = "Site 3"))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-1", openNights = listOf("2026-06-01", "2026-06-02")))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-3", openNights = listOf("2026-06-01", "2026-06-02")))

        val slowCamp2Calendar = CompletableDeferred<AvailabilityCalendar>()
        val gatedRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                if (campsiteId == "camp-2") slowCamp2Calendar.await()
                else campsiteRepository.getAvailability(campsiteId)
        }

        val raceController = LotteryController(gatedRepo, lotteryRepository, ownerId, "camp-1")
        raceController.onRangeStartChange("2026-06-01")
        raceController.onRangeEndChange("2026-06-02")
        raceController.loadSelectedRange()
        raceController.toggleSelectAll()

        // Toggle camp-2 (suspends on slowCamp2Calendar), then switch selection to camp-3 before it returns.
        raceController.toggleCampsite("camp-2", true)
        assertTrue(raceController.loadingCampsiteCalendars)
        raceController.toggleCampsite("camp-2", false)
        raceController.toggleCampsite("camp-3", true)
        assertFalse(raceController.loadingCampsiteCalendars)
        assertTrue(raceController.conflictingNights.isEmpty())

        // Stale camp-2 response arrives with a conflict on 2026-06-02: must NOT affect {camp-1, camp-3}.
        slowCamp2Calendar.complete(
            AvailabilityCalendar("camp-2", openNights = listOf("2026-06-01"), heldNights = listOf("2026-06-02")),
        )

        assertEquals(setOf("camp-1", "camp-3"), raceController.selectedCampsiteIds)
        assertTrue(raceController.conflictingNights.isEmpty())
        assertNull(raceController.conflictMessage)
        assertNull(raceController.error)
    }

    @Test
    fun createMode_whenSecondCalendarChangesBeforeSaveOrSiteBecomesRemoved_rejectsSave() = runTest {
        val now = System.currentTimeMillis()
        val bookingStart = now + 200_000
        val nights = datesStartingAt(bookingStart)
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Site 2"))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-1", openNights = nights))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-2", openNights = nights))

        val multiController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "camp-1")
        multiController.toggleCampsite("camp-2", true)
        multiController.onRangeStartChange(nights[0])
        multiController.onRangeEndChange(nights[1])
        multiController.loadSelectedRange()
        multiController.toggleNight(nights[0], true)
        multiController.setRegistrationStart(now + 100_000)
        multiController.setRegistrationEnd(now + 200_000)
        multiController.setBookingStart(bookingStart)
        multiController.setBookingEnd(now + 400_000)

        // Second campsite loses nights[0] before save
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-2", openNights = nights, heldNights = listOf(nights[0])),
        )
        multiController.saveLottery(now = now)
        assertFalse(multiController.saved)
        assertEquals("One or more selected nights are no longer available. Please reload the nights.", multiController.error)
        assertEquals(setOf(nights[0]), multiController.conflictingNights)

        // Restore calendar, then transfer camp-2 away before save -> ownership recheck blocks save
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-2", openNights = nights))
        multiController.saveNew(
            Lottery(
                ownerId = ownerId,
                campsiteIds = listOf("camp-1", "camp-2"),
                nightsOffered = listOf(nights[0]),
                registrationStart = now + 100_000,
                registrationEnd = now + 200_000,
                bookingStart = bookingStart,
                bookingEnd = now + 400_000,
            ).also {
                campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = "other-owner", name = "Taken"))
            },
        )
        assertFalse(multiController.saved)
        assertEquals(
            "One or more selected campsites are no longer available or you do not own them.",
            multiController.error,
        )
    }

    @Test
    fun editMode_unrelatedEditPreservesExistingUnavailableNights_whileNewPairsAreValidated() = runTest {
        val now = 1_000L
        val n0 = "2027-06-01"
        val n1 = "2027-06-02"
        val n2 = "2027-06-03"
        val n3 = "2027-06-04"
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Site 2"))
        // n1 is now held at camp-1 (unavailable since creation); n3 is not open at camp-1
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-1", openNights = listOf(n0, n1, n2), heldNights = listOf(n1)),
        )
        // n0 and n1 are held at camp-2; only n2 is free at camp-2
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-2", openNights = listOf(n0, n1, n2), heldNights = listOf(n0, n1)),
        )

        val stored = Lottery(
            ownerId = ownerId,
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf(n0, n1),
            registrationStart = 2_000L,
            registrationEnd = 3_000L,
            bookingStart = 4_000L,
            bookingEnd = 5_000L,
            winnersCount = 1,
        )
        val id = lotteryRepository.addLottery(stored)

        val editController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "")
        editController.fetchLotteryForEdit(id, now = now)
        assertTrue(editController.conflictingNights.isEmpty())

        // 1. Unrelated edit (winnersCount only) preserves existing n0, n1 even though n1 is now held
        editController.onWinnersCountChange("2")
        editController.saveLottery(now = now)
        assertTrue(editController.saved)
        assertEquals(listOf(n0, n1), lotteryRepository.getLottery(id)?.nightsOffered)
        assertEquals(2, lotteryRepository.getLottery(id)?.winnersCount)

        // 2. Direct saveEdits adding a night n3 at retained camp-1 rejects because (camp-1, n3) is unavailable
        val editController2 = LotteryController(campsiteRepository, lotteryRepository, ownerId, "")
        editController2.fetchLotteryForEdit(id, now = now)
        editController2.saveEdits(stored.copy(id = id, nightsOffered = listOf(n0, n1, n3)), now = now)
        assertFalse(editController2.saved)
        assertEquals("One or more selected nights are no longer available. Please reload the nights.", editController2.error)

        // 3. Selecting camp-2 introduces (camp-2, n0) & (camp-2, n1) conflicts; resolving to n2 succeeds
        val editController3 = LotteryController(campsiteRepository, lotteryRepository, ownerId, "")
        editController3.fetchLotteryForEdit(id, now = now)
        editController3.toggleCampsite("camp-2", true)
        assertEquals(setOf(n0, n1), editController3.conflictingNights)
        editController3.onRangeStartChange(n0)
        editController3.onRangeEndChange(n2)
        editController3.loadSelectedRange()
        editController3.toggleNight(n0, false)
        editController3.toggleNight(n1, false)
        editController3.toggleNight(n2, true)
        editController3.saveLottery(now = now)
        assertTrue(editController3.saved)
        val updated = lotteryRepository.getLottery(id)!!
        assertEquals(listOf("camp-1", "camp-2"), updated.campsiteIds)
        assertEquals(listOf(n2), updated.nightsOffered)
    }

    @Test
    fun saveLottery_noCampsiteSelected_rejectsSave() = runTest {
        setupCampsiteAndController(openNights = listOf("2027-06-01"))
        val now = 1_000L
        controller.toggleCampsite(campsiteId, false)
        controller.toggleNight("2027-06-01", true)
        controller.setRegistrationStart(2_000L)
        controller.setRegistrationEnd(3_000L)
        controller.setBookingStart(4_000L)
        controller.setBookingEnd(5_000L)

        controller.saveLottery(now = now)
        assertFalse(controller.saved)
        assertEquals("Select a campsite", controller.error)
    }

    @Test
    fun toggleCampsite_calendarLoadFailure_preservesCreateAndEditDraft_blocksActions_andRecoversOnRetryOrUncheck() = runTest {
        val now = 1_000L
        val night0 = "2027-06-01"
        val night1 = "2027-06-02"
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Maple Grove"))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-1", openNights = listOf(night0, night1)))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-2", openNights = listOf(night0)))

        var failCamp2Calendar = true
        val flakyRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar {
                if (campsiteId == "camp-2" && failCamp2Calendar) {
                    throw RuntimeException("Calendar timeout")
                }
                return campsiteRepository.getAvailability(campsiteId)
            }
        }

        // 1. Create mode: fill draft first, then select camp-2 whose calendar load fails.
        val createController = LotteryController(flakyRepo, lotteryRepository, ownerId, "camp-1")
        createController.onNightsOfferedTextChange("June family nights")
        createController.onWinnersCountChange("3")
        createController.onRangeStartChange(night0)
        createController.onRangeEndChange(night1)
        createController.loadSelectedRange()
        createController.toggleNight(night0, true)
        createController.setRegistrationStart(2_000L)
        createController.setRegistrationEnd(3_000L)
        createController.setBookingStart(4_000L)
        createController.setBookingEnd(5_000L)

        createController.toggleCampsite("camp-2", true)

        assertFalse(createController.loadFailed)
        assertFalse(createController.loadingCampsiteCalendars)
        assertTrue(createController.hasMissingSelectedCalendars)
        assertEquals(
            "Unable to load campsite availability. Retry or uncheck the campsite.",
            createController.calendarLoadError,
        )
        assertEquals("June family nights", createController.nightsOfferedText)
        assertEquals("3", createController.winnersCountText)
        assertEquals(setOf(night0), createController.selectedNights)

        createController.loadSelectedRange()
        assertEquals(
            "Unable to load campsite availability. Retry or uncheck the campsite.",
            createController.error,
        )
        createController.saveLottery(now = now)
        assertFalse(createController.saved)
        assertEquals(
            "Unable to load campsite availability. Retry or uncheck the campsite.",
            createController.error,
        )

        // Unchecking camp-2 clears the inline calendar error and lets draft actions continue.
        createController.toggleCampsite("camp-2", false)
        assertNull(createController.calendarLoadError)
        assertFalse(createController.hasMissingSelectedCalendars)
        assertEquals(listOf(night0, night1), createController.availableNights)

        // Re-check camp-2 while still failing, then recover via retry.
        createController.toggleCampsite("camp-2", true)
        assertNotNull(createController.calendarLoadError)
        assertTrue(createController.hasMissingSelectedCalendars)

        failCamp2Calendar = false
        createController.retryCampsiteCalendarLoad()
        assertNull(createController.calendarLoadError)
        assertFalse(createController.hasMissingSelectedCalendars)
        assertEquals(listOf(night0), createController.availableNights)

        createController.saveLottery(now = now)
        assertTrue(createController.saved)
        assertEquals(1, lotteryRepository.getLotteriesForCampsite("camp-2").size)

        // 2. Edit mode: initial form remains loaded with edited draft when new campsite load fails.
        val existingId = lotteryRepository.addLottery(
            Lottery(
                ownerId = ownerId,
                campsiteIds = listOf("camp-1"),
                nightsOffered = listOf(night0),
                nightsDescription = "Original note",
                registrationStart = 2_000L,
                registrationEnd = 3_000L,
                bookingStart = 4_000L,
                bookingEnd = 5_000L,
                winnersCount = 1,
            ),
        )
        failCamp2Calendar = true
        val editController = LotteryController(flakyRepo, lotteryRepository, ownerId, "")
        editController.fetchLotteryForEdit(existingId, now = now)
        editController.onNightsOfferedTextChange("Edited note")
        editController.toggleCampsite("camp-2", true)

        assertFalse(editController.loadFailed)
        assertEquals(FormMode.EDIT, editController.mode)
        assertEquals("Edited note", editController.nightsOfferedText)
        assertNotNull(editController.calendarLoadError)
        assertTrue(editController.hasMissingSelectedCalendars)

        editController.saveLottery(now = now)
        assertFalse(editController.saved)
        assertEquals(listOf("camp-1"), lotteryRepository.getLottery(existingId)?.campsiteIds)

        failCamp2Calendar = false
        editController.retryCampsiteCalendarLoad()
        assertNull(editController.calendarLoadError)
        editController.saveLottery(now = now)
        assertTrue(editController.saved)
        assertEquals(listOf("camp-1", "camp-2"), lotteryRepository.getLottery(existingId)?.campsiteIds)
    }

    @Test
    fun toggleCampsite_staleResponse_cannotClearCalendarErrorOrEnableSave() = runTest {
        val now = 1_000L
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Site 2"))
        campsiteRepository.addCampsite(Campsite(id = "camp-3", ownerId = ownerId, name = "Site 3"))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-1", openNights = listOf("2027-06-01")))
        campsiteRepository.saveAvailability(AvailabilityCalendar("camp-2", openNights = listOf("2027-06-01")))

        val slowCamp2 = CompletableDeferred<AvailabilityCalendar>()
        val failingCamp3 = CompletableDeferred<AvailabilityCalendar>()
        val gatedRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar = when (campsiteId) {
                "camp-2" -> slowCamp2.await()
                "camp-3" -> failingCamp3.await()
                else -> campsiteRepository.getAvailability(campsiteId)
            }
        }

        val raceController = LotteryController(gatedRepo, lotteryRepository, ownerId, "camp-1")
        raceController.onRangeStartChange("2027-06-01")
        raceController.onRangeEndChange("2027-06-01")
        raceController.loadSelectedRange()
        raceController.toggleNight("2027-06-01", true)
        raceController.setRegistrationStart(2_000L)
        raceController.setRegistrationEnd(3_000L)
        raceController.setBookingStart(4_000L)
        raceController.setBookingEnd(5_000L)

        // Start slow camp-2 load, then switch to camp-3 whose load fails.
        raceController.toggleCampsite("camp-2", true)
        raceController.toggleCampsite("camp-2", false)
        raceController.toggleCampsite("camp-3", true)
        failingCamp3.completeExceptionally(RuntimeException("Site 3 offline"))

        assertNotNull(raceController.calendarLoadError)
        assertTrue(raceController.hasMissingSelectedCalendars)

        // Late success from outdated camp-2 selection must not clear camp-3's error or enable Save.
        slowCamp2.complete(AvailabilityCalendar("camp-2", openNights = listOf("2027-06-01")))

        assertEquals(
            "Unable to load campsite availability. Retry or uncheck the campsite.",
            raceController.calendarLoadError,
        )
        assertTrue(raceController.hasMissingSelectedCalendars)
        assertFalse("camp-2" in raceController.calendarsByCampsiteId)
        raceController.saveLottery(now = now)
        assertFalse(raceController.saved)

        // Conversely, switching away from failing selection to a valid one must not be clobbered by a late error.
        val lateFailCamp3 = CompletableDeferred<AvailabilityCalendar>()
        val reverseRepo = object : com.example.vipers.domain.repository.CampsiteRepository by campsiteRepository {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar = when (campsiteId) {
                "camp-3" -> lateFailCamp3.await()
                else -> campsiteRepository.getAvailability(campsiteId)
            }
        }
        val reverseController = LotteryController(reverseRepo, lotteryRepository, ownerId, "camp-1")
        reverseController.toggleCampsite("camp-3", true)
        reverseController.toggleCampsite("camp-3", false)
        reverseController.toggleCampsite("camp-2", true)
        lateFailCamp3.completeExceptionally(RuntimeException("Late Site 3 failure"))

        assertEquals(setOf("camp-1", "camp-2"), reverseController.selectedCampsiteIds)
        assertNull(reverseController.calendarLoadError)
        assertFalse(reverseController.hasMissingSelectedCalendars)
        assertFalse(reverseController.loadFailed)
    }

    @Test
    fun toggleCampsite_recalculatesLoadedNights_andKeepsUncheckedUnavailableOutOfSelectAllUntilEligibleAgain() = runTest {
        val n0 = "2026-06-01"
        val n1 = "2026-06-02"
        val n2 = "2026-06-03"
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", ownerId = ownerId, name = "Site 2"))
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-1", openNights = listOf(n0, n1, n2)),
        )
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-2", openNights = listOf(n0, n1, n2), heldNights = listOf(n1, n2)),
        )

        val multiController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "camp-1")
        multiController.onRangeStartChange(n0)
        multiController.onRangeEndChange(n2)
        multiController.loadSelectedRange()
        multiController.toggleNight(n0, true)
        multiController.toggleNight(n1, true)
        // n2 stays unchecked

        // Adding camp-2 immediately removes unchecked n2 while keeping conflicting checked n1 removable.
        multiController.toggleCampsite("camp-2", true)
        assertEquals(listOf(n0, n1), multiController.availableNights)
        assertEquals(setOf(n0, n1), multiController.selectedNights)
        assertEquals(setOf(n1), multiController.conflictingNights)
        multiController.toggleNight(n2, true)
        assertFalse(n2 in multiController.selectedNights)

        // Unchecking n1 clears conflict and removes n1 from availableNights; Select All picks only eligible n0.
        multiController.toggleNight(n1, false)
        assertEquals(listOf(n0), multiController.availableNights)
        assertTrue(multiController.conflictingNights.isEmpty())

        multiController.toggleSelectAll() // Deselect all
        assertTrue(multiController.selectedNights.isEmpty())
        multiController.toggleSelectAll() // Select all
        assertEquals(setOf(n0), multiController.selectedNights)

        // Unchecking camp-2 makes n1 and n2 eligible again for display and Select All.
        multiController.toggleCampsite("camp-2", false)
        assertEquals(listOf(n0, n1, n2), multiController.availableNights)
        assertEquals(setOf(n0), multiController.selectedNights)
        multiController.toggleSelectAll()
        assertEquals(setOf(n0, n1, n2), multiController.selectedNights)
    }

    @Test
    fun editMode_previouslyOfferedUnavailableNight_preservedOnUnrelatedEdit_butExcludedFromSelectAllOnceUnchecked() = runTest {
        val now = 1_000L
        val n0 = "2027-06-01"
        val n1 = "2027-06-02"
        val n2 = "2027-06-03"
        campsiteRepository.addCampsite(Campsite(id = "camp-1", ownerId = ownerId, name = "Site 1"))
        campsiteRepository.saveAvailability(
            AvailabilityCalendar("camp-1", openNights = listOf(n0, n1, n2), heldNights = listOf(n1)),
        )
        val lotteryId = lotteryRepository.addLottery(
            Lottery(
                ownerId = ownerId,
                campsiteIds = listOf("camp-1"),
                nightsOffered = listOf(n0, n1),
                registrationStart = 2_000L,
                registrationEnd = 3_000L,
                bookingStart = 4_000L,
                bookingEnd = 5_000L,
                winnersCount = 1,
            ),
        )

        // Unrelated edit preserves previously offered unavailable night n1 without conflict.
        val preserveController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "")
        preserveController.fetchLotteryForEdit(lotteryId, now = now)
        assertTrue(preserveController.conflictingNights.isEmpty())
        assertEquals(listOf(n0, n1), preserveController.availableNights)
        preserveController.onWinnersCountChange("2")
        preserveController.saveLottery(now = now)
        assertTrue(preserveController.saved)
        assertEquals(listOf(n0, n1), lotteryRepository.getLottery(lotteryId)?.nightsOffered)

        // Once unchecked, n1 stays out of Select All because it is held at camp-1.
        val uncheckController = LotteryController(campsiteRepository, lotteryRepository, ownerId, "")
        uncheckController.fetchLotteryForEdit(lotteryId, now = now)
        uncheckController.onRangeStartChange(n0)
        uncheckController.onRangeEndChange(n2)
        uncheckController.loadSelectedRange()
        assertEquals(listOf(n0, n1, n2), uncheckController.availableNights)

        uncheckController.toggleNight(n1, false)
        assertEquals(listOf(n0, n2), uncheckController.availableNights)
        uncheckController.toggleSelectAll() // Select remaining eligible nights
        assertEquals(setOf(n0, n2), uncheckController.selectedNights)
        uncheckController.toggleSelectAll() // Deselect all
        assertTrue(uncheckController.selectedNights.isEmpty())
        uncheckController.toggleSelectAll() // Select all again
        assertEquals(setOf(n0, n2), uncheckController.selectedNights)
        assertFalse(n1 in uncheckController.selectedNights)
    }
}
