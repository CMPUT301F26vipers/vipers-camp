/*
 * LotteryDetailsController.kt
 * The controller (a ViewModel) handling data fetching, states, and joining logic for a specific lottery's detail screen.
 * Known issues: waiting-list capacity is currently checked client-side. Concurrent joins may both pass
 * the capacity check and exceed the limit. Correct enforcement requires a Firestore transaction and
 * an appropriate atomic count or data-model change, which is intentionally deferred to a follow-up
 * GitHub issue as agreed by the reviewer.
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
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.validateJoinLottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ViewModel controller for displaying details, managing waitlists, and joining a specific lottery.
 *
 * @param lotteryRepository Repository to fetch lottery details and entries.
 * @param profileRepository Repository to fetch user display names.
 * @param lotteryId ID of the lottery being inspected.
 * @param currentUserId Device ID of the current user (entrant or owner).
 * @param campsiteRepository Optional repository to fetch campsite occupancy.
 */
class LotteryDetailsController(
    private val lotteryRepository: LotteryRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryId: String,
    private val currentUserId: String = "",
    private val campsiteRepository: CampsiteRepository? = null,
) : ViewModel() {

    var lottery by mutableStateOf<Lottery?>(null)
        private set
    var waitingEntries by mutableStateOf<List<LotteryEntry>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var userNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var maxOccupancy by mutableStateOf<Int?>(null)
        private set

    var userEntry by mutableStateOf<LotteryEntry?>(null)
        private set
    var isJoining by mutableStateOf(false)
        private set
    var joinSuccess by mutableStateOf(false)
        private set
    var joinError by mutableStateOf<String?>(null)
        private set

    /** True if the current user is already on the waiting list. */
    val isJoined: Boolean
        get() = userEntry?.status == EntryStatus.WAITING

    /** True if the lottery has a waitlist limit and the number of waiting entrants has reached or exceeded it. */
    val isFull: Boolean
        get() {
            val capacity = lottery?.waitlistCapacity ?: return false
            return waitingEntries.size >= capacity
        }

    /** True if the current user is the owner who created this lottery. */
    val isOwner: Boolean
        get() = currentUserId.isNotBlank() && lottery?.ownerId == currentUserId

    init {
        loadData()
    }

    /** Loads lottery details and resolves waitlist entry user display names in the background. */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /** Loads lottery details, waitlist entries, and the current user's entry. Unit tests can call it directly. */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            val loadedLottery = lotteryRepository.getLottery(lotteryId)
            lottery = loadedLottery
            val entries = lotteryRepository.getEntries(lotteryId)
                .filter { it.status == EntryStatus.WAITING }
            waitingEntries = entries

            if (currentUserId.isNotBlank()) {
                userEntry = lotteryRepository.getEntry(lotteryId, currentUserId)
            }

            maxOccupancy = resolveMaxOccupancy(loadedLottery)

            val names = mutableMapOf<String, String>()
            for (entry in entries) {
                names[entry.userId] = profileRepository.getDisplayName(entry.userId)
            }
            if (currentUserId.isNotBlank() && currentUserId !in names) {
                names[currentUserId] = profileRepository.getDisplayName(currentUserId)
            }
            userNames = names
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Unable to load lottery details. Please check your network connection and try again."
        } finally {
            loading = false
        }
    }

    /** Joins the waiting list in the background with the specified [groupSize]. */
    fun joinWaitingList(groupSize: Int) {
        viewModelScope.launch { performJoin(groupSize) }
    }

    /**
     * Executes the join waiting list operation.
     * Validates eligibility, creates and saves a new [LotteryEntry], and updates state.
     *
     * @param groupSize Number of people in the entrant's group.
     * @param now Current timestamp in milliseconds (defaults to system time).
     * @return True if join succeeded, false otherwise.
     */
    suspend fun performJoin(groupSize: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (currentUserId.isBlank()) {
            joinError = "User ID is required to join."
            joinSuccess = false
            return false
        }

        val currentLottery = lottery ?: run {
            joinError = "Lottery not found."
            joinSuccess = false
            return false
        }

        if (currentLottery.ownerId == currentUserId) {
            joinError = "Owners cannot join their own lottery."
            joinSuccess = false
            return false
        }

        isJoining = true
        joinError = null
        joinSuccess = false

        val existing = try {
            userEntry ?: lotteryRepository.getEntry(lotteryId, currentUserId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isJoining = false
            joinSuccess = false
            joinError = "Unable to join waiting list. Please try again."
            return false
        }

        val limit = maxOccupancy ?: resolveMaxOccupancy(currentLottery)
        if (limit == null) {
            isJoining = false
            joinSuccess = false
            joinError = "Campsite occupancy information is unavailable. Please try again."
            return false
        }
        maxOccupancy = limit

        val validationErrors = validateJoinLottery(
            lottery = currentLottery,
            existingEntry = existing,
            waitingCount = waitingEntries.size,
            groupSize = groupSize,
            maxOccupancy = limit,
            now = now,
        )

        if (validationErrors.isNotEmpty()) {
            isJoining = false
            joinError = validationErrors.first()
            joinSuccess = false
            return false
        }

        val entryId = LotteryEntry.idFor(lotteryId, currentUserId)
        val newEntry = LotteryEntry(
            id = entryId,
            lotteryId = lotteryId,
            userId = currentUserId,
            groupSize = groupSize,
            joinedAt = now,
            status = EntryStatus.WAITING,
        )

        try {
            lotteryRepository.saveEntry(newEntry)
            userEntry = newEntry
            joinSuccess = true
            joinError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            joinSuccess = false
            joinError = "Unable to join waiting list. Please try again."
            isJoining = false
            return false
        }

        try {
            val updatedEntries = lotteryRepository.getEntries(lotteryId)
                .filter { it.status == EntryStatus.WAITING }
            waitingEntries = updatedEntries

            val names = userNames.toMutableMap()
            if (currentUserId !in names) {
                names[currentUserId] = profileRepository.getDisplayName(currentUserId)
            }
            userNames = names
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Refreshing the list failed, but the entry was already successfully saved.
            if (waitingEntries.none { it.id == newEntry.id }) {
                waitingEntries = waitingEntries + newEntry
            }
        } finally {
            isJoining = false
        }

        return true
    }

    /** Clears transient join feedback state. */
    fun clearJoinState() {
        joinSuccess = false
        joinError = null
    }

    /**
     * Resolves the maximum occupancy across all campsites covered by [lottery].
     * Returns null if any campsite lookup fails or is missing, ensuring incomplete data is not used.
     */
    private suspend fun resolveMaxOccupancy(lottery: Lottery?): Int? {
        val repo = campsiteRepository ?: return null
        val ids = lottery?.campsiteIds ?: return null
        if (ids.isEmpty()) return null

        val occupancies = mutableListOf<Int>()
        for (cid in ids) {
            try {
                val campsite = repo.getCampsite(cid) ?: return null
                occupancies.add(campsite.maxOccupancy)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        }
        return occupancies.maxOrNull()
    }

    companion object {
        fun factory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryDetailsController(
                    lotteryRepository = app.container.lotteryRepository,
                    profileRepository = app.container.profileRepository,
                    lotteryId = lotteryId,
                    currentUserId = app.container.deviceId,
                    campsiteRepository = app.container.campsiteRepository,
                )
            }
        }
    }
}
