/*
 * ReservationController.kt
 * The controller (a ViewModel) behind the lists of reservations: the entrant's My Reservations
 * (US 01.06.01) and the owner's Reservations screen. For the owner it loads the reservations at
 * their campsites once, splits them into the Active list (US 02.07.01) and the Past list of
 * completed stays (US 02.07.02), and looks up each entrant's current name.
 * Known issues: the owner's Cancelled list (US 02.07.03) is not built yet.
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
 * (US 01.06.01), and the ones at their campsites as an owner, split into the tabs of the owner's
 * Reservations screen: active ones (US 02.07.01) and completed stays (US 02.07.02). The owner's
 * tabs are built from the same load, so switching tabs doesn't load again and the tabs never
 * disagree.
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

    /** The owner's completed stays, most recent check-out first (US 02.07.02). */
    var pastRows by mutableStateOf(emptyList<ReservationRow>())
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
            // clear the old list so a failed reload doesn't leave stale reservations behind
            myActive = emptyList()
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

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
            val reservations = reservationRepository.getReservationsForOwner(userId)
                .filter { hasReadableDates(it) }

            val active = reservations
                .filter { it.isActive(today) }
                .sortedBy { it.checkIn }
            val past = reservations
                .filter { it.isCompleted(today) }
                .sortedWith(
                    compareByDescending<Reservation> { it.checkOut }.thenByDescending { it.checkIn }
                )

            // look each entrant up once, so a repeat guest doesn't cost another read
            val names = (active + past).map { it.userId }.distinct()
                .associateWith { profileRepository.getDisplayName(it) }

            activeRows = active.map { toRow(it, names) }
            pastRows = past.map { toRow(it, names) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            activeRows = emptyList()
            pastRows = emptyList()
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    // the active ones on today, soonest check-in first, skipping any with dates that can't be read
    // so one bad document doesn't stop the rest from showing (My Reservations uses this)
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

    /** True if [reservation]'s check-in and check-out are both readable "yyyy-MM-dd" dates. */
    private fun hasReadableDates(reservation: Reservation): Boolean =
        try {
            LocalDate.parse(reservation.checkIn)
            LocalDate.parse(reservation.checkOut)
            true
        } catch (e: DateTimeParseException) {
            false
        }

    /** The list row for [reservation], with the entrant's current name from [names]. */
    private fun toRow(reservation: Reservation, names: Map<String, String>) = ReservationRow(
        reservationId = reservation.id,
        entrantName = names.getValue(reservation.userId),
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
                    userId = app.container.deviceId,
                )
            }
        }
    }
}
