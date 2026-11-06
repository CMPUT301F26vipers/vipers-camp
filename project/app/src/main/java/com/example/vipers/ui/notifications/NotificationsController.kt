/*
 * NotificationsController.kt
 * The controller (a ViewModel) behind the Notifications tab (mockup E10, T-13). It listens to the
 * user's inbox while the tab is alive, and marks a notification read when the user opens it.
 * Known issues: none. If marking read fails (e.g. Firestore rejects it), the item stays unread and
 * the tap still opens its target.
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
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds [userId]'s inbox for the Notifications tab, kept up to date by [notificationService]
 * (T-13). Takes the service in its constructor so tests can build it on a fake repository.
 */
class NotificationsController(
    private val notificationService: NotificationService,
    private val userId: String,
) : ViewModel(), InboxListener {

    /** The user's notifications, newest first (T-13). */
    var items by mutableStateOf(emptyList<Notification>())
        private set

    /** True until the inbox has arrived the first time (T-13). */
    var isLoading by mutableStateOf(true)
        private set

    private val subscription = notificationService.listenToInbox(userId, this)

    /** Shows the latest [items], newest first, and ends loading (T-13). */
    override fun onInboxChanged(items: List<Notification>) {
        this.items = items
        isLoading = false
    }

    /**
     * Opens [item]: marks it read in the background if it's unread, and returns the screen it
     * opens, or null if it has nowhere to go (T-13).
     */
    fun open(item: Notification): NotificationTarget? {
        if (!item.read) {
            viewModelScope.launch {
                try {
                    notificationService.markRead(item.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // stays unread; the inbox listener shows the saved state
                }
            }
        }
        return item.target
    }

    override fun onCleared() {
        subscription.cancel()
        super.onCleared()
    }

    companion object {
        /** Makes the controller with the app's real repository and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                NotificationsController(
                    NotificationService(app.container.notificationRepository),
                    app.container.deviceId,
                )
            }
        }
    }
}
