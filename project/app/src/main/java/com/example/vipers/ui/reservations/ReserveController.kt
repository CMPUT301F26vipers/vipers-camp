/*
 * ReserveController.kt
 * The controller (a ViewModel) behind the Reserve screen (mockup E11, US 01.04.01). It loads the
 * won lottery, the campsite and its calendar, works out which of the lottery's nights are still
 * free, lets the winner pick nights in a row, select their group size, reserve the nights (T-03),
 * and adds up the total.
 * This is the "Load the Reserve screen" part of the ReservationController CRC card; it is its own
 * class because ReservationController already runs the owner's reservation list (02.07.01).
 * Known issues: declining the win (01.04.03) and choosing between several campsites (01.04.02)
 * are added by those stories.
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
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.repository.ReserveResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Where the user's win stands on the Reserve screen (US 01.04.01). */
enum class OfferState {
    /** The user won and the booking period is open, so they can pick nights. */
    OPEN,

    /** The user won, but the booking period hasn't started yet. */
    NOT_YET_OPEN,

    /** The booking period ended before the user reserved, so the offer expired. */
    EXPIRED,

    /** The win was already used: the user reserved or declined it. */
    USED,

    /** The user didn't win this lottery (still waiting, lost, or never joined). */
    NO_WIN,
}

/**
 * Loads the Reserve screen for [userId]'s win in the lottery [lotteryId] at the campsite
 * [campsiteId] (or the lottery's first campsite when it's null or not in the lottery),
 * and handles reserving the chosen nights (T-03).
 * Takes the repositories in its constructor so tests can pass fake ones.
 */
