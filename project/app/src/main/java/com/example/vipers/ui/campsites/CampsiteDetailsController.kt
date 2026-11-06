/*
 * CampsiteDetailsController.kt
 * The controller (a ViewModel) behind the entrant's Campsite Details screen (mockup E05,
 * US 01.01.05). It loads one campsite and finds its lottery that is open for registration, if any,
 * and the lottery the user won here, so they can open the Reserve screen (US 01.04.01).
 * Known issues: joining the waiting list (01.01.06) is added by that story.
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one campsite and its active lottery for the Campsite Details screen.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones, the [campsiteId] of the campsite to show, and the [userId] of the person looking
 * (empty in tests that don't need a win).
 */
class CampsiteDetailsController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val campsiteId: String,
    private val userId: String = "",
) : ViewModel() {

    /** The campsite, or null until it loads, or if it wasn't found or was removed. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** The campsite's lottery that entrants can join right now, or null if there is none. */
    var activeLottery by mutableStateOf<Lottery?>(null)
        private set

    /**
     * A lottery at this campsite that the user won and whose booking period hasn't ended, so they
     * can still open the Reserve screen (US 01.04.01), or null if there is none.
     */
    var wonLottery by mutableStateOf<Lottery?>(null)
        private set

    /**
     * How many spots remain on the active lottery's waiting list, or null if capacity is unlimited
     * or there is no active lottery.
     */
    val spotsLeft: Int?
        get() {
            val capacity = activeLottery?.waitlistCapacity ?: return null
            val count = activeLottery?.waitingUserIds?.size ?: 0
            return maxOf(0, capacity - count)
        }

    /** True while the campsite is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Loads the campsite and its active lottery in the background, when the screen opens. */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the campsite, its lottery that is open at time [now] (milliseconds since 1970) and the
     * lottery the user won here, and sets [loading] and [error]. If several are open, the one whose
     * registration closes first is shown. [loadData] runs this in the background; unit tests call
     * it directly and pass their own [now].
     */
    suspend fun fetchData(now: Long = System.currentTimeMillis()) {
        loading = true
        error = null
        try {
            // a removed campsite counts as not found, like in search
            val found = campsiteRepository.getCampsite(campsiteId)?.takeIf { !it.removed }
            val lotteries = if (found == null) {
                emptyList()
            } else {
                lotteryRepository.getLotteriesForCampsite(campsiteId)
            }
            activeLottery = lotteries
                .filter { it.canEntrantJoin(now) }
                .minByOrNull { it.registrationEnd }
            wonLottery = if (userId.isEmpty()) {
                null
            } else {
                lotteries
                    .filter { !it.isBookingExpired(now) }
                    .firstOrNull { lotteryRepository.getEntry(it.id, userId)?.status == EntryStatus.WON }
            }
            campsite = found
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Unable to load details."
        } finally {
            loading = false
        }
    }

    companion object {
        /** Makes the controller for [campsiteId] with the app's real repositories and this device. */
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteDetailsController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    campsiteId = campsiteId,
                    userId = app.container.deviceId,
                )
            }
        }
    }
}
