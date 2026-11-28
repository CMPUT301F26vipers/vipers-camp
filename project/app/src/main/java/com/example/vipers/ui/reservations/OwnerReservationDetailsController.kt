/*
 * OwnerReservationDetailsController.kt
 * The controller (a ViewModel) behind the owner's view of one reservation (mockup O15,
 * US 02.07.01). It loads the reservation and the entrant who made it, and lets the owner reject
 * an active reservation with a reason and then notifies the entrant (US 02.08.01).
 * Known issues: none. Messaging the entrant (US 02.12.01) only needs the reservation's ID, so the
 * screen opens the conversation itself.
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
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.service.NotificationService
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one reservation at [ownerId]'s campsites and its entrant (US 02.07.01), and rejects it
 * (US 02.08.01). Takes the repositories and [notificationService] in its constructor so tests can
 * pass fake ones, and the [reservationId] of the reservation to show.
 */
class OwnerReservationDetailsController(
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val notificationService: NotificationService,
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

    /** True while the Reject reservation dialog (mockup O16) is open. */
    var showRejectDialog by mutableStateOf(false)
        private set

    /** The reason typed in the Reject reservation dialog. */
    var rejectReason by mutableStateOf("")
        private set

    /** Shown under the reason field when the reason is missing, or null. */
    var reasonError by mutableStateOf<String?>(null)
        private set

    /** True while a rejection is being saved. */
    var rejecting by mutableStateOf(false)
        private set

    /** Why the last rejection didn't work, or didn't reach the entrant, or null. */
    var rejectError by mutableStateOf<String?>(null)
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

    /**
     * True if the Reject Reservation button shows: the loaded reservation can be rejected on
     * [today] (US 02.08.01).
     */
    fun canReject(today: LocalDate = LocalDate.now()): Boolean =
        reservation?.canReject(today) == true

    /** Opens the Reject reservation dialog with an empty reason (US 02.08.01). */
    fun openRejectDialog() {
        rejectReason = ""
        reasonError = null
        rejectError = null
        showRejectDialog = true
    }

    /** Closes the dialog (Cancel) and forgets the reason; the reservation stays as it is. */
    fun dismissRejectDialog() {
        showRejectDialog = false
        rejectReason = ""
        reasonError = null
    }

    /** Saves the reason as the owner types it. */
    fun onReasonChange(text: String) {
        rejectReason = text
        reasonError = null
    }

    /** Rejects the reservation in the background (US 02.08.01). */
    fun rejectReservation() {
        viewModelScope.launch { performReject() }
    }

    /**
     * Rejects the reservation with the typed reason, which gives its nights back, then reloads it
     * and notifies the entrant (US 02.08.01). A blank reason only sets [reasonError]. If it can't
     * be rejected any more (e.g. the entrant cancelled first) the dialog closes and [rejectError]
     * says so. If the save fails (e.g. offline) the dialog stays open with the reason. If the
     * notice fails the rejection still stands and [rejectError] says the entrant wasn't told.
     * [rejectReservation] runs this in the background; unit tests call it directly with a fixed
     * time and date.
     *
     * @return True if the reservation is now rejected.
     */
    suspend fun performReject(
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val current = reservation ?: return false
        if (rejecting) return false
        if (!Reservation.isValidRejectionReason(rejectReason)) {
            reasonError = "Enter a reason"
            return false
        }
        if (!current.canReject(today)) {
            dismissRejectDialog()
            rejectError = CANT_REJECT
            return false
        }
        val reason = rejectReason.trim()
        rejecting = true
        rejectError = null
        try {
            val rejected = try {
                reservationRepository.rejectTransaction(current.id, reason, now, sendNotice = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rejectError = "Couldn't reject. Check your connection and try again."
                return false
            }
            dismissRejectDialog()
            fetchData()
            if (!rejected) {
                rejectError = CANT_REJECT
                return false
            }
            val updated = reservation?.takeIf { it.status == ReservationStatus.REJECTED }
                ?: current.copy(
                    status = ReservationStatus.REJECTED,
                    statusChangedAt = now,
                    rejectionReason = reason,
                )
            try {
                notificationService.sendReservationRejected(updated, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rejectError = "Rejected, but the entrant couldn't be notified."
            }
            return true
        } finally {
            rejecting = false
        }
    }

    companion object {
        private const val CANT_REJECT = "This reservation can no longer be rejected."

        /** Makes the controller for [reservationId] with the app's real repositories and this device's ID. */
        fun factory(reservationId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                OwnerReservationDetailsController(
                    reservationRepository = app.container.reservationRepository,
                    profileRepository = app.container.profileRepository,
                    notificationService = NotificationService(
                        app.container.notificationRepository,
                        app.container.campsiteRepository,
                        app.container.profileRepository,
                        app.container.lotteryRepository,
                    ),
                    ownerId = app.container.deviceId,
                    reservationId = reservationId,
                )
            }
        }
    }
}
