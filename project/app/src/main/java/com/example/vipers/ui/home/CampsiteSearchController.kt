/*
 * CampsiteSearchController.kt
 * The controller (a ViewModel) behind the Home search (US 01.01.01). It loads the campsites once
 * and filters them by name on the device as the user types, as the CRC card describes. It also
 * filters them by region, date range, and guests (US 01.01.02), tracks result counts and loading
 * state (US 01.01.03), and marks which campsites have a lottery open for registration, for the
 * result cards (US 01.01.04). Filtered results are scored and ranked by match score (W-01).
 * When the entrant scans a campsite QR code it works out which campsite to open, or why it
 * can't (US 01.12.01).
 * Known issues: Finding the open lotteries and availability takes Firestore queries per campsite,
 * which is fine for now but slow with many campsites. If the lotteries or availability can't be
 * loaded, the campsites still show.
 */
package com.example.vipers.ui.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Turns the entrant's search text into the list of campsites shown on Home.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones.
 */
class CampsiteSearchController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
) : ViewModel() {

    /** What the user typed in the search bar. */
    var searchText by mutableStateOf("")
        private set

    /** The region to filter by, or null for all regions (US 01.01.02). */
    var selectedRegion by mutableStateOf<Region?>(null)
        private set

    /** The first night ("yyyy-MM-dd") of the stay to filter by, or null (US 01.01.02). */
    var filterStartDate by mutableStateOf<String?>(null)
        private set

    /** The last night ("yyyy-MM-dd") of the stay to filter by, or null (US 01.01.02). */
    var filterEndDate by mutableStateOf<String?>(null)
        private set

    /** The minimum guests required, or null for any group size (US 01.01.02). */
    var filterMinGuests by mutableStateOf<Int?>(null)
        private set

    /** Optional amenities that improve a campsite's score. */
    var preferredTags by mutableStateOf(emptySet<String>())
        private set

    /** Tags offered by the loaded campsites, with equivalent labels shown only once. */
    val availableTags by derivedStateOf {
        allCampsites.flatMap { it.tags }.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    /** Whether Home sorts filtered results by match percentage. */
    var sortByBestMatch by mutableStateOf(true)
        private set

    /** Selects best match or alphabetical order for the current filters. */
    fun changeSort(bestMatch: Boolean) {
        sortByBestMatch = bestMatch
    }

    /** True when at least one filter or preferred tag is set. */
    val hasActiveFilters: Boolean
        get() = preferredTags.isNotEmpty() || selectedRegion != null ||
                !filterStartDate.isNullOrBlank() ||
                !filterEndDate.isNullOrBlank() ||
                (filterMinGuests != null && filterMinGuests!! > 0)

    /** True while the campsites are loading. */
    var isLoading by mutableStateOf(false)
        private set

    /** True if the last load failed, e.g. because the phone is offline. */
    var loadFailed by mutableStateOf(false)
        private set

    // every campsite that isn't removed; the repository already skips removed ones
    private var allCampsites by mutableStateOf(emptyList<Campsite>())

    // IDs of the campsites that have a lottery open for registration right now
    private var openLotteryCampsiteIds by mutableStateOf(emptySet<String>())

    // Availability calendars by campsite ID for date filtering (US 01.01.02)
    private var availabilityByCampsiteId by mutableStateOf<Map<String, AvailabilityCalendar>>(emptyMap())

    // Reference day used when scoring how soon upcoming free nights start (W-01)
    private var referenceDate by mutableStateOf(LocalDate.now())

    /** A visible campsite and the whole percentage displayed on its Home card. */
    internal data class Result(val campsite: Campsite, val score: Int?)

    /** Filters and scores each campsite once when the search inputs change. */
    internal val rankedResults by derivedStateOf { findResults() }

    /** The campsites in the order shown on Home. */
    val results: List<Campsite>
        get() = rankedResults.map { it.campsite }

    private fun findResults(): List<Result> {
        val first = filterStartDate ?: filterEndDate
        val last = filterEndDate ?: filterStartDate
        val nights = if (first != null && last != null) {
            try {
                AvailabilityCalendar.requestedNightCount(LocalDate.parse(first), LocalDate.parse(last))
                    ?: return emptyList()
            } catch (_: DateTimeParseException) {
                return emptyList()
            }
        } else 0

        val matches = mutableListOf<Result>()
        for (site in allCampsites) {
            if (!site.matchesSearch(searchText) || !site.matchesRegion(selectedRegion) ||
                !site.matchesGuests(filterMinGuests)) continue
            val calendar = availabilityByCampsiteId[site.id]
            val free = if (first != null && last != null) {
                calendar?.freeNights(first, last).orEmpty().size
            } else 0
            if (nights > 0 && free == 0) continue
            val score = if (hasActiveFilters) {
                val proximity = calendar?.dateProximity(first, last, referenceDate) ?: 0.0
                site.matchScore(free, nights, preferredTags, proximity)
            } else null
            matches.add(Result(site, score))
        }
        return if (hasActiveFilters && sortByBestMatch) {
            matches.sortedWith(compareByDescending<Result> { it.score }
                .thenByDescending { it.campsite.averageRating() }.thenBy { it.campsite.name.lowercase() })
        } else matches.sortedBy { it.campsite.name.lowercase() }
    }

    /**
     * Formats the count of matching campsites for display on Home, e.g. "4 campsites" or "1 campsite" (US 01.01.03).
     */
    val resultCountText: String
        get() = if (rankedResults.size == 1) "1 campsite" else "${rankedResults.size} campsites"

    /** Called as the user types; the [results] update right away. */
    fun onSearchTextChange(text: String) {
        searchText = text
        clearScanMessage()
    }

    /**
     * Sets or updates the active filters (US 01.01.02). Passing null or blank leaves that filter inactive.
     */
    fun applyFilters(
        region: Region? = null,
        startDate: String? = null,
        endDate: String? = null,
        minGuests: Int? = null,
        preferredTags: Set<String> = emptySet(),
    ) {
        this.preferredTags = preferredTags.toSet()
        selectedRegion = region
        filterStartDate = startDate?.trim()?.ifBlank { null }
        filterEndDate = endDate?.trim()?.ifBlank { null }
        filterMinGuests = minGuests?.takeIf { it > 0 }
        if (!hasActiveFilters) sortByBestMatch = true
        clearScanMessage()
    }

    /** Clears all active filters (US 01.01.02), restoring the full list of campsites. */
    fun clearFilters() {
        applyFilters()
    }

    /** Updates the region filter alone. */
    fun onRegionChange(region: Region?) {
        selectedRegion = region
    }

    /** Updates the date range filter alone. */
    fun onDateRangeChange(startDate: String?, endDate: String?) {
        filterStartDate = startDate?.trim()?.ifBlank { null }
        filterEndDate = endDate?.trim()?.ifBlank { null }
    }

    /** Updates the minimum guests filter alone. */
    fun onMinGuestsChange(minGuests: Int?) {
        filterMinGuests = minGuests?.takeIf { it > 0 }
    }

    /**
     * True if [campsite] has a lottery whose registration is open, so entrants can still join
     * (the "Lottery open" badge on its card).
     */
    fun hasOpenLottery(campsite: Campsite): Boolean = campsite.id in openLotteryCampsiteIds

    /**
     * Why the last Scan QR didn't open a campsite (US 01.12.01), shown in red under the search
     * bar, or null when there's nothing to say.
     */
    var scanMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Handles what the QR scanner returned (US 01.12.01). Returns the campsite ID to open when the
     * code is one of our campsite links; Campsite Details then shows "Campsite not found." if that
     * campsite was removed. Otherwise returns null and sets [scanMessage], except when the user
     * just closed the scanner.
     */
    fun onQrScanned(result: QrScanResult): String? {
        scanMessage = null
        return when (result) {
            is QrScanResult.Scanned -> {
                val campsiteId = Campsite.idFromQrLink(result.text)
                if (campsiteId == null) scanMessage = "Not a valid campsite code"
                campsiteId
            }
            QrScanResult.Cancelled -> null
            QrScanResult.NoCameraPermission -> {
                scanMessage = "Scan QR needs the camera. Allow camera access in your phone's " +
                    "Settings, then try again."
                null
            }
            QrScanResult.Failed -> {
                scanMessage = "Couldn't open the scanner. Try again."
                null
            }
        }
    }

    /**
     * Clears [scanMessage] when a new scan starts or the entrant moves on (searches, filters or
     * opens a campsite), so an old error doesn't stay on Home.
     */
    fun clearScanMessage() {
        scanMessage = null
    }

    /**
     * Loads the campsites in the background. Home calls this each time it opens. The load runs in
     * [viewModelScope], so it keeps going if the user switches tabs before it finishes.
     */
    fun loadCampsites() {
        viewModelScope.launch { fetchCampsites() }
    }

    /**
     * Gets the campsites from the repository, finds which ones have a lottery open at time [now]
     * (milliseconds since 1970), loads availability for date filtering and date-proximity scoring
     * relative to [today], and sets [isLoading] and [loadFailed]. Only a failed campsite load sets
     * [loadFailed]. [loadCampsites] runs this in the background; unit tests call it directly and
     * pass their own [now] and [today].
     */
    suspend fun fetchCampsites(
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now(),
    ) {
        isLoading = true
        try {
            val campsites = campsiteRepository.getAllCampsites()
            val withOpenLottery = findOpenLotteries(campsites, now)
            val availability = loadAvailability(campsites)
            allCampsites = campsites
            openLotteryCampsiteIds = withOpenLottery
            availabilityByCampsiteId = availability
            referenceDate = today
            loadFailed = false
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    // IDs of the campsites that have a lottery open at time now. If the lotteries can't be loaded
    // (e.g. offline), returns none, so the campsites still show, just without the badge.
    private suspend fun findOpenLotteries(campsites: List<Campsite>, now: Long): Set<String> {
        return try {
            campsites
                .filter { campsite ->
                    lotteryRepository.getLotteriesForCampsite(campsite.id)
                        .any { it.status(now) == LotteryStatus.OPEN }
                }
                .map { it.id }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    // Availability calendars for each campsite. If loading fails for a campsite, an empty
    // calendar is used so the campsite still displays when not filtering by date.
    private suspend fun loadAvailability(campsites: List<Campsite>): Map<String, AvailabilityCalendar> {
        return try {
            campsites.associate { campsite ->
                campsite.id to try {
                    campsiteRepository.getAvailability(campsite.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AvailabilityCalendar(campsiteId = campsite.id)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        /** Makes the controller with the app's real repositories (from CampApp's container). */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteSearchController(
                    app.container.campsiteRepository,
                    app.container.lotteryRepository,
                )
            }
        }
    }
}
