/*
 * OwnerReservationDetailsController.kt
 * The controller (a ViewModel) behind the owner's view of one reservation (mockup O15,
 * US 02.07.01). It loads the reservation and the entrant who made it.
 * Known issues: none. Rejecting (US 02.08.01) and messaging the entrant (US 02.12.01) are added
 * by those stories.
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
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one reservation at [ownerId]'s campsites and its entrant (US 02.07.01).
 * Takes the repositories in its constructor so tests can pass fake ones, and the
 * [reservationId] of the reservation to show.
 */
class OwnerReservationDetailsController(
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val ownerId: String,
    private val reservationId: String,
) : ViewModel() {

    /**
     * The reservation, or null until it loads, if it wasn't found, or if it is at another owner's
     * campsite (so entrants' contact details only show to the campsite's owner).
     */
    var reservation by mutableStateOf<Reservation?>(null)
        private set

    /** The entrant who made the reservation, or null if their profile was deleted. */
    var entrant by mutableStateOf<UserProfile?>(null)
        private set

    /** True while the reservation is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Loads the reservation and its entrant in the background (US 02.07.01). */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the reservation and its entrant from the repositories, and sets [loading] and [error]
     * (US 02.07.01).
     * [loadData] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            val loaded = reservationRepository.getReservation(reservationId)
                ?.takeIf { it.ownerId == ownerId }
            entrant = loaded?.let { profileRepository.getProfile(it.userId) }
            reservation = loaded
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Couldn't load the reservation"
        } finally {
            loading = false
        }
    }

    companion object {
        /** Makes the controller for [reservationId] with the app's real repositories and this device's ID. */
        fun factory(reservationId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                OwnerReservationDetailsController(
                    reservationRepository = app.container.reservationRepository,
                    profileRepository = app.container.profileRepository,
                    ownerId = app.container.deviceId,
                    reservationId = reservationId,
                )
            }
        }
    }
}
