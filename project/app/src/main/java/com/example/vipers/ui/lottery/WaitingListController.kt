/*
 * WaitingListController.kt
 * The controller (a ViewModel) behind the owner's Waiting List screen (mockup O11, US 02.09.01).
 * It loads one lottery, then listens to its entries so the list updates live as entrants join,
 * leave or get their draw result. The CRC cards give this to LotteryController; it has its own
 * controller here because every screen in the app has one.
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
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shows the entrants of one lottery, oldest first, and keeps the list up to date.
 *
 * @param lotteryRepository Loads the lottery and listens to its entries.
 * @param profileRepository Looks up each entrant's current name.
 * @param lotteryId The lottery whose waiting list is shown.
 */
class WaitingListController(
    private val lotteryRepository: LotteryRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryId: String,
) : ViewModel() {

    /** The lottery, for its limit and draw time; null until loaded or if it doesn't exist. */
    var lottery by mutableStateOf<Lottery?>(null)
        private set

    /** The entrants to show, oldest first. Removed entries are left out. */
    var entries by mutableStateOf<List<LotteryEntry>>(emptyList())
        private set

    /** Each entrant's current name by user ID ("Deleted user" if their profile is gone). */
    var names by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** True until the lottery and the first list of entries have loaded. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if loading failed, or null. */
    var error by mutableStateOf<String?>(null)
        private set

    /** True once the draw has run, so each entrant's result is shown. */
    val isDrawn: Boolean
        get() = lottery?.drawnAt != null

    /** The total, with the limit if one is set, e.g. "3 of 10 joined" or "3 joined". */
    val countLabel: String
        get() {
            val capacity = lottery?.waitlistCapacity
            return if (capacity != null) "${entries.size} of $capacity joined" else "${entries.size} joined"
        }

    init {
        viewModelScope.launch { watch() }
    }

    /**
     * Loads the lottery, then updates [entries] and [names] every time its entries change.
     * Runs until the screen closes. Unit tests can call it directly.
     */
    suspend fun watch() {
        loading = true
        error = null
        try {
            lottery = lotteryRepository.getLottery(lotteryId)
            if (lottery == null) {
                error = "Lottery not found."
                loading = false
                return
            }
            lotteryRepository.observeEntries(lotteryId).collect { all ->
                val shown = all.filter { it.status != EntryStatus.REMOVED }.sortedBy { it.joinedAt }
                names = names + lookUpNewNames(shown)
                entries = shown
                loading = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't load the waiting list. Please try again."
            loading = false
        }
    }

    /**
     * Looks up the names of entrants in [shown] whose names aren't known yet. A lookup that fails
     * shows "Unknown" for that entrant, so one bad lookup doesn't stop the live list.
     */
    private suspend fun lookUpNewNames(shown: List<LotteryEntry>): Map<String, String> =
        shown.map { it.userId }
            .filter { it !in names }
            .distinct()
            .associateWith { userId ->
                try {
                    profileRepository.getDisplayName(userId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Unknown"
                }
            }

    companion object {
        /** Makes a [WaitingListController] for [lotteryId] with the app's repositories. */
        fun factory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                WaitingListController(
                    lotteryRepository = app.container.lotteryRepository,
                    profileRepository = app.container.profileRepository,
                    lotteryId = lotteryId,
                )
            }
        }
    }
}
