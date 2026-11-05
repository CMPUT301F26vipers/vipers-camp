/*
 * ReservationController.kt
 * The controller (a ViewModel) behind the owner's list of active reservations (US 02.07.01). It
 * loads the reservations at the owner's campsites, keeps the active ones, and looks up each
 * entrant's current name.
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
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads the active reservations at [ownerId]'s campsites (US 02.07.01).
 * Takes the repositories in its constructor so tests can pass fake ones, and [today] so tests can
 * fix the date.
 */
class ReservationController(
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val ownerId: String,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** The owner's active reservations, soonest check-in first. */
    var activeRows by mutableStateOf(emptyList<ReservationRow>())
        private set

    /** True while the reservations are loading. */
    var isLoading by mutableStateOf(true)
        private set

    /** True if the last load failed, e.g. because the phone is offline. */
    var loadFailed by mutableStateOf(false)
        private set

    /**
     * Loads the owner's active reservations into [activeRows] in the background, and sets
     * [isLoading] and [loadFailed] (US 02.07.01).
     */
    fun loadOwnerActive() {
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            try {
                val date = today()
                activeRows = reservationRepository.getReservationsForOwner(ownerId)
                    .filter { it.isActive(date) }
                    .sortedBy { it.checkIn }
                    .map { reservation ->
                        ReservationRow(
                            reservationId = reservation.id,
                            entrantName = profileRepository.getDisplayName(reservation.userId),
                            campsiteName = reservation.campsiteName,
                            checkIn = reservation.checkIn,
                            checkOut = reservation.checkOut,
                            groupSize = reservation.groupSize,
                        )
                    }
            } catch (e: CancellationException) {
                // the load was stopped on purpose (the controller is gone), so it didn't fail
                throw e
            } catch (e: Exception) {
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    companion object {
        /** Makes the controller with the app's real repositories and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ReservationController(
                    reservationRepository = app.container.reservationRepository,
                    profileRepository = app.container.profileRepository,
                    ownerId = app.container.deviceId,
                )
            }
        }
    }
}
