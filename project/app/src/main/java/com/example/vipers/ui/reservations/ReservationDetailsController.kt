/*
 * ReservationDetailsController.kt
 * The controller (a ViewModel) behind the entrant's view of their reservation (US 01.05.01).
 * It loads the reservation, its dates, group size, total price, and reservation number.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

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
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one reservation made by [userId] (US 01.05.01).
 * Takes repositories in its constructor so tests can pass fake ones, and the [reservationId]
 * to show. Only loads the reservation if it belongs to [userId] for privacy.
 */
class ReservationDetailsController(
    private val reservationRepository: ReservationRepository,
    private val campsiteRepository: CampsiteRepository? = null,
    val userId: String,
    val reservationId: String,
) : ViewModel() {

    /** The loaded reservation, or null if loading, missing, or belongs to another user. */
    var reservation by mutableStateOf<Reservation?>(null)
        private set

    /** Optional campsite details for extra information (e.g. photo, address). */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** True while the reservation is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Loads the reservation in the background (US 01.05.01). */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Fetches the reservation from the repository and sets [loading] and [error] (US 01.05.01).
     * [loadData] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            val loaded = reservationRepository.getReservation(reservationId)
                ?.takeIf { it.userId == userId }
            reservation = loaded
            if (loaded != null && campsiteRepository != null) {
                campsite = campsiteRepository.getCampsite(loaded.campsiteId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't load the reservation"
        } finally {
            loading = false
        }
    }

    companion object {
        /** Factory for creating [ReservationDetailsController] with application dependencies. */
        fun factory(reservationId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ReservationDetailsController(
                    reservationRepository = app.container.reservationRepository,
                    campsiteRepository = app.container.campsiteRepository,
                    userId = app.container.deviceId,
                    reservationId = reservationId,
                )
            }
        }
    }
}
