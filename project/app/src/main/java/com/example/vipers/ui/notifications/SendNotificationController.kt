/*
 * SendNotificationController.kt
 * The controller (a ViewModel) behind the owner's Send Notification screen (mockup O12,
 * US 02.10.01). It loads how many entrants are on the lottery's waiting list, holds the message
 * the owner types, and sends it to all of them through NotificationService. The CRC cards give
 * O12 only NotificationService as a collaborator; it has its own controller here because every
 * screen in the app has one.
 * Known issues: none. A send that fails partway keeps its batch, so sending again finishes it
 * without giving anyone a second copy.
 */
package com.example.vipers.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.domain.service.OwnerBroadcastResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Lets the owner [senderId] write a message and send it to everyone on [lotteryId]'s waiting list.
 *
 * @param notificationService Counts the waiting list and sends the message.
 * @param lotteryId The lottery whose waiting list gets the message.
 * @param senderId Device ID of the owner sending it.
 */
class SendNotificationController(
    private val notificationService: NotificationService,
    private val lotteryId: String,
    private val senderId: String,
) : ViewModel() {

    /**
     * How many entrants are on the waiting list, the same ones the Waiting List screen shows,
     * before opt-outs are removed; null until loaded.
     */
    var recipientCount by mutableStateOf<Int?>(null)
        private set

    /** True until the waiting-list count has loaded. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the count couldn't be loaded, or null. */
    var loadError by mutableStateOf<String?>(null)
        private set

    /** The message the owner has typed. */
    var message by mutableStateOf("")
        private set

    /** True while the message is being sent, so a second tap does nothing. */
    var isSending by mutableStateOf(false)
        private set

    /** How many entrants got the message once it was sent, or null until then. */
    var sentCount by mutableStateOf<Int?>(null)
        private set

    /** A message to show if sending failed or reached nobody, or null. The typed text is kept. */
    var sendError by mutableStateOf<String?>(null)
        private set

    private var pendingBatchId: String? = null

    /**
     * True when Send can be tapped: the count has loaded, someone is on the list, nothing is
     * being sent, and the message isn't empty or only whitespace ([Notification.ownerMessageText]).
     */
    val canSend: Boolean
        get() = !loading && !isSending && sentCount == null && (recipientCount ?: 0) > 0 &&
            Notification.ownerMessageText(message) != null

    /** The count under the info card, e.g. "36 entrants", "1 entrant" or "No entrants yet". */
    val countText: String
        get() = when (val count = recipientCount ?: 0) {
            0 -> "No entrants yet"
            else -> entrantsText(count)
        }

    /** The confirmation after sending, e.g. "Notification sent to 3 entrants", or null. */
    val sentText: String?
        get() = sentCount?.let { "Notification sent to ${entrantsText(it)}" }

    init {
        viewModelScope.launch { load() }
    }

    /** Loads [recipientCount] from the waiting list. Unit tests can call it directly. */
    suspend fun load() {
        loading = true
        loadError = null
        try {
            recipientCount = notificationService.waitingListRecipients(lotteryId).size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "Couldn't load the waiting list. Please try again."
        } finally {
            loading = false
        }
    }

    /** Changes the typed message to [text] and clears an old send error. */
    fun onMessageChange(text: String) {
        message = text
        sendError = null
    }

    /** Sends the message in the background. The screen calls this when Send is tapped. */
    fun send() {
        viewModelScope.launch { sendMessage() }
    }

    /**
     * Sends [message] to the waiting list at time [now] (milliseconds since 1970) with
     * [NotificationService.sendOwnerBroadcast], and sets [sentCount] or [sendError]. Does nothing
     * unless [canSend], so a second tap while sending can't send twice. If saving fails, the
     * message is kept and the next send reuses the same batch, so entrants who already got it
     * don't get a second copy. [send] runs this in the background; unit tests call it directly.
     */
    suspend fun sendMessage(now: Long = System.currentTimeMillis()) {
        if (!canSend) return
        isSending = true
        sendError = null
        val batchId = pendingBatchId ?: UUID.randomUUID().toString().also { pendingBatchId = it }
        try {
            when (val result = notificationService.sendOwnerBroadcast(senderId, lotteryId, message, batchId, now)) {
                is OwnerBroadcastResult.Sent -> {
                    pendingBatchId = null
                    sentCount = result.recipientCount
                }
                OwnerBroadcastResult.NobodyToNotify ->
                    sendError = "No one on this waiting list can get notifications right now."
                OwnerBroadcastResult.EmptyMessage -> sendError = "Write a message first."
                OwnerBroadcastResult.LotteryNotFound -> sendError = "This lottery no longer exists."
                OwnerBroadcastResult.NotOwner -> sendError = "Only the lottery's owner can send notifications."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendError = "Couldn't send the notification. Check your connection and try again."
        } finally {
            isSending = false
        }
    }

    private fun entrantsText(count: Int): String = if (count == 1) "1 entrant" else "$count entrants"

    companion object {
        /** Makes a [SendNotificationController] for [lotteryId] with the app's repositories and this device's ID. */
        fun factory(lotteryId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                SendNotificationController(
                    notificationService = NotificationService(
                        app.container.notificationRepository,
                        app.container.campsiteRepository,
                        app.container.profileRepository,
                        app.container.lotteryRepository,
                    ),
                    lotteryId = lotteryId,
                    senderId = app.container.deviceId,
                )
            }
        }
    }
}
