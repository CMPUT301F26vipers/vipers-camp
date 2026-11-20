/*
 * ReservationController.kt
 * The controller (a ViewModel) behind the owner's Reservations screen. It loads the reservations at
 * the owner's campsites once, splits them into the Active list (US 02.07.01) and the Past list of
 * completed stays (US 02.07.02), and looks up each entrant's current name.
 * Known issues: none. The Cancelled list (US 02.07.03) is not built yet.
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
 * Loads the reservations at [ownerId]'s campsites and splits them into the tabs of the owner's
 * Reservations screen: active ones (US 02.07.01) and completed stays (US 02.07.02). Each tab's list
 * is built from the same load, so switching tabs doesn't load again and the tabs never disagree.
 * Takes the repositories in its constructor so tests can pass fake ones.
 */
class ReservationController(
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val ownerId: String,
) : ViewModel() {

    /** The owner's active reservations, soonest check-in first. */
    var activeRows by mutableStateOf(emptyList<ReservationRow>())
        private set

    /** The owner's completed stays, most recent check-out first (US 02.07.02). */
    var pastRows by mutableStateOf(emptyList<ReservationRow>())
        private set

    /** True while the reservations are loading. */
    var isLoading by mutableStateOf(true)
        private set

    /** True if the last load failed, e.g. because the phone is offline. */
    var loadFailed by mutableStateOf(false)
        private set

    /** Loads the owner's reservations in the background (US 02.07.01, 02.07.02). */
    fun loadOwnerReservations() {
        viewModelScope.launch { fetchOwnerReservations() }
    }

    /**
     * Gets the reservations at the owner's campsites once and fills [activeRows] with the ones
     * active on [today] and [pastRows] with the stays completed before [today], and sets
     * [isLoading] and [loadFailed] (US 02.07.01, 02.07.02). Reservations whose dates can't be read
     * are left out of every list, so one bad document doesn't stop the rest from showing. If the
     * load fails, every list is emptied.
     * [loadOwnerReservations] runs this in the background; unit tests call it directly with a
     * fixed date.
     */
    suspend fun fetchOwnerReservations(today: LocalDate = LocalDate.now()) {
        isLoading = true
        loadFailed = false
        try {
            val reservations = reservationRepository.getReservationsForOwner(ownerId)
                .filter { hasReadableDates(it) }

            val active = reservations
                .filter { it.isActive(today) }
                .sortedBy { it.checkIn }
                .map { toRow(it) }
            val past = reservations
                .filter { it.isCompleted(today) }
                .sortedWith(
                    compareByDescending<Reservation> { it.checkOut }.thenByDescending { it.checkIn }
                )
                .map { toRow(it) }

            activeRows = active
            pastRows = past
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            activeRows = emptyList()
            pastRows = emptyList()
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    /** True if [reservation]'s check-in and check-out are both readable "yyyy-MM-dd" dates. */
    private fun hasReadableDates(reservation: Reservation): Boolean =
        try {
            LocalDate.parse(reservation.checkIn)
            LocalDate.parse(reservation.checkOut)
            true
        } catch (e: DateTimeParseException) {
            false
        }

    /** The list row for [reservation], with the entrant's current name. */
    private suspend fun toRow(reservation: Reservation) = ReservationRow(
        reservationId = reservation.id,
        entrantName = profileRepository.getDisplayName(reservation.userId),
        campsiteName = reservation.campsiteName,
        checkIn = reservation.checkIn,
        checkOut = reservation.checkOut,
        groupSize = reservation.groupSize,
    )

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
