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
     * doesn't send anyone a second notification, but a notification already saved keeps its
     * [Notification.read] and [Notification.shownOnDevice], so a read one stays read. Firestore
     * saves them in transactions of up to 500, which need a connection; if a later one fails, the
     * earlier ones stay saved, and calling again with the same list finishes the job. Does nothing
     * for an empty list.
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

    /**
     * Returns [recipientId]'s notifications that haven't been shown as a phone notification yet
     * ([Notification.shownOnDevice] is false), oldest first by [Notification.createdAt] (T-14).
     */
    suspend fun listUnshownOnDevice(recipientId: String): List<Notification>

    /**
     * Sets [Notification.shownOnDevice] to true on the notification with this [notificationId],
     * only if it is still false, and changes no other field (T-14). Returns true if it changed
     * the notification, or false if there is no such notification or it was already shown, so
     * two checks running at once post it only once. Firestore does this in a transaction, which
     * needs a connection.
     */
    suspend fun markShownOnDevice(notificationId: String): Boolean
}
