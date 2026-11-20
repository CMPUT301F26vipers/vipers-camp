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
}
