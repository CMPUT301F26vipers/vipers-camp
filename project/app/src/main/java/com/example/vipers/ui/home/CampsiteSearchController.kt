/*
 * CampsiteSearchController.kt
 * The controller (a ViewModel) behind the Home search (US 01.01.01). It loads the campsites once
 * and filters them by name on the device as the user types, as the CRC card describes. It also
 * marks which campsites have a lottery open for registration, for the result cards (US 01.01.04).
 * Known issues: the region, date and guest filters (01.01.02) and the result count and loading
 * indicator (01.01.03) are added by those stories. Finding the open lotteries takes one Firestore
 * query per campsite, which is fine for now but slow with many campsites.
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Turns the entrant's search text into the list of campsites shown on Home.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones, and [now] (milliseconds since 1970) so tests can pick the time.
 */
class CampsiteSearchController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** What the user typed in the search bar. */
    var searchText by mutableStateOf("")
        private set

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

    /** The campsites whose name starts with [searchText], sorted by name. */
    val results: List<Campsite>
        get() = allCampsites
            .filter { it.matchesSearch(searchText) }
            .sortedBy { it.name.lowercase() }

    /** Called as the user types; the [results] update right away. */
    fun onSearchTextChange(text: String) {
        searchText = text
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
     * Gets the campsites from the repository, finds which ones have an open lottery, and sets
     * [isLoading] and [loadFailed]. [loadCampsites] runs this in the background; unit tests call
     * it directly.
     */
    suspend fun fetchCampsites() {
        isLoading = true
        try {
            val campsites = campsiteRepository.getAllCampsites()
            val time = now()
            val withOpenLottery = campsites.filter { campsite ->
                lotteryRepository.getLotteriesForCampsite(campsite.id)
                    .any { it.status(time) == LotteryStatus.OPEN }
            }
            allCampsites = campsites
            openLotteryCampsiteIds = withOpenLottery.map { it.id }.toSet()
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
