/*
 * Notification.kt
 * A message in a user's in-app inbox: lottery results, reservation updates and owner messages.
 * Stored in the Firestore "notifications" collection.
 * Known issues: none. target and displayCategory are worked out from the type, never stored.
 */
package com.example.vipers.domain.model

/**
 * One item in a user's notification inbox.
 *
 * @property id Firestore document ID.
 * @property recipientId Device ID of the user it is for.
 * @property senderId Device ID of the sender, or "system" for automatic ones.
 * @property campsiteId The campsite it is about, if any.
 * @property lotteryId The lottery it is about, if any; a WON tap opens Reserve for this lottery.
 * @property reservationId The reservation it is about, if any; tapping opens its details.
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
    val lotteryId: String? = null,
    val reservationId: String? = null,
    val type: NotificationType = NotificationType.WON,
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    val shownOnDevice: Boolean = false,
    val batchId: String? = null,
    val createdAt: Long = 0,
) {
    /**
     * The screen tapping this notification opens (T-13): Reserve for a win, the reservation for
     * reservation updates, its thread for a new message, and the campsite for everything else.
     * Null if the ID that [type] needs is missing. Worked out, never stored.
     */
    val target: NotificationTarget?
        get() = when (type) {
            NotificationType.WON -> lotteryId?.let { NotificationTarget.OpenReserve(it) }
            NotificationType.RESERVATION_CONFIRMED,
            NotificationType.RESERVATION_CANCELLED,
            NotificationType.RESERVATION_REJECTED ->
                reservationId?.let { NotificationTarget.OpenReservation(it) }
            NotificationType.NEW_MESSAGE -> reservationId?.let { NotificationTarget.OpenThread(it) }
            NotificationType.OWNER_BROADCAST,
            NotificationType.CAMPSITE_REMOVED,
            NotificationType.NOT_SELECTED -> campsiteId?.let { NotificationTarget.OpenCampsite(it) }
        }

    /**
     * The kind of notification as shown in the inbox, e.g. "Won" or "Reservation" (T-13,
     * mockup E10). Worked out from [type], never stored.
     */
    val displayCategory: String
        get() = when (type) {
            NotificationType.WON -> "Won"
            NotificationType.NOT_SELECTED -> "Not selected"
            NotificationType.RESERVATION_CONFIRMED,
            NotificationType.RESERVATION_CANCELLED,
            NotificationType.RESERVATION_REJECTED -> "Reservation"
            NotificationType.OWNER_BROADCAST -> "Owner message"
            NotificationType.NEW_MESSAGE -> "Message"
            NotificationType.CAMPSITE_REMOVED -> "Campsite removed"
        }
}