class ReserveController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val userId: String,
    private val lotteryId: String,
    private val campsiteId: String? = null,
    private val reservationRepository: ReservationRepository,
) : ViewModel() {

    /** The won lottery, or null until it loads, or if it wasn't found or was removed. */
    var lottery by mutableStateOf<Lottery?>(null)
        private set

    /** The campsite being reserved, or null if it wasn't found or was removed. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** Where the user's win stands. */
    var offerState by mutableStateOf(OfferState.NO_WIN)
        private set

    /** Every night the lottery offers, in date order; the screen shows all of them. */
    var nights by mutableStateOf(emptyList<String>())
        private set

    /** The offered nights that are open, not booked and not past, so they can be picked. */
    var freeNights by mutableStateOf(emptyList<String>())
        private set

    /** The nights the winner picked, in a row and in date order. */
    var selectedNights by mutableStateOf(emptyList<String>())
        private set

    /** Number of guests in the group (US 01.04.01, T-03). Defaults to entry groupSize. */
    var groupSize by mutableStateOf(1)
        private set

    /** True while the screen is loading. */
    var loading by mutableStateOf(true)
        private set

    /** True while the reservation request is being processed. */
    var isReserving by mutableStateOf(false)
        private set

    /** The confirmed reservation after a successful reserve (T-03, US 01.05.01). */
    var confirmedReservation by mutableStateOf<Reservation?>(null)
        private set

    /** A message to show if loading failed. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Error message from a reserve attempt, if any (T-03). */
    var reserveError by mutableStateOf<String?>(null)
        private set

    /** True if the winner can pick nights now, but none are left to pick. */
    val noNightsLeft: Boolean
        get() = offerState == OfferState.OPEN && freeNights.isEmpty()

    /** The price line, e.g. "2 nights × $50", or null until the campsite loads. */
    val nightsTimesPriceText: String?
        get() {
            val site = campsite ?: return null
            val count = selectedNights.size
            val word = if (count == 1) "night" else "nights"
            return "$count $word × $${site.priceInDollars()}"
        }

    /** The total for the picked nights, e.g. "Total $100", or null until the campsite loads. */
    val totalText: String?
        get() = campsite?.let { "Total ${it.totalPriceText(selectedNights.size)}" }

    /** Loads the screen in the background. The screen calls this when it opens. */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the lottery, the campsite, the user's entry and the campsite's calendar at time [now]
     * (milliseconds since 1970), then works out [offerState] and [freeNights]. If a picked night
     * isn't free any more, the pick is cleared. [loadData] runs this in the background; unit tests
     * call it directly and pass their own [now].
     */
    suspend fun fetchData(now: Long = System.currentTimeMillis()) {
        loading = true
        error = null
        try {
            // a removed lottery counts as not found
            val found = lotteryRepository.getLottery(lotteryId)?.takeIf { !it.removed }
            if (found == null) {
                lottery = null
                campsite = null
                nights = emptyList()
                freeNights = emptyList()
                offerState = OfferState.NO_WIN
                return
            }

            // only a campsite this lottery covers can be reserved with its win
            var chosenId = found.campsiteIds.firstOrNull()
            if (campsiteId != null && campsiteId in found.campsiteIds) {
                chosenId = campsiteId
            }
            var site: Campsite? = null
            if (chosenId != null) {
                site = campsiteRepository.getCampsite(chosenId)?.takeIf { !it.removed }
            }

            val entry = lotteryRepository.getEntry(found.id, userId)
            if (entry != null && entry.groupSize > 0) {
                groupSize = entry.groupSize
            }
            // the day of "now" on this phone's clock, to leave out nights that are already past
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            var free = emptyList<String>()
            if (site != null) {
                free = found.freeNights(campsiteRepository.getAvailability(site.id), today)
            }

            lottery = found
            campsite = site
            nights = found.nightsOffered.distinct().sorted()
            freeNights = free
            offerState = stateOf(entry, found, now)
            // if a picked night was taken in the meantime, the rest may not be in a row any more
            if (selectedNights.any { it !in free }) selectedNights = emptyList()
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Unable to load your win."
        } finally {
            loading = false
        }
    }

    /** Sets group size within 1..maxOccupancy (T-03). */
    fun onGroupSizeChange(newSize: Int) {
        val max = campsite?.maxOccupancy ?: 1
        if (newSize in 1..max) {
            groupSize = newSize
        }
    }

    /** Increments group size by 1 up to campsite.maxOccupancy (T-03). */
    fun incrementGroupSize() {
        val max = campsite?.maxOccupancy ?: 1
        if (groupSize < max) {
            groupSize++
        }
    }

    /** Decrements group size by 1 down to 1 (T-03). */
    fun decrementGroupSize() {
        if (groupSize > 1) {
            groupSize--
        }
    }

    /** Clears the current reservation error message. */
    fun clearReserveError() {
        reserveError = null
    }

    /** Dismisses the confirmed reservation after viewing (US 01.05.01). */
    fun dismissConfirmation() {
        confirmedReservation = null
    }

    /** Reserves the picked nights in the background (T-03). */
    fun reserve() {
        viewModelScope.launch { reserveNights() }
    }

    /**
     * Reserves the picked nights for [groupSize] at time [now] (T-03).
     * Enforces that the winner has an unused win during the booking period,
     * groupSize <= maxOccupancy, and selected nights are not already held.
     * If another winner reserved any of the same nights first, fails with a clear message
     * and refreshes the calendar so the taken nights are greyed out (AC 4).
     */
    suspend fun reserveNights(now: Long = System.currentTimeMillis()): ReserveResult {
        val site = campsite ?: run {
            reserveError = "Campsite is not available."
            return ReserveResult.Error(reserveError!!)
        }
        val lot = lottery ?: run {
            reserveError = "Lottery is not available."
            return ReserveResult.Error(reserveError!!)
        }
        if (offerState != OfferState.OPEN) {
            reserveError = "Only winners with an open booking period can reserve."
            return ReserveResult.NotEligible
        }
        if (selectedNights.isEmpty()) {
            reserveError = "Please pick at least one night."
            return ReserveResult.Error(reserveError!!)
        }
        if (groupSize > site.maxOccupancy) {
            reserveError = "Group size cannot exceed the maximum occupancy of ${site.maxOccupancy}."
            return ReserveResult.CapacityExceeded
        }

        isReserving = true
        reserveError = null
        try {
            val result = reservationRepository.createReservation(
                lotteryId = lot.id,
                campsiteId = site.id,
                userId = userId,
                nights = selectedNights,
                groupSize = groupSize,
                now = now,
            )
            when (result) {
                is ReserveResult.Success -> {
                    confirmedReservation = result.reservation
                    offerState = OfferState.USED
                    fetchData(now)
                }
                is ReserveResult.Conflict -> {
                    reserveError =
                        "These nights were just booked by another winner. " +
                            "Please pick different nights."
                    fetchData(now)
                }
                is ReserveResult.CapacityExceeded -> {
                    reserveError =
                        "Group size cannot exceed the maximum occupancy of ${site.maxOccupancy}."
                }
                is ReserveResult.NotEligible -> {
                    reserveError = "You are not eligible to reserve this win."
                    fetchData(now)
                }
                is ReserveResult.Error -> {
                    reserveError = result.message
                }
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Unable to complete reservation. Please try again."
            reserveError = msg
            return ReserveResult.Error(msg)
        } finally {
            isReserving = false
        }
    }

    /**
     * Picks or un-picks [night]. Nights must be in a row, because a reservation has one check-in
     * and one check-out: a free night next to the picked ones is added, the first or last picked
     * night is removed, and any other free night starts a new pick. Taken nights, and every night
     * while the offer isn't [OfferState.OPEN], do nothing.
     */
    fun onNightTap(night: String) {
        if (offerState != OfferState.OPEN || night !in freeNights) return
        val picked = selectedNights
        if (picked.isEmpty()) {
            selectedNights = listOf(night)
            return
        }
        val first = LocalDate.parse(picked.first())
        val last = LocalDate.parse(picked.last())
        val tapped = LocalDate.parse(night)
        selectedNights = when {
            night == picked.first() || night == picked.last() -> picked - night
            tapped == last.plusDays(1) -> picked + night
            tapped == first.minusDays(1) -> listOf(night) + picked
            else -> listOf(night)
        }
    }

    // Where the win stands, from the user's entry and the lottery's booking period at time now.
    private fun stateOf(entry: LotteryEntry?, lottery: Lottery, now: Long): OfferState = when {
        entry == null -> OfferState.NO_WIN
        entry.status == EntryStatus.RESERVED || entry.status == EntryStatus.DECLINED ->
            OfferState.USED
        entry.status == EntryStatus.EXPIRED || entry.isExpired(lottery, now) -> OfferState.EXPIRED
        entry.canReserve(lottery, now) -> OfferState.OPEN
        entry.status == EntryStatus.WON && now < lottery.bookingStart -> OfferState.NOT_YET_OPEN
        else -> OfferState.NO_WIN
    }

    companion object {
        /** Makes the controller for this device's win in [lotteryId] at [campsiteId]. */
        fun factory(lotteryId: String, campsiteId: String?) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ReserveController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    reservationRepository = app.container.reservationRepository,
                    userId = app.container.deviceId,
                    lotteryId = lotteryId,
                    campsiteId = campsiteId,
                )
            }
        }
    }
}
