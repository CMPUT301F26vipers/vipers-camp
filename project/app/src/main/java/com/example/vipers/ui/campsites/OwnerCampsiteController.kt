/*
 * OwnerCampsiteController.kt
 * The controller (a ViewModel) behind the owner's view of one campsite (mockup O04, US 02.01.04).
 * It loads the campsite with all its saved details and its lotteries (US 02.04.01), with how many
 * entrants joined or reserved each one, and works out whether this user may edit it (US 02.01.03).
 * It also saves the campsite's QR code as an image (US 02.02.01).
 * Known issues: this campsite's own reservations are not loaded here. The screen's Reservations
 * button opens the list for all of the current user's campsites (US 02.07.01).
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
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads one of the owner's campsites and its lotteries for the My Campsite screen.
 * Takes the [campsiteRepository] and [lotteryRepository] in its constructor so tests can pass
 * fake ones, the [campsiteId] of the campsite to show, and the [userId] of whoever is viewing it.
 */
class OwnerCampsiteController(
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val campsiteId: String,
    private val userId: String,
) : ViewModel() {

    /** The campsite with its latest saved details, or null until it loads or if it wasn't found. */
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    /**
     * True only when the campsite has loaded and belongs to [userId] (US 02.01.03). Admins also
     * open this screen from Browse Campsites, and they can't edit other owners' campsites.
     */
    val canEdit: Boolean
        get() = campsite?.isOwnedBy(userId) == true

    /** The campsite's lotteries. */
    var lotteries by mutableStateOf<List<Lottery>>(emptyList())
        private set

    /** Each lottery's entry count as text, e.g. "36 joined" or "3 reserved", by lottery ID. */
    var entrySummaries by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** True while the campsite is loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** "QR code saved" or an error after saving the QR code, or null when there's nothing to say. */
    var qrMessage by mutableStateOf<String?>(null)
        private set

    /** True when [qrMessage] is the error, so the screen can show it in red. */
    var qrSaveFailed by mutableStateOf(false)
        private set

    /**
     * Loads the campsite and its lotteries in the background. The screen calls this each time it
     * comes back into view, so it always shows the latest saved values (e.g. after Edit).
     */
    fun loadData() {
        viewModelScope.launch { fetchData() }
    }

    /**
     * Gets the campsite, its lotteries and their entry counts from the repositories, and sets
     * [loading] and [error].
     * [loadData] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchData() {
        loading = true
        error = null
        try {
            campsite = campsiteRepository.getCampsite(campsiteId)
            val loaded = lotteryRepository.getLotteriesForCampsite(campsiteId)
            entrySummaries = loaded.associate { lottery ->
                lottery.id to entrySummary(lotteryRepository.getEntries(lottery.id))
            }
            lotteries = loaded
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Unable to load details."
        } finally {
            loading = false
        }
    }

    /**
     * Saves the campsite's QR code as a PNG in the background (US 02.02.01). [openOutput] opens
     * the file the owner picked, or returns null if it can't be opened.
     */
    fun saveQrCode(openOutput: () -> OutputStream?) {
        viewModelScope.launch { writeQrCode(openOutput) }
    }

    /**
     * Writes the QR code for [campsite] with [write] to the stream from [openOutput], and sets
     * [qrMessage] to say whether it worked. Does nothing if the campsite hasn't loaded.
     * [saveQrCode] runs this in the background; unit tests call it directly with their own [write].
     */
    suspend fun writeQrCode(
        openOutput: () -> OutputStream?,
        write: (String, OutputStream) -> Boolean = ::writeQrCodePng,
    ) {
        val link = campsite?.qrLink() ?: return
        val saved = try {
            withContext(Dispatchers.IO) {
                openOutput()?.use { output -> write(link, output) } ?: false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. the phone is out of space or the file was removed while saving
            false
        }
        qrSaveFailed = !saved
        qrMessage = if (saved) "QR code saved" else "Couldn't save the QR code. Try again."
    }

    /** Clears [qrMessage] once the screen has shown it. */
    fun clearQrMessage() {
        qrMessage = null
        qrSaveFailed = false
    }

    // like mockup O04: "36 joined" while people sign up, "3 reserved" once winners start reserving
    private fun entrySummary(entries: List<LotteryEntry>): String {
        val reserved = entries.count { it.status == EntryStatus.RESERVED }
        if (reserved > 0) return "$reserved reserved"
        return "${entries.count { it.status != EntryStatus.REMOVED }} joined"
    }

    companion object {
        /** Makes the controller for [campsiteId] with the app's real repositories and this device's ID. */
        fun factory(campsiteId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                OwnerCampsiteController(
                    campsiteRepository = app.container.campsiteRepository,
                    lotteryRepository = app.container.lotteryRepository,
                    campsiteId = campsiteId,
                    userId = app.container.deviceId,
                )
            }
        }
    }
}
