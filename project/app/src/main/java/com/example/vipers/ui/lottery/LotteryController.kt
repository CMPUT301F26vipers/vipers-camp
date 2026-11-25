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
import com.example.vipers.domain.model.conflictingOfferedNights
import com.example.vipers.domain.model.sharedAvailableNights
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
 * ViewModel controller for managing the creation and editing of a lottery covering one or more of
 * an owner's campsites (US 02.04.01, 02.04.02, 02.04.06).
 *
 * @param campsiteRepository Repository to access campsite details and availability.
 * @param lotteryRepository Repository to store and update lotteries.
 * @param ownerId Device ID of the current user.
 * @param campsiteId ID of the launching campsite when creating a new lottery.
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

    /** Active campsites owned by [ownerId] that can be included in this lottery (US 02.04.02). */
    var ownedCampsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    /** Currently selected campsite IDs for this lottery (US 02.04.02). */
    var selectedCampsiteIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Loaded availability calendars keyed by campsite ID. */
    var calendarsByCampsiteId by mutableStateOf<Map<String, AvailabilityCalendar>>(emptyMap())
        private set

    /** True while availability calendars are loading for newly selected campsites. */
    var loadingCampsiteCalendars by mutableStateOf(false)
        private set

    /** Inline error shown when loading calendars for newly selected campsites fails. */
    var calendarLoadError by mutableStateOf<String?>(null)
        private set

    /** True when any currently selected campsite is missing a loaded availability calendar. */
    val hasMissingSelectedCalendars: Boolean
        get() = orderedSelectedCampsiteIds().any { it !in calendarsByCampsiteId }

    /** Offered nights that are unavailable at one or more selected campsites. */
    var conflictingNights by mutableStateOf<Set<String>>(emptySet())
        private set

    /** User-facing conflict summary when selected nights are unavailable at chosen campsites. */
    var conflictMessage by mutableStateOf<String?>(null)
        private set

    /** Number of stored campsite IDs in Edit mode that are no longer owned/active. */
    var ineligibleStoredCampsiteCount by mutableStateOf(0)
        private set

    /** Whether the owner explicitly confirmed removing ineligible stored campsites on Edit save. */
    var removeIneligibleConfirmed by mutableStateOf(false)
        private set

    /** True when Edit loaded at least one stored campsite that is no longer eligible. */
    val hasIneligibleStoredCampsites: Boolean
        get() = ineligibleStoredCampsiteCount > 0

    /** True while Edit still needs explicit confirmation to drop ineligible stored campsites. */
    val requiresIneligibleRemovalConfirmation: Boolean
        get() = mode == FormMode.EDIT && hasIneligibleStoredCampsites && !removeIneligibleConfirmed

    /** Primary selected/launching campsite, kept in sync for callers expecting a single site. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** Availability calendar for [campsite], kept in sync for callers expecting a single site. */
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

    // Monotonic guard so slow calendar loads for an older campsite selection never overwrite newer state.
    private var selectionVersion = 0L

    // All candidate nights loaded from date ranges (and stored offered nights in Edit) so
    // campsite selection changes can recalculate eligibility when nights become eligible again.
    private var loadedCandidateNights: Set<String> = emptySet()

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

    /** Loads the launching campsite, owner's campsites, and initial availability in the background. */
    fun loadCampsiteData() {
        viewModelScope.launch { fetchCampsiteData() }
    }

    /** Loads launching campsite, active owned campsites, and initial availability. Unit tests call directly. */
    suspend fun fetchCampsiteData() {
        if (campsiteId.isBlank()) {
            loading = false
            return
        }
        val version = ++selectionVersion
        loading = true
        error = null
        loadFailed = false
        loadingCampsiteCalendars = false
        calendarLoadError = null
        loadedCandidateNights = emptySet()
        conflictingNights = emptySet()
        conflictMessage = null
        try {
            val result = campsiteRepository.getCampsite(campsiteId)
            if (selectionVersion != version) return
            if (result == null || result.removed || result.ownerId != ownerId) {
                campsite = null
                availability = null
                ownedCampsites = emptyList()
                selectedCampsiteIds = emptySet()
                calendarsByCampsiteId = emptyMap()
                error = "Campsite not found or you do not own it."
            } else {
                val activeOwned = loadActiveOwnedCampsites()
                val calendar = campsiteRepository.getAvailability(campsiteId)
                if (selectionVersion != version) return
                val mergedOwned = (listOf(result) + activeOwned).distinctBy { it.id }
                ownedCampsites = mergedOwned
                campsite = result
                selectedCampsiteIds = setOf(campsiteId)
                calendarsByCampsiteId = mapOf(campsiteId to calendar)
                availability = calendar
                recomputeNightsAndConflicts()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (selectionVersion == version) {
                campsite = null
                availability = null
                ownedCampsites = emptyList()
                selectedCampsiteIds = emptySet()
                calendarsByCampsiteId = emptyMap()
                loadFailed = true
                error = "Unable to load campsite availability."
            }
        } finally {
            loading = false
        }
    }

    /** Loads an existing lottery for editing in the background (US 02.04.06). */
    fun loadForEdit(lotteryId: String) {
        viewModelScope.launch { fetchLotteryForEdit(lotteryId) }
    }

    /**
     * Loads the existing lottery with [lotteryId] for editing (US 02.04.02, 02.04.06).
     *
     * Validates owner identity, loads the owner's active campsites and their availability
     * calendars, preselects stored eligible campsites while flagging any stored IDs that are no
     * longer owned or active without leaking foreign details, and evaluates read-only state.
     */
    suspend fun fetchLotteryForEdit(
        lotteryId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val version = ++selectionVersion
        loading = true
        error = null
        notAllowed = false
        loadFailed = false
        isReadOnly = false
        readOnlyReason = null
        loadingCampsiteCalendars = false
        calendarLoadError = null
        conflictingNights = emptySet()
        conflictMessage = null
        ineligibleStoredCampsiteCount = 0
        removeIneligibleConfirmed = false

        try {
            val stored = lotteryRepository.getLottery(lotteryId)
            if (selectionVersion != version) return
            if (stored == null || stored.ownerId != ownerId) {
                notAllowed = true
                error = "Lottery not found or you do not own it."
                return
            }

            val activeOwned = loadActiveOwnedCampsites()
            val ownedIds = activeOwned.map { it.id }.toSet()
            val storedIds = stored.campsiteIds.filter { it.isNotBlank() }.distinct()
            val eligibleStoredIds = storedIds.filter { it in ownedIds }
            val ineligibleCount = storedIds.count { it !in ownedIds }

            val loadedCalendars = mutableMapOf<String, AvailabilityCalendar>()
            for (cid in eligibleStoredIds) {
                loadedCalendars[cid] = campsiteRepository.getAvailability(cid)
            }
            if (selectionVersion != version) return

            loadedLottery = stored
            mode = FormMode.EDIT
            ownedCampsites = activeOwned
            selectedCampsiteIds = eligibleStoredIds.toSet()
            calendarsByCampsiteId = loadedCalendars
            ineligibleStoredCampsiteCount = ineligibleCount
            removeIneligibleConfirmed = false

            val primaryId = eligibleStoredIds.firstOrNull()
            campsite = primaryId?.let { id -> activeOwned.firstOrNull { it.id == id } }
            availability = primaryId?.let { loadedCalendars[it] }

            // Pre-fill editable fields
            selectedNights = stored.nightsOffered.toSet()
            loadedCandidateNights = stored.nightsOffered.toSet()
            nightsOfferedText = stored.nightsDescription ?: ""
            registrationStartMillis = stored.registrationStart
            registrationEndMillis = stored.registrationEnd
            bookingStartMillis = stored.bookingStart
            bookingEndMillis = stored.bookingEnd
            winnersCountText = stored.winnersCount.toString()
            waitlistCapacityText = stored.waitlistCapacity?.toString() ?: ""
            waitlistCapacityError = null

            recomputeNightsAndConflicts()

            // Evaluate read-only state based on stored status (US 02.04.06)
            val blockedReason = stored.editBlockedReason(now)
            if (blockedReason != null) {
                isReadOnly = true
                readOnlyReason = blockedReason
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (selectionVersion == version) {
                loadFailed = true
                error = "Couldn't load the lottery. Check your connection and try again."
            }
        } finally {
            loading = false
        }
    }

    /**
     * Toggles [targetCampsiteId] in [selectedCampsiteIds], keeping offered nights visible while
     * loading any missing campsite calendars and recalculating selectable nights and conflicts
     * (US 02.04.02).
     */
    fun toggleCampsite(targetCampsiteId: String, checked: Boolean) {
        if (isReadOnly) return
        if (checked && ownedCampsites.none { it.id == targetCampsiteId }) return

        val version = ++selectionVersion
        val updatedSelection = if (checked) {
            selectedCampsiteIds + targetCampsiteId
        } else {
            selectedCampsiteIds - targetCampsiteId
        }
        selectedCampsiteIds = updatedSelection
        val firstSelectedId = orderedSelectedCampsiteIds().firstOrNull()
        campsite = firstSelectedId?.let { id -> ownedCampsites.firstOrNull { it.id == id } }
        availability = firstSelectedId?.let { calendarsByCampsiteId[it] }
        calendarLoadError = null
        error = null

        val missingIds = orderedSelectedCampsiteIds().filter { it !in calendarsByCampsiteId }
        if (missingIds.isEmpty()) {
            loadingCampsiteCalendars = false
            recomputeNightsAndConflicts()
        } else {
            loadingCampsiteCalendars = true
            conflictingNights = emptySet()
            conflictMessage = null
            recomputeNightsAndConflicts()
            loadCalendarsForSelection(missingIds, version)
        }
    }

    /** Retries loading availability calendars for selected campsites whose calendars are missing. */
    fun retryCampsiteCalendarLoad() {
        if (isReadOnly || loadingCampsiteCalendars) return
        val missingIds = orderedSelectedCampsiteIds().filter { it !in calendarsByCampsiteId }
        if (missingIds.isEmpty()) {
            calendarLoadError = null
            recomputeNightsAndConflicts()
            return
        }
        val version = ++selectionVersion
        loadingCampsiteCalendars = true
        calendarLoadError = null
        error = null
        loadCalendarsForSelection(missingIds, version)
    }

    /** Launches async loading for missing selected campsite calendars tied to [expectedVersion]. */
    fun loadCalendarsForSelection(campsiteIds: Collection<String>, expectedVersion: Long = selectionVersion) {
        viewModelScope.launch { fetchCalendarsForSelection(campsiteIds, expectedVersion) }
    }

    /**
     * Fetches availability calendars for [campsiteIds] and applies them only if the selection has
     * not moved past [expectedVersion]. Failures keep the draft visible with an inline retryable error.
     */
    suspend fun fetchCalendarsForSelection(
        campsiteIds: Collection<String>,
        expectedVersion: Long = selectionVersion,
    ) {
        try {
            val fetched = mutableMapOf<String, AvailabilityCalendar>()
            for (cid in campsiteIds.distinct()) {
                fetched[cid] = campsiteRepository.getAvailability(cid)
            }
            if (selectionVersion != expectedVersion) return

            calendarsByCampsiteId = calendarsByCampsiteId + fetched
            val firstSelectedId = orderedSelectedCampsiteIds().firstOrNull()
            availability = firstSelectedId?.let { calendarsByCampsiteId[it] }
            loadingCampsiteCalendars = false
            calendarLoadError = null
            recomputeNightsAndConflicts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (selectionVersion == expectedVersion) {
                loadingCampsiteCalendars = false
                calendarLoadError = "Unable to load campsite availability. Retry or uncheck the campsite."
            }
        }
    }

    /** Records whether the owner explicitly confirmed removing ineligible stored campsites. */
    fun onConfirmRemoveIneligibleChange(confirmed: Boolean) {
        if (isReadOnly) return
        removeIneligibleConfirmed = confirmed
        error = null
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

    private suspend fun loadActiveOwnedCampsites(): List<Campsite> =
        campsiteRepository.getCampsitesByOwner(ownerId)
            .filter { it.ownerId == ownerId && !it.removed }
            .distinctBy { it.id }

    private fun orderedSelectedCampsiteIds(): List<String> {
        val orderedOwned = ownedCampsites.map { it.id }.filter { it in selectedCampsiteIds }
        val extra = selectedCampsiteIds.filter { it !in orderedOwned.toSet() }
        return (orderedOwned + extra).filter { it.isNotBlank() }.distinct()
    }

    private fun updateConflictState(conflicts: Collection<String>) {
        val sorted = conflicts.distinct().sorted()
        conflictingNights = sorted.toSet()
        conflictMessage = if (sorted.isEmpty()) {
            null
        } else {
            "Unavailable at all selected campsites: ${sorted.joinToString(", ")}"
        }
    }

    private fun eligibleLoadedNights(activeIds: List<String>): List<String> {
        if (activeIds.isEmpty() || activeIds.any { it !in calendarsByCampsiteId }) {
            return emptyList()
        }
        return sharedAvailableNights(activeIds, calendarsByCampsiteId, loadedCandidateNights)
    }

    private fun recomputeNightsAndConflicts() {
        val activeIds = orderedSelectedCampsiteIds()
        val eligible = eligibleLoadedNights(activeIds)
        availableNights = (eligible + selectedNights).distinct().sorted()
        recomputeConflicts()
    }

    private fun recomputeConflicts() {
        val activeIds = orderedSelectedCampsiteIds()
        if (activeIds.isEmpty() || selectedNights.isEmpty()) {
            updateConflictState(emptyList())
            return
        }
        if (activeIds.any { it !in calendarsByCampsiteId }) {
            updateConflictState(emptyList())
            return
        }
        val currentDraft = Lottery(
            ownerId = ownerId,
            campsiteIds = activeIds,
            nightsOffered = selectedNights.sorted(),
        )
        val conflicts = conflictingOfferedNights(
            lottery = currentDraft,
            calendarsByCampsiteId = calendarsByCampsiteId,
            storedLottery = if (mode == FormMode.EDIT) loadedLottery else null,
        )
        updateConflictState(conflicts)
    }

    private suspend fun verifyOwnershipAndActive(campsiteIds: List<String>): String? {
        for (cid in campsiteIds) {
            val current = campsiteRepository.getCampsite(cid)
            if (current == null || current.removed || current.ownerId != ownerId) {
                return "One or more selected campsites are no longer available or you do not own them."
            }
        }
        return null
    }

    /** Loads and accumulates shared open, unheld nights across all selected campsites. */
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

            val activeIds = orderedSelectedCampsiteIds()
            if (activeIds.isEmpty()) {
                error = "Select a campsite"
                return
            }
            if (loadingCampsiteCalendars || activeIds.any { it !in calendarsByCampsiteId }) {
                error = calendarLoadError ?: "Loading campsite availability. Please try again."
                return
            }

            val cursor = Calendar.getInstance().apply { time = start }
            val endCalendar = Calendar.getInstance().apply { time = end }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val candidateNights = mutableListOf<String>()
            while (!cursor.after(endCalendar)) {
                candidateNights.add(dateFormat.format(cursor.time))
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }

            val newNights = sharedAvailableNights(activeIds, calendarsByCampsiteId, candidateNights)
            loadedCandidateNights = loadedCandidateNights + candidateNights
            recomputeNightsAndConflicts()

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
        if (isReadOnly) return
        if (checked) {
            val activeIds = orderedSelectedCampsiteIds()
            if (activeIds.isNotEmpty()) {
                if (loadingCampsiteCalendars || hasMissingSelectedCalendars) return
                if (!activeIds.all { id -> calendarsByCampsiteId[id]?.isFree(night) == true }) return
            }
            selectedNights = selectedNights + night
        } else {
            selectedNights = selectedNights - night
        }
        recomputeNightsAndConflicts()
        error = null
    }

    /** Toggles between selecting all eligible available nights and clearing selections. */
    fun toggleSelectAll() {
        if (isReadOnly) return
        if (loadingCampsiteCalendars || hasMissingSelectedCalendars) return
        val activeIds = orderedSelectedCampsiteIds()
        val eligible = eligibleLoadedNights(activeIds).toSet()
        val allSelected = availableNights.isNotEmpty() && selectedNights.size == availableNights.size
        selectedNights = if (allSelected) emptySet() else selectedNights + eligible
        recomputeNightsAndConflicts()
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

        if (loadingCampsiteCalendars || hasMissingSelectedCalendars) {
            error = calendarLoadError ?: "Loading campsite availability. Please try again."
            saving = false
            return
        }

        if (mode == FormMode.EDIT && requiresIneligibleRemovalConfirmation) {
            error = "Please confirm removing the unavailable campsite(s) from this lottery before saving."
            saving = false
            return
        }

        val proposedCampsiteIds = orderedSelectedCampsiteIds()
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
                campsiteIds = proposedCampsiteIds,
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
                campsiteIds = proposedCampsiteIds,
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

    /** Saves a new lottery after rechecking ownership, active status, and all campsite/night pairs. */
    suspend fun saveNew(lottery: Lottery) {
        val submittedIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
        if (submittedIds.isEmpty()) {
            error = "Select a campsite"
            return
        }
        val ownershipError = verifyOwnershipAndActive(submittedIds)
        if (ownershipError != null) {
            error = ownershipError
            return
        }

        val freshCalendars = submittedIds.associateWith { campsiteRepository.getAvailability(it) }
        calendarsByCampsiteId = calendarsByCampsiteId + freshCalendars
        val normalized = lottery.copy(campsiteIds = submittedIds)
        val conflicts = conflictingOfferedNights(normalized, freshCalendars, storedLottery = null)
        if (conflicts.isNotEmpty()) {
            updateConflictState(conflicts)
            error = "One or more selected nights are no longer available. Please reload the nights."
            return
        }

        lotteryRepository.addLottery(normalized)
        saved = true
    }

    /**
     * Saves edits to an existing lottery via repository transaction (US 02.04.02, 02.04.06).
     *
     * Rechecks ownership and active status of all submitted campsites, validates newly introduced
     * `(campsiteId, night)` pairs against fresh calendars while preserving existing pairs during
     * unrelated edits, and evaluates edit eligibility against [now].
     */
    suspend fun saveEdits(
        lottery: Lottery,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (mode == FormMode.EDIT && requiresIneligibleRemovalConfirmation && loadedLottery?.id == lottery.id) {
            error = "Please confirm removing the unavailable campsite(s) from this lottery before saving."
            return
        }

        val submittedIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
        if (submittedIds.isEmpty()) {
            error = "Select a campsite"
            return
        }
        val ownershipError = verifyOwnershipAndActive(submittedIds)
        if (ownershipError != null) {
            error = ownershipError
            return
        }

        val normalized = lottery.copy(campsiteIds = submittedIds)
        val baseline = loadedLottery ?: lotteryRepository.getLottery(normalized.id)
        if (baseline != null) {
            val storedIds = baseline.campsiteIds.filter { it.isNotBlank() }.toSet()
            val storedNights = baseline.nightsOffered.toSet()
            val hasAddedNight = normalized.nightsOffered.any { it !in storedNights }
            val idsToCheck = submittedIds.filter { cid -> cid !in storedIds || hasAddedNight }
            if (idsToCheck.isNotEmpty()) {
                val freshCalendars = idsToCheck.associateWith { campsiteRepository.getAvailability(it) }
                calendarsByCampsiteId = calendarsByCampsiteId + freshCalendars
                val conflicts = conflictingOfferedNights(normalized, freshCalendars, storedLottery = baseline)
                if (conflicts.isNotEmpty()) {
                    updateConflictState(conflicts)
                    error = "One or more selected nights are no longer available. Please reload the nights."
                    return
                }
            }
        }

        when (val result = lotteryRepository.updateLottery(normalized, callerId = ownerId, now = now, zone = zone)) {
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
