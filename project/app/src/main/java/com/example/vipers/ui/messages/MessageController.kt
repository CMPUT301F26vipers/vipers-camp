/*
 * MessageController.kt
 * The controller (a ViewModel) behind the Messages screen (mockup E17, US 02.12.01): one
 * reservation's conversation between its entrant and the campsite's owner. It checks the user is
 * one of the two, listens to the conversation while the screen is open, sends new messages and
 * asks NotificationService to tell the other side.
 * Known issues: none. Both sides can use it; the entrant's "Message Owner" button comes with
 * US 01.14.01, and the admin's read-only view with 03.12.01.
 */
package com.example.vipers.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.repository.Subscription
import com.example.vipers.domain.repository.ThreadListener
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds the conversation about reservation [reservationId] for the user [userId], who must be its
 * entrant or the campsite's owner (US 02.12.01). Takes the repositories and the service in its
 * constructor so tests can pass fake ones.
 */
class MessageController(
    private val messageRepository: MessageRepository,
    private val reservationRepository: ReservationRepository,
    private val profileRepository: ProfileRepository,
    private val notificationService: NotificationService,
    private val userId: String,
    private val reservationId: String,
) : ViewModel(), ThreadListener {

    /**
     * The reservation, or null until it loads, if it wasn't found, or if [userId] isn't its
     * entrant or owner ([Reservation.canMessage]), so nobody else can read the conversation.
     */
    var reservation by mutableStateOf<Reservation?>(null)
        private set

    /** The other person's current name, shown in the top bar and on their messages. */
    var otherName by mutableStateOf("")
        private set

    /** The conversation, oldest first. */
    var messages by mutableStateOf(emptyList<Message>())
        private set

    /** True until the reservation and the conversation have loaded. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the last load failed, e.g. because the phone is offline. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The text in the message box. */
    var draft by mutableStateOf("")
        private set

    /** True while a message is being sent. */
    var sending by mutableStateOf(false)
        private set

    /** "Couldn't send..." if the last send failed, otherwise null. */
    var sendError by mutableStateOf<String?>(null)
        private set

    /**
     * True if the send button works: the conversation is open, the text isn't empty
     * ([Message.canSend]) and nothing is sending.
     */
    val canSend: Boolean
        get() = reservation != null && !sending && Message.canSend(draft)

    private var subscription: Subscription? = null

    /** Shows the text typed in the message box and hides an old send error. */
    fun onDraftChange(text: String) {
        draft = text
        sendError = null
    }

    /** "You" for [message]s the user sent, and the other person's name for the rest. */
    fun senderName(message: Message): String = if (message.isFrom(userId)) "You" else otherName

    /** True if the user sent [message], so the screen puts it on the right. */
    fun isMine(message: Message): Boolean = message.isFrom(userId)

    /** Loads the reservation and starts listening to the conversation in the background. */
    fun loadThread() {
        viewModelScope.launch { fetchThread() }
    }

    /**
     * Gets the reservation and the other person's name, and if the user can message about it,
     * starts listening to the conversation ([onThreadChanged] then ends loading). Sets [loading]
     * and [error]. [loadThread] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchThread() {
        loading = true
        error = null
        try {
            val loaded = reservationRepository.getReservation(reservationId)
                ?.takeIf { it.canMessage(userId) }
            if (loaded != null) {
                // the other side's name is looked up, never copied (it may have changed)
                otherName = profileRepository.getDisplayName(loaded.otherSide(userId) ?: userId)
            }
            reservation = loaded
            if (loaded == null) {
                loading = false
            } else {
                subscription?.cancel()
                subscription = messageRepository.listenToThread(reservationId, this)
            }
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            error = "Couldn't load the messages"
            loading = false
        }
    }

    /** Shows the latest conversation and ends loading. */
    override fun onThreadChanged(messages: List<Message>) {
        this.messages = messages
        error = null
        loading = false
    }

    /** Ends loading and sets [error], keeping any messages already shown. */
    override fun onThreadError() {
        error = "Couldn't load the messages"
        loading = false
    }

    /** Sends the [draft] in the background. */
    fun send() {
        viewModelScope.launch { sendMessage() }
    }

    /**
     * Saves the [draft], trimmed, as a message from the user sent at [now] (milliseconds since
     * 1970), clears the box, and then tells the other side ([NotificationService.sendNewMessage]).
     * Does nothing if [canSend] is false. If saving fails the text stays in the box and
     * [sendError] says so; if only the notice fails the message is still sent. [sending] ends
     * before the notice, so a notice still waiting for the connection doesn't hold up the next
     * message. [send] runs this in the background; unit tests call it directly.
     */
    suspend fun sendMessage(now: Long = System.currentTimeMillis()) {
        val current = reservation ?: return
        if (!canSend) return
        val typed = draft
        val message = Message(
            reservationId = reservationId,
            senderId = userId,
            text = typed.trim(),
            createdAt = now,
        )
        sending = true
        sendError = null
        val id = try {
            messageRepository.addMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendError = "Couldn't send. Check your connection and try again."
            return
        } finally {
            sending = false
        }
        // keep anything typed while it was sending
        if (draft == typed) draft = ""
        tellOtherSide(current, message.copy(id = id))
    }

    // The message is already saved, so a notice that fails doesn't make the send fail.
    private suspend fun tellOtherSide(reservation: Reservation, message: Message) {
        try {
            notificationService.sendNewMessage(reservation, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the other side still sees the message when they open the conversation
        }
    }

    override fun onCleared() {
        subscription?.cancel()
        super.onCleared()
    }

    companion object {
        /** Makes the controller for [reservationId] with the app's repositories and device ID. */
        fun factory(reservationId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                MessageController(
                    messageRepository = app.container.messageRepository,
                    reservationRepository = app.container.reservationRepository,
                    profileRepository = app.container.profileRepository,
                    notificationService = NotificationService(
                        app.container.notificationRepository,
                        app.container.campsiteRepository,
                        app.container.profileRepository,
                    ),
                    userId = app.container.deviceId,
                    reservationId = reservationId,
                )
            }
        }
    }
}
