/*
 * CampsiteDetailsController.kt
 * The controller (a ViewModel) behind the entrant's Campsite Details screen (mockup E05,
 * US 01.01.05, US 01.01.06). It loads one campsite, finds its lottery that is open for registration,
 * and tracks the entrant's waitlist membership and eligibility to join.
 * Known issues: none.
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
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads one campsite and its active lottery for the Campsite Details screen.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones, the [campsiteId] of the campsite to show, and [currentUserId] to determine eligibility.
 */
class CampsiteDetailsController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val campsiteId: String,
    private val currentUserId: String = "",
) : ViewModel() {

    /** The campsite, or null until it loads, or if it wasn't found or was removed. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /** The campsite's lottery that entrants can join right now, or null if there is none. */
    var activeLottery by mutableStateOf<Lottery?>(null)
        private set

    /** The current user's entry in [activeLottery], if any. */
    var userEntry by mutableStateOf<LotteryEntry?>(null)
        private set

    /**
     * How many spots remain on the active lottery's waiting list, or null if capacity is unlimited
     * or there is no active lottery.
     */
    val spotsLeft: Int?
        get() {
            if (activeLottery?.drawnAt != null) return null
            val capacity = activeLottery?.waitlistCapacity ?: return null
            val count = activeLottery?.waitingUserIds?.size ?: 0
            return maxOf(0, capacity - count)
        }

    /** True if the current user is already on the waiting list. */
    val isJoined: Boolean
        get() = userEntry?.status == EntryStatus.WAITING ||
            (currentUserId.isNotBlank() && activeLottery?.waitingUserIds?.contains(currentUserId) == true)

    /** True if the active lottery has a waitlist limit and it has reached or exceeded it. */
    val isFull: Boolean
        get() {
            if (activeLottery?.drawnAt != null) return false
            val capacity = activeLottery?.waitlistCapacity ?: return false
            val count = activeLottery?.waitingUserIds?.size ?: 0
            return count >= capacity
        }

    /** True if the current user is the owner who created this campsite or active lottery. */
    val isOwner: Boolean
        get() = currentUserId.isNotBlank() && (campsite?.ownerId == currentUserId || activeLottery?.ownerId == currentUserId)

    /** True if the entrant is eligible to join the active lottery. */
    val canJoin: Boolean
        get() = activeLottery != null && activeLottery?.drawnAt == null && !isOwner && !isJoined && !isFull

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
     * Gets the campsite and its lottery that is open at time [now] (milliseconds since 1970), and
     * sets [loading] and [error]. If several are open, the one whose registration closes first is
     * shown. If none are open, finds the entrant's completed draw result if available.
     * [loadData] runs this in the background; unit tests call it directly and pass their own [now].
     */
    suspend fun fetchData(now: Long = System.currentTimeMillis()) {
        loading = true
        error = null
        try {
            // a removed campsite counts as not found, like in search
            val found = campsiteRepository.getCampsite(campsiteId)?.takeIf { !it.removed }
            val candidateLotteries = if (found == null) {
                emptyList()
            } else {
                lotteryRepository.getLotteriesForCampsite(campsiteId).filter { !it.removed }
            }

            // Prioritize an existing valid winning entry whose booking period is still active
            val activeWin = if (currentUserId.isNotBlank()) {
                candidateLotteries
                    .filter { it.drawnAt != null }
                    .mapNotNull { lottery ->
                        val entry = lotteryRepository.getEntry(lottery.id, currentUserId)
                        if (entry != null && entry.status == EntryStatus.WON && (entry.canReserve(lottery, now) || !entry.isExpired(lottery, now))) {
                            lottery to entry
                        } else {
                            null
                        }
                    }
                    .maxByOrNull { it.first.drawnAt ?: 0L }
            } else {
                null
            }

            if (activeWin != null) {
                activeLottery = activeWin.first
                userEntry = activeWin.second
            } else {
                val active = candidateLotteries
                    .filter { it.canEntrantJoin(now) }
                    .minByOrNull { it.registrationEnd }

                if (active != null) {
                    activeLottery = active
                    userEntry = if (currentUserId.isNotBlank()) {
                        lotteryRepository.getEntry(active.id, currentUserId)
                    } else {
                        null
                    }
                } else {
                    val drawnWithEntry = if (currentUserId.isNotBlank()) {
                        candidateLotteries
                            .filter { it.drawnAt != null }
                            .mapNotNull { lottery ->
                                val entry = lotteryRepository.getEntry(lottery.id, currentUserId)
                                if (entry != null && (entry.status == EntryStatus.WON || entry.status == EntryStatus.LOST)) {
                                    lottery to entry
                                } else {
                                    null
                                }
                            }
                            .maxByOrNull { it.first.drawnAt ?: 0L }
                    } else {
                        null
                    }
                    activeLottery = drawnWithEntry?.first
                    userEntry = drawnWithEntry?.second
                }
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
        /** Makes the controller for [campsiteId] with the app's real repositories. */
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteDetailsController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    campsiteId = campsiteId,
                    currentUserId = app.container.deviceId,
                )
            }
        }
    }
}
