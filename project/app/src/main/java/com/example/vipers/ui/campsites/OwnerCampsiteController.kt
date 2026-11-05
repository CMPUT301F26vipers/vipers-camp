/*
 * OwnerCampsiteController.kt
 * The controller (a ViewModel) behind the owner's view of one campsite (mockup O04, US 02.01.04).
 * It loads the campsite with all its saved details and its lotteries (US 02.04.01), with how many
 * entrants joined or reserved each one.
 * Known issues: this campsite's own reservations are not loaded here. The screen's Reservations
 * button opens the list for all of the current user's campsites (US 02.07.01).
 */
package com.example.vipers.ui.campsites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one of the owner's campsites and its lotteries for the My Campsite screen.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones, and the [campsiteId] of the campsite to show.
 */
class OwnerCampsiteController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val campsiteId: String,
) : ViewModel() {

    /** The campsite with its latest saved details, or null until it loads or if it wasn't found. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** The campsite's lotteries. */
    var lotteries by mutableStateOf<List<Lottery>>(emptyList())
        private set

    /** Each lottery's entry count as text, e.g. "36 joined" or "3 reserved", by lottery ID. */
    var entrySummaries by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** True while the campsite is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * Loads the campsite and its lotteries in the background. The screen calls this each time it
     * comes back into view, so it always shows the latest saved values (e.g. after Edit).
     */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the campsite, its lotteries and their entry counts from the repositories, and sets
     * [loading] and [error].
     * [loadData] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            campsite = campsiteRepository.getCampsite(campsiteId)
            val loaded = lotteryRepository.getLotteriesForCampsite(campsiteId)
            entrySummaries = loaded.associate { lottery ->
                lottery.id to entrySummary(lotteryRepository.getEntries(lottery.id))
            }
            lotteries = loaded
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Unable to load details."
        } finally {
            loading = false
        }
    }

    // like mockup O04: "36 joined" while people sign up, "3 reserved" once winners start reserving
    private fun entrySummary(entries: List<LotteryEntry>): String {
        val reserved = entries.count { it.status == EntryStatus.RESERVED }
        if (reserved > 0) return "$reserved reserved"
        return "${entries.count { it.status != EntryStatus.REMOVED }} joined"
    }

    companion object {
        /** Makes the controller for [campsiteId] with the app's real repositories. */
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                OwnerCampsiteController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    campsiteId = campsiteId
                )
            }
        }
    }
}
