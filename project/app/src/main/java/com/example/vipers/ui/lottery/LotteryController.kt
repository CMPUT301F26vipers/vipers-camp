/*
 * LotteryController.kt
 * The controller (a ViewModel) handling the creation logic and date availability mapping for a new lottery.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.validateLottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ViewModel controller for managing the creation of a new lottery for a campsite.
 *
 * @param campsiteRepository Repository to access campsite details and availability.
 * @param lotteryRepository Repository to store new lotteries.
 * @param ownerId Device ID of the current user.
 * @param campsiteId ID of the campsite for which the lottery is created.
 */
class LotteryController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val ownerId: String,
    private val campsiteId: String,
) : ViewModel() {

    var campsite by mutableStateOf<Campsite?>(null)
        private set
    var availability by mutableStateOf<AvailabilityCalendar?>(null)
        private set
    var availableNights by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedNights by mutableStateOf(setOf<String>())
        private set

    var rangeStart by mutableStateOf("")
        private set
    var rangeEnd by mutableStateOf("")
        private set

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadCampsiteData()
    }

    /** Loads the campsite details and current availability calendar. */
    fun loadCampsiteData() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val result = campsiteRepository.getCampsite(campsiteId)
                if (result == null || result.ownerId != ownerId) {
                    error = "Campsite not found or you do not own it."
                } else {
                    campsite = result
                    val calendar = campsiteRepository.getAvailability(campsiteId)
                    availability = calendar
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Unable to load campsite availability."
            } finally {
                loading = false
            }
        }
    }

    /** Updates rangeStart and clears rangeEnd if invalid. */
    fun onRangeStartChange(date: String) {
        rangeStart = date
        if (rangeEnd.isNotEmpty()) {
            val start = dateFormat.parse(date)
            val end = dateFormat.parse(rangeEnd)
            if (end != null && start != null && end.before(start)) {
                rangeEnd = ""
            }
        }
    }

    /** Updates rangeEnd date. */
    fun onRangeEndChange(date: String) {
        rangeEnd = date
    }

    /** Clears any active error message. */
    fun clearError() {
        error = null
    }

    /** Loads and accumulates open, unheld nights for the selected date range. */
    fun loadSelectedRange() {
        try {
            val start = dateFormat.parse(rangeStart)
                ?: throw IllegalArgumentException()
            val end = dateFormat.parse(rangeEnd)
                ?: throw IllegalArgumentException()

            if (end.before(start)) {
                error = "End date must be on or after the start date."
                return
            }

            val calendar = availability ?: return

            val cursor = Calendar.getInstance().apply { time = start }
            val endCalendar = Calendar.getInstance().apply { time = end }

            val newNights = mutableListOf<String>()

            while (!cursor.after(endCalendar)) {
                val night = dateFormat.format(cursor.time)
                if (night in calendar.openNights && night !in calendar.heldNights) {
                    newNights.add(night)
                }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }

            availableNights = (availableNights + newNights).distinct().sorted()
            selectedNights = selectedNights.intersect(availableNights.toSet())

            error = if (newNights.isEmpty()) {
                "No available nights in this date range."
            } else {
                null
            }
        } catch (e: Exception) {
            error = "Please select valid start and end dates."
        }
    }

    /** Toggles the selection state of a specific night. */
    fun toggleNight(night: String, checked: Boolean) {
        if (checked) {
            selectedNights = selectedNights + night
        } else {
            selectedNights = selectedNights - night
        }
        error = null
    }

    /** Toggles between selecting all available nights and clearing selections. */
    fun toggleSelectAll() {
        if (selectedNights.size == availableNights.size) {
            selectedNights = emptySet()
        } else {
            selectedNights = availableNights.toSet()
        }
        error = null
    }

    /** Validates and saves the created lottery asynchronously. */
    fun saveLottery() {
        if (saving) return
        val nightsSnapshot = selectedNights.toSet()
        if (nightsSnapshot.isEmpty()) {
            error = "Select at least one night"
            return
        }

        val lottery = Lottery(
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = nightsSnapshot.sorted(),
            winnersCount = 1
        )

        val validationErrors = validateLottery(lottery)
        if (validationErrors.isNotEmpty()) {
            error = validationErrors.first()
            return
        }

        viewModelScope.launch {
            saving = true
            error = null
            try {
                val latestCalendar = campsiteRepository.getAvailability(campsiteId)
                val invalidNights = nightsSnapshot.filter {
                    it !in latestCalendar.openNights || it in latestCalendar.heldNights
                }

                if (invalidNights.isNotEmpty()) {
                    error = "Your selected nights are no longer available."
                    saving = false
                    return@launch
                }

                lotteryRepository.addLottery(lottery)
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Unable to save lottery."
            } finally {
                saving = false
            }
        }
    }

    companion object {
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    ownerId = app.container.deviceId,
                    campsiteId = campsiteId
                )
            }
        }
    }
}