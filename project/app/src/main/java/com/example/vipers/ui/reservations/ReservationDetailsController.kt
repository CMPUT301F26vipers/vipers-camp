/*
 * ReservationDetailsController.kt
 * The controller (a ViewModel) behind the entrant's view of their reservation (US 01.05.01).
 * It loads the reservation, its dates, group size, total price, and reservation number, and
 * lets the entrant cancel it before check-in (US 01.07.01).
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
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
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

    /** True while a cancel is being saved. */
    var cancelling by mutableStateOf(false)
        private set

    /** Why the last cancel didn't work, or null. */
    var cancelError by mutableStateOf<String?>(null)
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

    /** True if the loaded reservation can still be cancelled on [today] (US 01.07.01). */
    fun canCancel(today: LocalDate = LocalDate.now()): Boolean =
        reservation?.canCancel(today) == true

    /** Cancels the reservation in the background (US 01.07.01). */
    fun cancelReservation() {
        viewModelScope.launch { performCancel() }
    }

    /**
     * Cancels the loaded reservation, which gives its nights back and sends the entrant a
     * "Reservation cancelled" notice (US 01.07.01). Sets [cancelError] if it can't be cancelled
     * or the save fails. [cancelReservation] runs this in the background; unit tests call it
     * directly with a fixed time and date.
     *
     * @return True if the reservation is now cancelled.
     */
    suspend fun performCancel(
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val current = reservation ?: return false
        if (!current.canCancel(today)) {
            cancelError = CANT_CANCEL
            return false
        }
        cancelling = true
        cancelError = null
        return try {
            val cancelled = reservationRepository.changeStatus(
                id = current.id,
                status = ReservationStatus.CANCELLED,
                now = now,
            )
            if (cancelled) {
                reservation = current.copy(
                    status = ReservationStatus.CANCELLED,
                    statusChangedAt = now,
                )
            } else {
                // it changed somewhere else (e.g. the owner rejected it), so show what it is now
                reservation = reservationRepository.getReservation(current.id)
                    ?.takeIf { it.userId == userId }
                cancelError = CANT_CANCEL
            }
            cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cancelError = "Couldn't cancel. Check your connection and try again."
            false
        } finally {
            cancelling = false
        }
    }

    companion object {
        private const val CANT_CANCEL = "This reservation can no longer be cancelled."

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
