/*
 * FakeNotificationRepository.kt
 * An in-memory NotificationRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.repository.NotificationRepository

/** Keeps notifications in a map instead of Firestore. Pass [initial] to start with some. */
class FakeNotificationRepository(initial: List<Notification> = emptyList()) : NotificationRepository {

    private val notifications = initial.associateBy { it.id }.toMutableMap()
    private var nextId = 1

    override suspend fun addNotification(notification: Notification): String {
        val id = "notification-${nextId++}"
        notifications[id] = notification.copy(id = id)
        return id
    }

    override suspend fun getNotificationsFor(recipientId: String): List<Notification> =
        notifications.values.filter { it.recipientId == recipientId }.sortedByDescending { it.createdAt }

    override suspend fun markRead(id: String) {
        notifications[id]?.let { notifications[id] = it.copy(read = true) }
    }
}
