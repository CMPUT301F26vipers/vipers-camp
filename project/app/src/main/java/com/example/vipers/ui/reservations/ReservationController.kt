/*
 * ReservationController.kt
 * The controller (a ViewModel) behind the lists of reservations: the entrant's My Reservations
 * (US 01.06.01) and the owner's active reservations (US 02.07.01). It loads the reservations,
 * keeps the active ones, and for the owner looks up each entrant's current name.
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
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads the reservation lists for this device's user: the ones they made as an entrant
 * (US 01.06.01) and the ones at their campsites as an owner (US 02.07.01).
 * Takes the repositories in its constructor so tests can pass fake ones.
 *
 * @param userId This device's ID, the entrant for My Reservations and the owner for the owner list.
 */
class ReservationController(
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val userId: String,
) : ViewModel() {

    /** The entrant's own active reservations, soonest check-in first (US 01.06.01). */
    var myActive by mutableStateOf(emptyList<Reservation>())
        private set

    /** The owner's active reservations, soonest check-in first. */
    var activeRows by mutableStateOf(emptyList<ReservationRow>())
        private set

    /** True while the reservations are loading. */
    var isLoading by mutableStateOf(true)
        private set

    /** True if the last load failed, e.g. because the phone is offline. */
    var loadFailed by mutableStateOf(false)
        private set

    /** Loads the entrant's own reservations in the background (US 01.06.01). */
    fun loadMyReservations() {
        viewModelScope.launch { fetchMyReservations() }
    }

    /**
     * Gets the reservations this user made that are active on [today] into [myActive], and sets
     * [isLoading] and [loadFailed] (US 01.06.01). Reservations whose dates can't be read are
     * left out. [loadMyReservations] runs this in the background; unit tests call it directly
     * with a fixed date.
     */
    suspend fun fetchMyReservations(today: LocalDate = LocalDate.now()) {
        isLoading = true
        loadFailed = false
        try {
            val reservations = reservationRepository.getReservationsForUser(userId)
            myActive = activeSoonestFirst(reservations, today)
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    /** Loads the owner's active reservations in the background (US 02.07.01). */
    fun loadOwnerActive() {
        viewModelScope.launch { fetchOwnerActive() }
    }

    /**
     * Gets the reservations active on [today] at the owner's campsites into [activeRows], and sets
     * [isLoading] and [loadFailed] (US 02.07.01). Reservations whose dates can't be read are left
     * out, so one bad document doesn't stop the rest from showing.
     * [loadOwnerActive] runs this in the background; unit tests call it directly with a fixed date.
     */
    suspend fun fetchOwnerActive(today: LocalDate = LocalDate.now()) {
        isLoading = true
        loadFailed = false
        try {
            val reservations = reservationRepository.getReservationsForOwner(userId)
            activeRows = activeSoonestFirst(reservations, today).map { reservation ->
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
            throw e
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    // the active ones on today, soonest check-in first, skipping any with dates that can't be read
    // so one bad document doesn't stop the rest from showing (both lists use this)
    private fun activeSoonestFirst(
        reservations: List<Reservation>,
        today: LocalDate,
    ): List<Reservation> =
        reservations
            .filter { reservation ->
                try {
                    LocalDate.parse(reservation.checkIn)
                    reservation.isActive(today)
                } catch (e: DateTimeParseException) {
                    false
                }
            }
            .sortedBy { it.checkIn }

    companion object {
        /** Makes the controller with the app's real repositories and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ReservationController(
                    reservationRepository = app.container.reservationRepository,
                    profileRepository = app.container.profileRepository,
                    userId = app.container.deviceId,
                )
            }
        }
    }
}
