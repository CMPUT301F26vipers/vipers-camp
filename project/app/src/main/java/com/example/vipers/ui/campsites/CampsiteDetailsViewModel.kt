/*
 * CampsiteDetailsViewModel.kt
 *
 * Manages the state for viewing a specific campsite's details.
 *
 * Known issues: None.
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
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.launch

/**
 * UI representation of a lottery item displayed on the Campsite Details screen.
 *
 * @property id The document ID of the lottery.
 * @property title Heading for the card (e.g. date range or label).
 * @property subtext Subtitle summarizing entries or reservations.
 * @property status Label indicating stage (Open, Closed, Drawn, Upcoming).
 * @property isOpen Whether the lottery is currently open for registration.
 */
data class LotteryCardUiState(
    val id: String,
    val title: String,
    val subtext: String,
    val status: String,
    val isOpen: Boolean,
)

/**
 * ViewModel managing state for [CampsiteDetailsScreen].
 *
 * Loads campsite details and its associated lotteries, keeping them up-to-date.
 */
class CampsiteDetailsViewModel(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    val campsiteId: String,
) : ViewModel() {

    // Secondary constructor to support callers and tests that pass only (campsiteRepository, campsiteId)
    constructor(campsiteRepository: CampsiteRepository, campsiteId: String) :
        this(campsiteRepository, FakeLotteryRepository(), campsiteId)

    // Nullable because it takes a moment to load from the repository
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    var lotteries by mutableStateOf<List<LotteryCardUiState>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadDetails()
    }

    /**
     * Loads the latest campsite information and lotteries from the repositories.
     * Can be called with a specific timestamp [now] for deterministic testing.
     */
    fun loadDetails(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            isLoading = true
            campsite = campsiteRepository.getCampsite(campsiteId)
            val campsiteLotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
            lotteries = campsiteLotteries.map { lottery ->
                val entries = lotteryRepository.getEntries(lottery.id)
                val reservedCount = entries.count { it.status == EntryStatus.RESERVED }
                val statusEnum = lottery.status(now)
                val statusText = when (statusEnum) {
                    LotteryStatus.OPEN -> "Open"
                    LotteryStatus.CLOSED -> "Closed"
                    LotteryStatus.DRAWN -> "Drawn"
                    LotteryStatus.UPCOMING -> "Upcoming"
                }
                val isOpen = (statusEnum == LotteryStatus.OPEN)
                val subtext = if (reservedCount > 0) {
                    if (reservedCount == 1) "1 reserved" else "$reservedCount reserved"
                } else {
                    "${entries.size} joined"
                }
                val title = when {
                    lottery.nightsOffered.size > 1 -> "${lottery.nightsOffered.first()} to ${lottery.nightsOffered.last()}"
                    lottery.nightsOffered.size == 1 -> lottery.nightsOffered.first()
                    else -> "Lottery ${lottery.id}"
                }
                LotteryCardUiState(
                    id = lottery.id,
                    title = title,
                    subtext = subtext,
                    status = statusText,
                    isOpen = isOpen
                )
            }
            isLoading = false
        }
    }

    /** Reloads campsite details and lotteries to reflect latest saved values. */
    fun refresh() {
        loadDetails()
    }

    companion object {
        // Factory requires the ID passed from the navigation route
        fun provideFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp)
                val container = application.container

                CampsiteDetailsViewModel(
                    campsiteRepository = container.campsiteRepository,
                    lotteryRepository = container.lotteryRepository,
                    campsiteId = campsiteId
                )
            }
        }
    }
}
