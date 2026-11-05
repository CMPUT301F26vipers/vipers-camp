/*
 * LotteryDetailsController.kt
 * The controller (a ViewModel) handling data fetching and states for a specific lottery's detail screen.
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
import com.example.vipers.domain.model.DuplicateLotteryEntryException
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.validateEntrantJoinEligibility
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ViewModel controller for displaying details and waitlists for a specific lottery.
 *
 * @param lotteryRepository Repository to fetch lottery details and entries.
 * @param profileRepository Repository to fetch user display names.
 * @param lotteryId ID of the lottery being inspected.
 * @param currentUserId Device ID of the user viewing/joining the lottery.
 */
class LotteryDetailsController(
    private val lotteryRepository: LotteryRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryId: String,
    private val currentUserId: String = "",
) : ViewModel() {

    var lottery by mutableStateOf<Lottery?>(null)
        private set
    var waitingEntries by mutableStateOf<List<LotteryEntry>>(emptyList())
        private set
    var userEntry by mutableStateOf<LotteryEntry?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var userNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    init {
        loadData()
    }

    /** Loads lottery details and resolves waitlist entry user display names in the background. */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /** Loads lottery details and resolves waitlist entry user display names. Unit tests can call it directly. */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            lottery = lotteryRepository.getLottery(lotteryId)
            val entries = lotteryRepository.getEntries(lotteryId)
                .filter { it.status == EntryStatus.WAITING }
            waitingEntries = entries

            if (currentUserId.isNotBlank()) {
                userEntry = lotteryRepository.getEntry(lotteryId, currentUserId)
            }

            val names = mutableMapOf<String, String>()
            for (entry in entries) {
                names[entry.userId] = profileRepository.getDisplayName(entry.userId)
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

    /** Asynchronously joins the lottery using the current user's device identity. */
    fun joinLottery(groupSize: Int = 1) {
        viewModelScope.launch {
            performJoinLottery(groupSize = groupSize)
        }
    }

    /**
     * Validates eligibility and saves a waiting-list entry for [userId] in this lottery.
     * Enforces that the current time is within [Lottery.registrationStart] and [Lottery.registrationEnd] (inclusive),
     * and guards against duplicate entries.
     *
     * @return null on success, or an error message if joining failed.
     */
    suspend fun performJoinLottery(
        userId: String = currentUserId,
        groupSize: Int = 1,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (userId.isBlank()) {
            error = "Unable to join: user not identified."
            return error
        }

        val currentLottery = lottery ?: run {
            error = "Unable to join: lottery not found."
            return error
        }

        val existingEntry = lotteryRepository.getEntry(lotteryId, userId)
        if (existingEntry != null) {
            userEntry = existingEntry
            error = "You have already joined this lottery."
            return error
        }

        val eligibilityError = validateEntrantJoinEligibility(currentLottery, now)
        if (eligibilityError != null) {
            error = eligibilityError
            return eligibilityError
        }

        return try {
            val entry = LotteryEntry(
                lotteryId = lotteryId,
                userId = userId,
                groupSize = groupSize,
                joinedAt = now,
                status = EntryStatus.WAITING
            )
            lotteryRepository.saveEntry(entry)
            userEntry = entry
            fetchData()
            null
        } catch (e: DuplicateLotteryEntryException) {
            error = "You have already joined this lottery."
            error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Unable to join lottery. Please try again."
            error
        }
    }

    companion object {
        fun factory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryDetailsController(
                    lotteryRepository = app.container.lotteryRepository,
                    profileRepository = app.container.profileRepository,
                    lotteryId = lotteryId,
                    currentUserId = app.container.deviceId
                )
            }
        }
    }
}
