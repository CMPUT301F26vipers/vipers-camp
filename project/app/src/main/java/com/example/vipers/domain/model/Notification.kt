/*
 * Notification.kt
 * A message in a user's in-app inbox: lottery results, reservation updates and owner messages.
 * Stored in the Firestore "notifications" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * One item in a user's notification inbox.
 *
 * @property id Firestore document ID.
 * @property recipientId Device ID of the user it is for.
 * @property senderId Device ID of the sender, or "system" for automatic ones.
 * @property campsiteId The campsite it is about, if any.
 * @property type What it is about; decides which screen a tap opens.
 * @property title Short heading, e.g. "You won the lottery!".
 * @property message Body text.
 * @property read True once the user has opened it.
 * @property shownOnDevice True once it was shown as a phone notification.
 * @property batchId Groups the notifications from one owner broadcast, or null.
 * @property createdAt When it was sent, in milliseconds since 1970.
 */
data class Notification(
    val id: String = "",
    val recipientId: String = "",
    val senderId: String = "system",
    val campsiteId: String? = null,
    val type: NotificationType = NotificationType.WON,
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    val shownOnDevice: Boolean = false,
    val batchId: String? = null,
    val createdAt: Long = 0,
)
