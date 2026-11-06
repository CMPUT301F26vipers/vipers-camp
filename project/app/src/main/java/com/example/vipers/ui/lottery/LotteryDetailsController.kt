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
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
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
 * @param lotteryDrawer Optional drawer service to trigger or verify lottery draw.
 */
class LotteryDetailsController(
    private val lotteryRepository: LotteryRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryId: String,
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

    init {
        loadData()
    }

    /** Loads lottery details and resolves waitlist entry user display names in the background. */
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

    /** Loads lottery details and resolves waitlist entry user display names. Unit tests can call it directly. */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            lottery = lotteryRepository.getLottery(lotteryId)
            val all = lotteryRepository.getEntries(lotteryId)
            allEntries = all
            waitingEntries = all.filter { it.status == EntryStatus.WAITING }

            val names = mutableMapOf<String, String>()
            for (entry in all) {
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

    companion object {
        fun factory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                LotteryDetailsController(
                    lotteryRepository = app.container.lotteryRepository,
                    profileRepository = app.container.profileRepository,
                    lotteryId = lotteryId,
                    lotteryDrawer = app.container.lotteryDrawer,
                )
            }
        }
    }
}
