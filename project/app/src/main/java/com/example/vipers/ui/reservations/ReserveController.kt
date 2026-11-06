/*
 * ReserveController.kt
 * The controller (a ViewModel) behind the Reserve screen (mockup E11, US 01.04.01). It loads the
 * won lottery, the campsite and its calendar, works out which of the lottery's nights are still
 * free, lets the winner pick nights in a row, and adds up the total. The winner can also decline
 * the win (US 01.04.03).
 * This is the "Load the Reserve screen" and "Decline a win" parts of the ReservationController CRC
 * card; it is its own class because ReservationController already runs the owner's reservation
 * list (02.07.01).
 * Known issues: reserving the nights (T-03), choosing between several campsites (01.04.02) and the
 * guests field are added by those stories. Declining doesn't pick a replacement winner yet; that's
 * the replacement draw (T-12).
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
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
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
 * [campsiteId] (or the lottery's first campsite when it's null or not in the lottery).
 * Takes the repositories in its constructor so tests can pass fake ones.
 */
class ReserveController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val userId: String,
    private val lotteryId: String,
    private val campsiteId: String? = null,
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

    /** True while the screen is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The user's entry in the lottery, or null if they never joined or it hasn't loaded. */
    var entry by mutableStateOf<LotteryEntry?>(null)
        private set

    /**
     * True if the user can decline this win now, so the screen offers Decline (US 01.04.03): the
     * entry is WON and booking is open. False once it's reserved, declined or expired.
     */
    var canDecline by mutableStateOf(false)
        private set

    /** True while a decline is being saved, so a second tap does nothing (US 01.04.03). */
    var isDeclining by mutableStateOf(false)
        private set

    /**
     * A message to show if declining failed, e.g. because the phone is offline (US 01.04.03). Kept
     * apart from [error] so the rest of the screen stays on show.
     */
    var declineError by mutableStateOf<String?>(null)
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
                entry = null
                canDecline = false
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

            val loadedEntry = lotteryRepository.getEntry(found.id, userId)
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
            entry = loadedEntry
            offerState = stateOf(loadedEntry, found, now)
            canDecline = loadedEntry?.canDecline(found, now) == true
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

    /** Declines the win in the background (US 01.04.03). The screen calls this on Confirm. */
    fun declineWin() {
        viewModelScope.launch { decline() }
    }

    /**
     * Declines the user's win at time [now] (milliseconds since 1970) (US 01.04.03). Checks
     * [LotteryEntry.canDecline] again first, so a win that was reserved or expired since the screen
     * loaded is left alone, then sets the entry to DECLINED with
     * [LotteryRepository.declineTransaction] and reloads, so [canDecline] becomes false and
     * [offerState] becomes [OfferState.USED]. A second call while one is saving does nothing. If
     * saving fails, [declineError] says so. [declineWin] runs this in the background; unit tests
     * call it directly and pass their own [now].
     */
    suspend fun decline(now: Long = System.currentTimeMillis()) {
        val currentLottery = lottery ?: return
        val currentEntry = entry ?: return
        if (isDeclining || !currentEntry.canDecline(currentLottery, now)) return
        isDeclining = true
        declineError = null
        try {
            // false means it was no longer WON (e.g. a second tap got there first); the reload shows that
            lotteryRepository.declineTransaction(currentLottery.id, userId)
            // TODO(T-12): run one replacement draw for the declined spot with LotteryDrawer, as the
            //  ReservationController CRC card says. It needs the draw, which comes in #16.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            declineError = "Unable to decline your win. Please try again."
            return
        } finally {
            isDeclining = false
        }
        fetchData(now)
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
                    userId = app.container.deviceId,
                    lotteryId = lotteryId,
                    campsiteId = campsiteId,
                )
            }
        }
    }
}
