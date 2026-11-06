/*
 * CampsiteSearchController.kt
 * The controller (a ViewModel) behind the Home search (US 01.01.01). It loads the campsites once
 * and filters them by name on the device as the user types, as the CRC card describes. It also
 * filters them by region, date range, and guests (US 01.01.02), and marks which campsites have a
 * lottery open for registration, for the result cards (US 01.01.04).
 * Known issues: the result count and loading indicator (01.01.03) are added by that story.
 * Finding the open lotteries and availability takes Firestore queries per campsite, which is
 * fine for now but slow with many campsites. If the lotteries or availability can't be loaded,
 * the campsites still show.
 */
package com.example.vipers.ui.home

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

    /** True if any filter is set (region, date range, or guest count > 0). */
    val hasActiveFilters: Boolean
        get() = selectedRegion != null ||
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

    /**
     * The campsites matching [searchText] and any active region, date, or guest filters,
     * sorted by name.
     */
    val results: List<Campsite>
        get() {
            val start = filterStartDate?.trim()?.takeIf { it.isNotEmpty() }
            val end = filterEndDate?.trim()?.takeIf { it.isNotEmpty() }
            val hasDateFilter = start != null || end != null
            val effectiveStart = start ?: end
            val effectiveEnd = end ?: start

            return allCampsites
                .filter { it.matchesSearch(searchText) }
                .filter { it.matchesRegion(selectedRegion) }
                .filter { it.matchesGuests(filterMinGuests) }
                .filter { campsite ->
                    if (!hasDateFilter) {
                        true
                    } else {
                        val calendar = availabilityByCampsiteId[campsite.id]
                        try {
                            calendar?.freeNights(effectiveStart!!, effectiveEnd!!).orEmpty().isNotEmpty()
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                .sortedBy { it.name.lowercase() }
        }

    /** Called as the user types; the [results] update right away. */
    fun onSearchTextChange(text: String) {
        searchText = text
    }

    /**
     * Sets or updates the active filters (US 01.01.02). Passing null or blank leaves that filter inactive.
     */
    fun applyFilters(
        region: Region? = null,
        startDate: String? = null,
        endDate: String? = null,
        minGuests: Int? = null,
    ) {
        selectedRegion = region
        filterStartDate = startDate?.trim()?.ifBlank { null }
        filterEndDate = endDate?.trim()?.ifBlank { null }
        filterMinGuests = minGuests?.takeIf { it > 0 }
    }

    /** Clears all active filters (US 01.01.02), restoring the full list of campsites. */
    fun clearFilters() {
        selectedRegion = null
        filterStartDate = null
        filterEndDate = null
        filterMinGuests = null
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
     * Loads the campsites in the background. Home calls this each time it opens. The load runs in
     * [viewModelScope], so it keeps going if the user switches tabs before it finishes.
     */
    fun loadCampsites() {
        viewModelScope.launch { fetchCampsites() }
    }

    /**
     * Gets the campsites from the repository, finds which ones have a lottery open at time [now]
     * (milliseconds since 1970), loads availability for date filtering, and sets [isLoading] and
     * [loadFailed]. Only a failed campsite load sets [loadFailed]. [loadCampsites] runs this in the
     * background; unit tests call it directly and pass their own [now].
     */
    suspend fun fetchCampsites(now: Long = System.currentTimeMillis()) {
        isLoading = true
        try {
            val campsites = campsiteRepository.getAllCampsites()
            val withOpenLottery = findOpenLotteries(campsites, now)
            val availability = loadAvailability(campsites)
            allCampsites = campsites
            openLotteryCampsiteIds = withOpenLottery
            availabilityByCampsiteId = availability
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
