/*
 * MyCampsitesController.kt
 * The controller (a ViewModel) for the My Campsites screen (mockup O01).
 * Fetches and manages the state of the user's listed campsites and their real lottery statuses.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Controller for the My Campsites screen (mockup O01).
 * Fetches and holds the owner's listed campsites and their current lottery status.
 *
 * @param campsiteRepository Repository holding all campsites.
 * @param lotteryRepository Repository holding lotteries for status calculation.
 * @param ownerId The current user's ID; only their campsites are loaded.
 */
class MyCampsitesController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository?,
    val ownerId: String,
) : ViewModel() {

    /** Secondary constructor without lotteryRepository for backwards compatibility. */
    constructor(
        campsiteRepository: CampsiteRepository,
        ownerId: String,
    ) : this(campsiteRepository, null, ownerId)

    /** The owner's listed campsites. */
    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    /** Map of campsite ID to formatted lottery status label (Mockup O01). */
    var lotteryStatuses by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** True while campsites are loading. */
    var loading by mutableStateOf(true)
        private set

    /** Error message to show if loading failed. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Loads the owner's campsites in the background. */
    fun loadCampsites() {
        viewModelScope.launch { fetchCampsites() }
    }

    /**
     * Fetches the owner's campsites and calculates the current lottery status for each
     * (US 02.03.01, Mockup O01).
     *
     * @param now Current timestamp in milliseconds since 1970 for lottery status evaluation.
     */
    suspend fun fetchCampsites(now: Long = System.currentTimeMillis()) {
        loading = true
        error = null
        try {
            val list = campsiteRepository.getCampsitesByOwner(ownerId)
            campsites = list
            val statuses = mutableMapOf<String, String>()
            if (lotteryRepository != null) {
                for (site in list) {
                    val lotteries = lotteryRepository.getLotteriesForCampsite(site.id)
                        .filter { !it.removed }
                    statuses[site.id] = calculateLotteryStatus(lotteries, now)
                }
            }
            lotteryStatuses = statuses
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't load campsites. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    companion object {
        /**
         * Determines the overall lottery status label for a campsite given its [lotteries]
         * at time [now] (Mockup O01).
         */
        fun calculateLotteryStatus(
            lotteries: List<Lottery>,
            now: Long = System.currentTimeMillis(),
        ): String {
            val active = lotteries.filter { !it.removed }
            if (active.isEmpty()) return "None"
            val open = active.firstOrNull { it.status(now) == LotteryStatus.OPEN }
            if (open != null) return formatStatus(LotteryStatus.OPEN)
            val drawn = active.firstOrNull { it.status(now) == LotteryStatus.DRAWN }
            if (drawn != null) return formatStatus(LotteryStatus.DRAWN)
            val upcoming = active.firstOrNull { it.status(now) == LotteryStatus.UPCOMING }
            if (upcoming != null) return formatStatus(LotteryStatus.UPCOMING)
            return formatStatus(LotteryStatus.CLOSED)
        }

        /** Formats a [LotteryStatus] into its display label for mockup O01 and O04. */
        fun formatStatus(status: LotteryStatus): String = when (status) {
            LotteryStatus.OPEN -> "Open"
            LotteryStatus.UPCOMING -> "Upcoming"
            LotteryStatus.DRAWN -> "Booking period"
            LotteryStatus.CLOSED -> "Closed"
        }

        /** Builds a [MyCampsitesController] with real app repositories and device ID. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CampApp
                val container = app.container
                MyCampsitesController(
                    campsiteRepository = container.campsiteRepository,
                    lotteryRepository = container.lotteryRepository,
                    ownerId = container.deviceId,
                )
            }
        }
    }
}
