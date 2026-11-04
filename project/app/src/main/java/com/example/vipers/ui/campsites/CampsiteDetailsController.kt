/*
 * CampsiteDetailsController.kt
 * The controller (a ViewModel) handling details fetching and creation triggers for a campsite.
 * Known issues: none.
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
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ViewModel for viewing details of a campsite and its associated lotteries.
 *
 * @param campsiteRepository Repository to fetch campsite metadata.
 * @param lotteryRepository Repository to fetch lotteries associated with this campsite.
 * @param campsiteId ID of the campsite being inspected.
 */
class CampsiteDetailsController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val campsiteId: String,
) : ViewModel() {

    var campsite by mutableStateOf<Campsite?>(null)
        private set
    var lotteries by mutableStateOf<List<Lottery>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadData()
    }

    /** Loads campsite metadata and lotteries asynchronously. */
    fun loadData() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                campsite = campsiteRepository.getCampsite(campsiteId)
                lotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Unable to load details."
            } finally {
                loading = false
            }
        }
    }

    companion object {
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteDetailsController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    campsiteId = campsiteId
                )
            }
        }
    }
}
