/*
 * NotificationRepository.kt
 * What the app can do with inbox notifications, without saying how they are stored (Repository pattern).
 * Known issues: only the methods needed for the half-way checkpoint. Sent batches and the admin
 * log are added by those stories.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Notification

/** Saves and loads [Notification]s. */
interface NotificationRepository {

    /** Saves a new [notification] and returns the ID it was given. */
    suspend fun addNotification(notification: Notification): String

    /** Saves or updates [notification]. When [Notification.id] is set, writes to that ID idempotently. */
    suspend fun saveNotification(notification: Notification): String

    /**
     * Saves every notification in [notifications] under its own [Notification.id] (US 01.03.01).
     * Writing the same IDs again overwrites them instead of adding copies, so a retried draw
     * doesn't send anyone a second notification. Firestore saves them in batches of up to 500;
     * if a later batch fails, the earlier ones stay saved, and calling again with the same list
     * finishes the job. Does nothing for an empty list.
     *
     * @throws IllegalArgumentException if any notification has a blank ID; then nothing is saved.
     */
    suspend fun addAll(notifications: List<Notification>)

    /** Loads [recipientId]'s inbox, newest first. */
    suspend fun getNotificationsFor(recipientId: String): List<Notification>

    /** Marks the notification with this [id] as read. */
    suspend fun markRead(id: String)

    /**
     * Sends [recipientId]'s inbox, newest first, to [listener] straight away and again on every
     * change, until the returned [Subscription] is cancelled (T-13).
     */
    fun listenToInbox(recipientId: String, listener: InboxListener): Subscription
}
