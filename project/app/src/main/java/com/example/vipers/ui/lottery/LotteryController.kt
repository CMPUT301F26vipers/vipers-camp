/*
 * LotteryController.kt
 * The controller (a ViewModel) handling the creation and editing logic and date availability mapping for a lottery.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.model.validateLottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.UpdateLotteryResult
import com.example.vipers.ui.campsites.FormMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel controller for managing the creation and editing of a lottery for a campsite.
 *
 * @param campsiteRepository Repository to access campsite details and availability.
 * @param lotteryRepository Repository to store and update lotteries.
 * @param ownerId Device ID of the current user.
 * @param campsiteId ID of the campsite for which the lottery is created or edited.
 */
class LotteryController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val ownerId: String,
    private val campsiteId: String,
) : ViewModel() {

    /** Whether the form is creating a new lottery or editing an existing one (US 02.04.06). */
    var mode by mutableStateOf(FormMode.CREATE)
        private set

    /** The saved lottery being edited, or null when creating (US 02.04.06). */
    var loadedLottery by mutableStateOf<Lottery?>(null)
        private set

    /** True if the lottery to edit is not owned by this owner or does not exist (US 02.04.06). */
    var notAllowed by mutableStateOf(false)
        private set

    /** True if loading the lottery or campsite failed (US 02.04.06). */
    var loadFailed by mutableStateOf(false)
        private set

    /** True if the loaded lottery's stored status is no longer UPCOMING or OPEN (US 02.04.06). */
    var isReadOnly by mutableStateOf(false)
        private set

    /** User-facing explanation for why editing is disabled when [isReadOnly] is true (US 02.04.06). */
    var readOnlyReason by mutableStateOf<String?>(null)
        private set

    var campsite by mutableStateOf<Campsite?>(null)
        private set

    var availability by mutableStateOf<AvailabilityCalendar?>(null)
        private set
    var availableNights by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedNights by mutableStateOf(setOf<String>())
        private set

    var rangeStart by mutableStateOf("")
        private set
    var rangeEnd by mutableStateOf("")
        private set

    var nightsOfferedText by mutableStateOf("")
        private set

    var registrationStartMillis by mutableStateOf<Long?>(null)
        private set
    var registrationEndMillis by mutableStateOf<Long?>(null)
        private set

    var bookingStartMillis by mutableStateOf<Long?>(null)
        private set
    var bookingEndMillis by mutableStateOf<Long?>(null)
        private set

    var winnersCountText by mutableStateOf("1")
        private set

    var waitlistCapacityText by mutableStateOf("")
        private set
    var waitlistCapacityError by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadCampsiteData()
    }

    /** Helper for strict date parsing guaranteeing complete input consumption. */
    fun parseStrictDate(text: String, pattern: String = "yyyy-MM-dd"): Date? {
        if (text.isBlank()) return null
        val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val pos = ParsePosition(0)
        val date = sdf.parse(text, pos)
        if (date == null || pos.index != text.length) return null
        return date
    }

    /** Loads the campsite details and current availability calendar in the background. */
    fun loadCampsiteData() {
        viewModelScope.launch { fetchCampsiteData() }
    }

    /** Loads the campsite details and current availability calendar. Unit tests can call it directly. */
    suspend fun fetchCampsiteData() {
        if (campsiteId.isBlank()) {
            loading = false
            return
        }
        loading = true
        error = null
        try {
            val result = campsiteRepository.getCampsite(campsiteId)
            if (result == null || result.ownerId != ownerId) {
                error = "Campsite not found or you do not own it."
            } else {
                campsite = result
                val calendar = campsiteRepository.getAvailability(campsiteId)
                availability = calendar
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Unable to load campsite availability."
        } finally {
            loading = false
        }
    }

    /** Loads an existing lottery for editing in the background (US 02.04.06). */
    fun loadForEdit(lotteryId: String) {
        viewModelScope.launch { fetchLotteryForEdit(lotteryId) }
    }

    /**
     * Loads the existing lottery with [lotteryId] for editing (US 02.04.06).
     *
     * Validates owner identity and stored lottery status. If valid, switches to [FormMode.EDIT]
     * and pre-fills the form fields. If status is CLOSED, DRAWN, or REMOVED, sets [isReadOnly] with
     * a clear user-facing explanation in [readOnlyReason].
     */
    suspend fun fetchLotteryForEdit(
        lotteryId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        loading = true
        error = null
        notAllowed = false
        loadFailed = false
        isReadOnly = false
        readOnlyReason = null

        try {
            val stored = lotteryRepository.getLottery(lotteryId)
            if (stored == null || stored.ownerId != ownerId) {
                notAllowed = true
                error = "Lottery not found or you do not own it."
                return
            }

            loadedLottery = stored
            mode = FormMode.EDIT

            // Pre-fill editable fields
            selectedNights = stored.nightsOffered.toSet()
            availableNights = (availableNights + stored.nightsOffered).distinct().sorted()
            nightsOfferedText = stored.nightsDescription ?: ""
            registrationStartMillis = stored.registrationStart
            registrationEndMillis = stored.registrationEnd
            bookingStartMillis = stored.bookingStart
            bookingEndMillis = stored.bookingEnd
            winnersCountText = stored.winnersCount.toString()
            waitlistCapacityText = stored.waitlistCapacity?.toString() ?: ""
            waitlistCapacityError = null

            // If campsiteId was not set, populate it from stored lottery
            val targetCampsiteId = stored.campsiteIds.firstOrNull() ?: campsiteId
            if (targetCampsiteId.isNotBlank() && campsite == null) {
                campsite = campsiteRepository.getCampsite(targetCampsiteId)
                availability = campsiteRepository.getAvailability(targetCampsiteId)
            }

            // Evaluate read-only state based on stored status (US 02.04.06)
            val blockedReason = stored.editBlockedReason(now)
            if (blockedReason != null) {
                isReadOnly = true
                readOnlyReason = blockedReason
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
            error = "Couldn't load the lottery. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    /** Updates text for nights offered field. */
    fun onNightsOfferedTextChange(text: String) {
        nightsOfferedText = text
        error = null
    }

    /** Updates rangeStart safely using strict parsing. */
    fun onRangeStartChange(date: String) {
        rangeStart = date
        if (rangeEnd.isNotEmpty()) {
            val start = parseStrictDate(date)
            val end = parseStrictDate(rangeEnd)
            if (end != null && start != null && end.before(start)) {
                rangeEnd = ""
            }
        }
    }

    /** Updates rangeEnd date. */
    fun onRangeEndChange(date: String) {
        rangeEnd = date
    }

    /** Sets registration start timestamp. */
    fun setRegistrationStart(millis: Long) {
        registrationStartMillis = millis
        error = null
    }

    /** Sets registration end timestamp. */
    fun setRegistrationEnd(millis: Long) {
        registrationEndMillis = millis
        error = null
    }

    /** Sets booking start timestamp. */
    fun setBookingStart(millis: Long) {
        bookingStartMillis = millis
        error = null
    }

    /** Sets booking end timestamp. */
    fun setBookingEnd(millis: Long) {
        bookingEndMillis = millis
        error = null
    }

    /** Sets winner count string. */
    fun onWinnersCountChange(text: String) {
        winnersCountText = text
        error = null
    }

    /** Sets waitlist capacity limit string and validates input inline. */
    fun onWaitlistCapacityChange(text: String) {
        waitlistCapacityText = text
        error = null
        waitlistCapacityError = validateWaitlistCapacity(text)
    }

    private fun validateWaitlistCapacity(text: String): String? {
        if (text.isBlank()) return null
        val parsed = text.trim().toIntOrNull()
        return if (parsed == null || parsed < 1) {
            "Waitlist limit must be a whole number at least 1"
        } else {
            null
        }
    }

    /** Clears any active error message. */
    fun clearError() {
        error = null
    }

    /** Loads and accumulates open, unheld nights for the selected date range. */
    fun loadSelectedRange() {
        try {
            val start = parseStrictDate(rangeStart)
                ?: throw IllegalArgumentException("Invalid start date")
            val end = parseStrictDate(rangeEnd)
                ?: throw IllegalArgumentException("Invalid end date")

            if (end.before(start)) {
                error = "End date must be on or after the start date."
                return
            }

            val calendar = availability ?: return

            val cursor = Calendar.getInstance().apply { time = start }
            val endCalendar = Calendar.getInstance().apply { time = end }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val newNights = mutableListOf<String>()

            while (!cursor.after(endCalendar)) {
                val night = dateFormat.format(cursor.time)
                if (night in calendar.openNights && night !in calendar.heldNights) {
                    newNights.add(night)
                }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }

            availableNights = (availableNights + newNights).distinct().sorted()
            selectedNights = selectedNights.intersect(availableNights.toSet())

            error = if (newNights.isEmpty()) {
                "No available nights in this date range."
            } else {
                null
            }
        } catch (e: Exception) {
            error = "Please select valid start and end dates."
        }
    }

    /** Toggles the selection state of a specific night. */
    fun toggleNight(night: String, checked: Boolean) {
        if (checked) {
            selectedNights = selectedNights + night
        } else {
            selectedNights = selectedNights - night
        }
        error = null
    }

    /** Toggles between selecting all available nights and clearing selections. */
    fun toggleSelectAll() {
        if (selectedNights.size == availableNights.size) {
            selectedNights = emptySet()
        } else {
            selectedNights = availableNights.toSet()
        }
        error = null
    }

    /** Validates and saves the created or edited lottery asynchronously. */
    fun saveLottery(now: Long? = null) {
        if (saving) return
        saving = true
        error = null

        val currentTime = now ?: System.currentTimeMillis()

        if (mode == FormMode.EDIT && isReadOnly) {
            error = readOnlyReason ?: "This lottery cannot be edited."
            saving = false
            return
        }

        val nightsList = selectedNights.sorted()
        val nightsDesc = nightsOfferedText.takeIf { it.isNotBlank() }

        val winnersCount = winnersCountText.toIntOrNull() ?: 0

        val capacityError = validateWaitlistCapacity(waitlistCapacityText)
        if (capacityError != null) {
            waitlistCapacityError = capacityError
            error = capacityError
            saving = false
            return
        }
        val waitlistCapacity: Int? = waitlistCapacityText.trim().toIntOrNull()

        val proposedLottery: Lottery
        val stored = loadedLottery
        if (mode == FormMode.EDIT && stored != null) {
            proposedLottery = stored.copy(
                nightsOffered = nightsList,
                nightsDescription = nightsDesc,
                registrationStart = registrationStartMillis ?: 0L,
                registrationEnd = registrationEndMillis ?: 0L,
                bookingStart = bookingStartMillis ?: 0L,
                bookingEnd = bookingEndMillis ?: 0L,
                winnersCount = winnersCount,
                waitlistCapacity = waitlistCapacity,
            )
        } else {
            proposedLottery = Lottery(
                ownerId = ownerId,
                campsiteIds = listOf(campsiteId),
                nightsOffered = nightsList,
                nightsDescription = nightsDesc,
                registrationStart = registrationStartMillis ?: 0L,
                registrationEnd = registrationEndMillis ?: 0L,
                bookingStart = bookingStartMillis ?: 0L,
                bookingEnd = bookingEndMillis ?: 0L,
                winnersCount = winnersCount,
                waitlistCapacity = waitlistCapacity,
            )
        }

        val validationErrors = validateLottery(
            lottery = proposedLottery,
            now = currentTime,
            storedLottery = stored,
            currentWaitingCount = stored?.waitingUserIds?.size ?: 0,
        )
        if (validationErrors.isNotEmpty()) {
            val firstError = validationErrors.first()
            error = firstError
            if (mode == FormMode.EDIT && stored != null) {
                val blockedReason = stored.editBlockedReason(currentTime)
                if (blockedReason != null) {
                    isReadOnly = true
                    readOnlyReason = blockedReason
                }
            }
            saving = false
            return
        }

        viewModelScope.launch {
            try {
                if (mode == FormMode.EDIT) {
                    saveEdits(proposedLottery, now = currentTime)
                } else {
                    saveNew(proposedLottery)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't save. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }

    /** Saves a new lottery. */
    suspend fun saveNew(lottery: Lottery) {
        val latestCalendar = campsiteRepository.getAvailability(campsiteId)
        val invalidNights = selectedNights.filter {
            it !in latestCalendar.openNights || it in latestCalendar.heldNights
        }

        if (invalidNights.isNotEmpty()) {
            error = "One or more selected nights are no longer available. Please reload the nights."
            return
        }

        lotteryRepository.addLottery(lottery)
        saved = true
    }

    /**
     * Saves edits to an existing lottery via repository transaction (US 02.04.06).
     *
     * Evaluates edit eligibility against [now] (captured time convention).
     */
    suspend fun saveEdits(
        lottery: Lottery,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        when (val result = lotteryRepository.updateLottery(lottery, callerId = ownerId, now = now, zone = zone)) {
            is UpdateLotteryResult.Success -> {
                saved = true
            }
            is UpdateLotteryResult.ReadOnly -> {
                error = result.reason
                isReadOnly = true
                readOnlyReason = result.reason
            }
            is UpdateLotteryResult.Error -> {
                error = result.message
            }
        }
    }

    companion object {
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    ownerId = app.container.deviceId,
                    campsiteId = campsiteId
                )
            }
        }

        fun editFactory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    ownerId = app.container.deviceId,
                    campsiteId = ""
                )
            }
        }
    }
}
