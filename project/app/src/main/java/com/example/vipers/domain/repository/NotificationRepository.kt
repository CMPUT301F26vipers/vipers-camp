/*
 * NotificationRepository.kt
 * What the app can do with inbox notifications, without saying how they are stored (Repository pattern).
 * Known issues: only the methods needed for the half-way checkpoint. Live inbox updates,
 * sent batches and the admin log are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Notification

/** Saves and loads [Notification]s. */
interface NotificationRepository {

    /** Saves a new [notification] and returns the ID it was given. */
    suspend fun addNotification(notification: Notification): String

    /** Loads [recipientId]'s inbox, newest first. */
    suspend fun getNotificationsFor(recipientId: String): List<Notification>

    /** Marks the notification with this [id] as read. */
    suspend fun markRead(id: String)
}
