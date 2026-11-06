/*
 * AvailabilityController.kt
 * The controller (a ViewModel) behind the owner's Availability screen (mockup O07, T-10). It loads
 * one campsite's calendar, opens and closes nights as the owner taps them (one at a time or a whole
 * date range), and saves the open nights.
 * Known issues: saving re-reads the calendar first so a booking made in the meantime isn't lost,
 * but it isn't a Firestore transaction; T-03 could make both writes one transaction.
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
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Loads, changes and saves the availability of the campsite with [campsiteId].
 * Takes the [campsiteRepository] in its constructor so tests can pass a fake one.
 */
class AvailabilityController(
    private val campsiteRepository: CampsiteRepository,
    private val campsiteId: String,
) : ViewModel() {

    /** The calendar with the owner's unsaved changes, or null until it loads. */
    var calendar by mutableStateOf<AvailabilityCalendar?>(null)
        private set

    /** The month the screen shows. */
    var month by mutableStateOf(YearMonth.now())
        private set

    /** True while the owner is choosing a date range: the next two taps are its ends. */
    var pickingRange by mutableStateOf(false)
        private set

    /** The first night of the date range being chosen, or null before it's tapped. */
    var rangeStart by mutableStateOf<String?>(null)
        private set

    /** A short note for the owner, e.g. why a night didn't change or that saving worked. */
    var message by mutableStateOf<String?>(null)
        private set

    /** True while the calendar is loading. */
    var loading by mutableStateOf(true)
        private set

    /** True while the calendar is being saved. */
    var saving by mutableStateOf(false)
        private set

    /** A message to show if loading failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Loads the calendar in the background. The screen calls this when it opens. */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the calendar from the repository and shows the month of [today]. [loadData] runs this
     * in the background; unit tests call it directly and pass their own [today].
     */
    suspend fun fetchData(today: LocalDate = LocalDate.now()) {
        loading = true
        error = null
        try {
            calendar = campsiteRepository.getAvailability(campsiteId)
            month = YearMonth.from(today)
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Unable to load availability."
        } finally {
            loading = false
        }
    }

    /** Shows the month before the current one. */
    fun showPreviousMonth() {
        month = month.minusMonths(1)
    }

    /** Shows the month after the current one. */
    fun showNextMonth() {
        month = month.plusMonths(1)
    }

    /** Starts choosing a date range; the owner then taps its first and last nights. */
    fun startRange() {
        pickingRange = true
        rangeStart = null
        message = "Tap the first night, then the last night."
    }

    /** Stops choosing a date range without changing anything. */
    fun cancelRange() {
        pickingRange = false
        rangeStart = null
        message = null
    }

    /**
     * Handles a tap on [night] ("yyyy-MM-dd"). Normally it opens or closes that night; while
     * [pickingRange] it records the range's first night, then opens the whole range. Past nights
     * (before [today]) and booked nights don't change, and [message] says why.
     */
    fun onNightTap(night: String, today: LocalDate = LocalDate.now()) {
        val current = calendar ?: return
        if (current.isPast(night, today)) {
            message = "Past nights can't be changed."
            return
        }
        if (pickingRange) {
            val start = rangeStart
            if (start == null) {
                rangeStart = night
                message = "Now tap the last night."
            } else {
                calendar = current.openRange(start, night, today)
                cancelRange()
            }
            return
        }
        if (current.isHeld(night)) {
            message = "This night is booked. Reject the reservation first to close it."
            return
        }
        calendar = current.toggle(night, today)
        message = null
    }

    /** Saves the open nights in the background. The Save button calls this. */
    fun save() {
        viewModelScope.launch { saveCalendar() }
    }

    /**
     * Saves the owner's open nights. It reads the calendar again first and keeps its booked nights,
     * so a reservation made while the owner was editing isn't lost. [save] runs this in the
     * background; unit tests call it directly.
     */
    suspend fun saveCalendar() {
        val edited = calendar ?: return
        saving = true
        try {
            val latest = campsiteRepository.getAvailability(campsiteId)
            val toSave = latest.copy(campsiteId = campsiteId, openNights = edited.openNights)
            campsiteRepository.saveAvailability(toSave)
            calendar = toSave
            message = "Availability saved"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "Couldn't save. Check your connection and try again."
        } finally {
            saving = false
        }
    }

    companion object {
        /** Makes the controller for [campsiteId] with the app's real repository. */
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                AvailabilityController(app.container.campsiteRepository, campsiteId)
            }
        }
    }
}
