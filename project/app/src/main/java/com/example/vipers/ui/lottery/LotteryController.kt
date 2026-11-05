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
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

    var nightsOfferedText by mutableStateOf("")
        private set

    var registrationStartMillis by mutableStateOf<Long?>(null)
        private set
    var registrationEndMillis by mutableStateOf<Long?>(null)
        private set

    var bookingStartMillis by mutableStateOf<Long?>(null)
        private set
    var bookingEndMillis by mutableStateOf<Long?>(null)
        private set

    var winnersCountText by mutableStateOf("1")
        private set

    var waitlistCapacityText by mutableStateOf("")
        private set

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadCampsiteData()
    }

    /** Helper for strict date parsing guaranteeing complete input consumption. */
    fun parseStrictDate(text: String, pattern: String = "yyyy-MM-dd"): Date? {
        if (text.isBlank()) return null
        val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val pos = ParsePosition(0)
        val date = sdf.parse(text, pos)
        if (date == null || pos.index != text.length) return null
        return date
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

    /** Updates text for nights offered field. */
    fun onNightsOfferedTextChange(text: String) {
        nightsOfferedText = text
        error = null
    }

    /** Updates rangeStart safely using strict parsing. */
    fun onRangeStartChange(date: String) {
        rangeStart = date
        if (rangeEnd.isNotEmpty()) {
            val start = parseStrictDate(date)
            val end = parseStrictDate(rangeEnd)
            if (end != null && start != null && end.before(start)) {
                rangeEnd = ""
            }
        }
    }

    /** Updates rangeEnd date. */
    fun onRangeEndChange(date: String) {
        rangeEnd = date
    }

    /** Sets registration start timestamp. */
    fun setRegistrationStart(millis: Long) {
        registrationStartMillis = millis
        error = null
    }

    /** Sets registration end timestamp. */
    fun setRegistrationEnd(millis: Long) {
        registrationEndMillis = millis
        error = null
    }

    /** Sets booking start timestamp. */
    fun setBookingStart(millis: Long) {
        bookingStartMillis = millis
        error = null
    }

    /** Sets booking end timestamp. */
    fun setBookingEnd(millis: Long) {
        bookingEndMillis = millis
        error = null
    }

    /** Sets winner count string. */
    fun onWinnersCountChange(text: String) {
        winnersCountText = text
        error = null
    }

    /** Sets waitlist capacity limit string. */
    fun onWaitlistCapacityChange(text: String) {
        waitlistCapacityText = text
        error = null
    }

    /** Clears any active error message. */
    fun clearError() {
        error = null
    }

    /** Loads and accumulates open, unheld nights for the selected date range. */
    fun loadSelectedRange() {
        try {
            val start = parseStrictDate(rangeStart)
                ?: throw IllegalArgumentException("Invalid start date")
            val end = parseStrictDate(rangeEnd)
                ?: throw IllegalArgumentException("Invalid end date")

            if (end.before(start)) {
                error = "End date must be on or after the start date."
                return
            }

            val calendar = availability ?: return

            val cursor = Calendar.getInstance().apply { time = start }
            val endCalendar = Calendar.getInstance().apply { time = end }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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
        saving = true
        error = null

        val nightsList = selectedNights.sorted()
        val nightsDesc = nightsOfferedText.takeIf { it.isNotBlank() }

        val winnersCount = winnersCountText.toIntOrNull() ?: 0

        val waitlistCapacity: Int? = if (waitlistCapacityText.isBlank()) {
            null
        } else {
            val parsed = waitlistCapacityText.trim().toIntOrNull()
            if (parsed == null || parsed < 1) {
                error = "Waitlist limit must be a whole number at least 1"
                saving = false
                return
            }
            parsed
        }

        val lottery = Lottery(
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = nightsList,
            nightsDescription = nightsDesc,
            registrationStart = registrationStartMillis ?: 0L,
            registrationEnd = registrationEndMillis ?: 0L,
            bookingStart = bookingStartMillis ?: 0L,
            bookingEnd = bookingEndMillis ?: 0L,
            winnersCount = winnersCount,
            waitlistCapacity = waitlistCapacity,
        )

        val validationErrors = validateLottery(lottery)
        if (validationErrors.isNotEmpty()) {
            error = validationErrors.first()
            saving = false
            return
        }

        viewModelScope.launch {
            try {
                val latestCalendar = campsiteRepository.getAvailability(campsiteId)
                val invalidNights = selectedNights.filter {
                    it !in latestCalendar.openNights || it in latestCalendar.heldNights
                }

                if (invalidNights.isNotEmpty()) {
                    error = "One or more selected nights are no longer available. Please reload the nights."
                    saving = false
                    return@launch
                }

                lotteryRepository.addLottery(lottery)
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Unable to save lottery."
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
