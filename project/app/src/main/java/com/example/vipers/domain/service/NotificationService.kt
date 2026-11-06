/*
 * NotificationService.kt
 * The app's notification logic, between the screens and NotificationRepository (T-13). For now it
 * only passes the inbox and marking read through to the repository.
 * Known issues: sending notifications is added by #16.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.Subscription

/** Reads a user's inbox and marks notifications read, using [notificationRepository] (T-13). */
class NotificationService(private val notificationRepository: NotificationRepository) {

    /**
     * Sends [userId]'s inbox, newest first, to [listener] straight away and on every change, until
     * the returned [Subscription] is cancelled (T-13).
     */
    fun listenToInbox(userId: String, listener: InboxListener): Subscription =
        notificationRepository.listenToInbox(userId, listener)

    /** Marks the notification with this [id] as read (T-13). */
    suspend fun markRead(id: String) = notificationRepository.markRead(id)
}
