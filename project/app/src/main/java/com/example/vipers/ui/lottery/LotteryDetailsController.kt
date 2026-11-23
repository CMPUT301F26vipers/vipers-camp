/*
 * LotteryDetailsController.kt
 * The controller (a ViewModel) handling data fetching, states, joining, and leaving logic for a specific lottery's detail screen.
 * The owner's Draw now button runs the draw through it, even while registration is open (US 02.05.01b).
 * Known issues: none. Waiting-list capacity and leaving are enforced atomically in a Firestore transaction via LotteryRepository.
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
import com.example.vipers.domain.repository.JoinWaitingListResult
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
 * @param lotteryDrawer Optional drawer service to trigger or verify lottery draw.
 */
class LotteryDetailsController(
    private val lotteryRepository: LotteryRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryId: String,
    private val currentUserId: String = "",
    private val campsiteRepository: CampsiteRepository? = null,
    private val lotteryDrawer: LotteryDrawer? = null,
) : ViewModel() {

    var lottery by mutableStateOf<Lottery?>(null)
        private set
    var waitingEntries by mutableStateOf<List<LotteryEntry>>(emptyList())
        private set
    var allEntries by mutableStateOf<List<LotteryEntry>>(emptyList())
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
    var actionError by mutableStateOf<String?>(null)
        private set
    var leaveSuccess by mutableStateOf(false)
        private set

    /** True while the owner's Draw now is running, so the button can't start it twice. */
    var isDrawing by mutableStateOf(false)
        private set

    /** What happened after the owner tapped Draw now, or null before that (US 02.05.01b). */
    var drawMessage by mutableStateOf<String?>(null)
        private set

    /** True when [drawMessage] is an error, so the screen can show it in red. */
    var drawFailed by mutableStateOf(false)
        private set

    /** True if the current user is already on the waiting list. */
    val isJoined: Boolean
        get() = userEntry?.status == EntryStatus.WAITING

    /** True if the current user can leave the waiting list. */
    val canLeave: Boolean
        get() = !isOwner && lottery?.let { userEntry?.canLeave(it) } == true

    /** True if the lottery has a waitlist limit and the number of waiting entrants has reached or exceeded it. */
    val isFull: Boolean
        get() {
            val capacity = lottery?.waitlistCapacity ?: return false
            return waitingEntries.size >= capacity
        }

    /** True if the current user is the owner who created this lottery. */
    val isOwner: Boolean
        get() = currentUserId.isNotBlank() && lottery?.ownerId == currentUserId

    /** True if the current user owns this lottery and can draw it at [now] (US 02.05.01b). */
    fun canDraw(now: Long = System.currentTimeMillis()): Boolean =
        isOwner && lottery?.canOwnerDraw(now) == true

    /** Runs the owner's Draw now in the background (US 02.05.01b). */
    fun drawNow() {
        viewModelScope.launch { performDrawNow() }
    }

    /**
     * Runs the draw at [now], closing registration first if it's still open, then sets
     * [drawMessage] and reloads so the screen shows the lottery as drawn. Unit tests call this
     * directly.
     */
    suspend fun performDrawNow(now: Long = System.currentTimeMillis()) {
        val drawer = lotteryDrawer
        if (isDrawing || drawer == null || !canDraw(now)) return
        isDrawing = true
        // don't leave an old error on screen while trying again
        drawMessage = null
        drawFailed = false
        val result = try {
            drawer.drawNow(lotteryId, now)
        } catch (e: CancellationException) {
            isDrawing = false
            throw e
        } catch (e: Exception) {
            LotteryDrawResult.Error(e.message ?: "Draw failed")
        }
        isDrawing = false
        fetchData()
        // another phone's automatic draw may have finished just before the owner tapped
        val drawnElsewhere = result is LotteryDrawResult.Error && lottery?.drawnAt != null
        drawFailed = result is LotteryDrawResult.Error && !drawnElsewhere
        drawMessage = when {
            drawnElsewhere -> "This lottery has already been drawn"
            result is LotteryDrawResult.Success ->
                "Draw done: ${result.winnerCount} of ${result.totalCount} " +
                    (if (result.totalCount == 1) "entrant" else "entrants") + " won"
            result is LotteryDrawResult.EmptyList -> "Draw done: nobody had joined the waiting list"
            else -> "Couldn't run the draw. Try again."
        }
    }

    /**
     * Loads lottery details and resolves waitlist entry user display names in the background.
     * Triggered by [LotteryDetailsScreen]'s lifecycle observer on ON_RESUME.
     */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /** Triggers a lottery draw using the configured [LotteryDrawer], then refreshes data. */
    suspend fun drawLottery(): LotteryDrawResult {
        val drawer = lotteryDrawer ?: throw IllegalStateException("LotteryDrawer not configured")
        val result = drawer.drawLottery(lotteryId)
        fetchData()
        return result
    }

    /** Loads lottery details, waitlist entries, and the current user's entry. Unit tests can call it directly. */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            val loadedLottery = lotteryRepository.getLottery(lotteryId)
            lottery = loadedLottery
            val all = lotteryRepository.getEntries(lotteryId)
            allEntries = all
            val entries = all.filter { it.status == EntryStatus.WAITING }
            waitingEntries = entries

            if (currentUserId.isNotBlank()) {
                userEntry = lotteryRepository.getEntry(lotteryId, currentUserId)
            }

            maxOccupancy = resolveMaxOccupancy(loadedLottery)

            val names = mutableMapOf<String, String>()
            val displayEntries = if (loadedLottery?.drawnAt != null) all else entries
            for (entry in displayEntries) {
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
            actionError = "User ID is required to join."
            joinSuccess = false
            return false
        }

        isJoining = true
        actionError = null
        joinSuccess = false
        leaveSuccess = false

        val currentLottery = try {
            lotteryRepository.getLottery(lotteryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isJoining = false
            joinSuccess = false
            actionError = "Unable to join waiting list. Please try again."
            return false
        }

        if (currentLottery == null) {
            lottery = null
            maxOccupancy = null
            isJoining = false
            actionError = "Lottery not found."
            joinSuccess = false
            return false
        }
        lottery = currentLottery

        if (currentLottery.ownerId == currentUserId) {
            isJoining = false
            actionError = "Owners cannot join their own lottery."
            joinSuccess = false
            return false
        }

        val existing = try {
            userEntry ?: lotteryRepository.getEntry(lotteryId, currentUserId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            isJoining = false
            joinSuccess = false
            actionError = "Unable to join waiting list. Please try again."
            return false
        }

        val limit = resolveMaxOccupancy(currentLottery)
        maxOccupancy = limit
        if (limit == null) {
            isJoining = false
            joinSuccess = false
            actionError = "Campsite occupancy information is unavailable. Please try again."
            return false
        }

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
            actionError = validationErrors.first()
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

        val joinResult = try {
            lotteryRepository.joinWaitingList(newEntry, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            joinSuccess = false
            actionError = "Unable to join waiting list. Please try again."
            isJoining = false
            return false
        }

        when (joinResult) {
            is JoinWaitingListResult.Success -> {
                userEntry = newEntry
                joinSuccess = true
                actionError = null
            }
            is JoinWaitingListResult.WaitlistFull -> {
                joinSuccess = false
                actionError = "Waiting list is full"
                isJoining = false
                return false
            }
            is JoinWaitingListResult.AlreadyJoined -> {
                joinSuccess = false
                actionError = "Already on the waiting list"
                isJoining = false
                return false
            }
            is JoinWaitingListResult.Error -> {
                joinSuccess = false
                actionError = joinResult.message
                isJoining = false
                return false
            }
        }

        try {
            val all = lotteryRepository.getEntries(lotteryId)
            allEntries = all
            val updatedEntries = all.filter { it.status == EntryStatus.WAITING }
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
            if (allEntries.none { it.id == newEntry.id }) {
                allEntries = allEntries + newEntry
            }
        } finally {
            isJoining = false
        }

        return true
    }

    /** Clears transient feedback state. */
    fun clearJoinState() {
        joinSuccess = false
        leaveSuccess = false
        actionError = null
    }

    /** Leaves the waiting list in the background for the current user. */
    fun leaveWaitingList() {
        viewModelScope.launch { performLeave() }
    }

    /**
     * Executes the leave waiting list operation and refreshes state.
     *
     * @return True if leaving succeeded, false otherwise.
     */
    suspend fun performLeave(): Boolean {
        if (currentUserId.isBlank() || !isJoined) {
            actionError = "Not on the waiting list."
            return false
        }
        if (!canLeave) {
            actionError = "Cannot leave after the lottery has been drawn."
            return false
        }

        actionError = null
        joinSuccess = false
        leaveSuccess = false

        return try {
            lotteryRepository.leaveWaitingList(lotteryId, currentUserId)
            userEntry = null
            leaveSuccess = true
            try {
                fetchData()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Refresh failure must not be reported as a failed leave
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            actionError = "Couldn't leave the waiting list. Please try again."
            false
        }
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
                    lotteryDrawer = app.container.lotteryDrawer,
                )
            }
        }
    }
}
