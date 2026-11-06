/*
 * FakeNotificationRepository.kt
 * An in-memory NotificationRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.Subscription

/** Keeps notifications in a map instead of Firestore. Pass [initial] to start with some. */
class FakeNotificationRepository(initial: List<Notification> = emptyList()) : NotificationRepository {

    private val notifications = initial.associateBy { it.id }.toMutableMap()
    private var nextId = 1

    // each live inbox listener, with the user whose inbox it gets
    private val listeners = mutableListOf<Pair<String, InboxListener>>()

    override suspend fun addNotification(notification: Notification): String {
        val id = "notification-${nextId++}"
        notifications[id] = notification.copy(id = id)
        notifyListeners(notification.recipientId)
        return id
    }

    override suspend fun saveNotification(notification: Notification): String {
        val id = notification.id.ifBlank { "notification-${nextId++}" }
        notifications[id] = notification.copy(id = id)
        notifyListeners(notification.recipientId)
        return id
    }

    override suspend fun addAll(notifications: List<Notification>) {
        // check every ID first, so a bad one means nothing is written, like the Firestore batch
        require(notifications.none { it.id.isBlank() }) { "Every notification needs an id" }
        for (notification in notifications) {
            this.notifications[notification.id] = notification
        }
        // each changed inbox hears once, after the whole batch
        for (recipientId in notifications.map { it.recipientId }.distinct()) {
            notifyListeners(recipientId)
        }
    }

    override suspend fun getNotificationsFor(recipientId: String): List<Notification> = inboxOf(recipientId)

    override suspend fun markRead(id: String) {
        val notification = notifications[id] ?: return
        notifications[id] = notification.copy(read = true)
        notifyListeners(notification.recipientId)
    }

    override fun listenToInbox(recipientId: String, listener: InboxListener): Subscription {
        val entry = recipientId to listener
        listeners.add(entry)
        listener.onInboxChanged(inboxOf(recipientId))
        return Subscription { listeners.remove(entry) }
    }

    private fun inboxOf(recipientId: String): List<Notification> =
        notifications.values.filter { it.recipientId == recipientId }.sortedByDescending { it.createdAt }

    // Like Firestore, only the listeners on the changed inbox hear about it.
    private fun notifyListeners(recipientId: String) {
        for ((user, listener) in listeners.toList()) {
            if (user == recipientId) listener.onInboxChanged(inboxOf(recipientId))
        }
    }
}
