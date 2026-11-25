/*
 * NotificationLogEntry.kt
 * One row of the admin Notification Log (US 03.08.01, mockup A09): one send, which is the
 * notifications from one owner broadcast, one draw result, or a single notice, with its recipients.
 * Known issues: none. Worked out from saved notifications, never stored. Names are looked up later.
 */
package com.example.vipers.domain.model

/**
 * One send in the admin Notification Log (US 03.08.01): every [Notification] that went out together.
 *
 * @property key What the send's notifications share: "batch:<batchId>", "draw:<lotteryId>:<type>"
 * or "notification:<id>". Unique within one log, so it can key a list row.
 * @property type The type of the send's newest notification.
 * @property senderId Device ID of who sent it, or "system", as stored.
 * @property recipientIds The different users who got it.
 * @property campsiteId The campsite it is about, if any.
 * @property lotteryId The lottery it is about, if any.
 * @property title The newest notification's title.
 * @property message The newest notification's text.
 * @property time When it was sent: the newest createdAt in the send, in milliseconds since 1970.
 * @property notificationIds The IDs of the send's notifications, newest first.
 */
data class NotificationLogEntry(
    val key: String,
    val type: NotificationType,
    val senderId: String,
    val recipientIds: List<String>,
    val campsiteId: String?,
    val lotteryId: String?,
    val title: String,
    val message: String,
    val time: Long,
    val notificationIds: List<String>,
) {
    /** How many different users got this send. */
    val recipientCount: Int
        get() = recipientIds.size

    /** The type as the log shows it, e.g. "Won" or "Reservation rejected". */
    val typeLabel: String
        get() = labelFor(type)

    companion object {

        /** The label the log shows for [type], e.g. "Owner message" for [NotificationType.OWNER_BROADCAST]. */
        fun labelFor(type: NotificationType): String = when (type) {
            NotificationType.WON -> "Won"
            NotificationType.NOT_SELECTED -> "Not selected"
            NotificationType.RESERVATION_CONFIRMED -> "Reservation confirmed"
            NotificationType.RESERVATION_CANCELLED -> "Reservation cancelled"
            NotificationType.RESERVATION_REJECTED -> "Reservation rejected"
            NotificationType.OWNER_BROADCAST -> "Owner message"
            NotificationType.NEW_MESSAGE -> "Message"
            NotificationType.CAMPSITE_REMOVED -> "Campsite removed"
        }

        /**
         * [notifications] grouped into log rows, newest first (US 03.08.01). Notifications sharing a
         * batch ID are one row; otherwise a draw's WON notices (ID "draw_<lotteryId>_<userId>") are
         * one row and its NOT_SELECTED notices another; anything else is a row of its own. A row's
         * type, sender, title, text and time come from its newest notification, and its campsite
         * and lottery from the newest one that has them. Rows sent at the same time are ordered by
         * [NotificationLogEntry.key], so the order never changes between loads.
         */
        fun fromNotifications(notifications: List<Notification>): List<NotificationLogEntry> =
            notifications.groupBy(::groupKey)
                .map { (key, group) -> entryFor(key, group) }
                .sortedWith(compareByDescending<NotificationLogEntry> { it.time }.thenBy { it.key })

        private fun groupKey(notification: Notification): String {
            val batchId = notification.batchId
            val lotteryId = notification.lotteryId
            return when {
                batchId != null -> "batch:$batchId"
                lotteryId != null && isDrawResult(notification, lotteryId) ->
                    "draw:$lotteryId:${notification.type.name}"
                else -> "notification:${notification.id}"
            }
        }

        private fun isDrawResult(notification: Notification, lotteryId: String): Boolean =
            (notification.type == NotificationType.WON || notification.type == NotificationType.NOT_SELECTED) &&
                notification.id.startsWith("draw_${lotteryId}_")

        private fun entryFor(key: String, group: List<Notification>): NotificationLogEntry {
            val newestFirst = group.sortedWith(compareByDescending<Notification> { it.createdAt }.thenBy { it.id })
            val newest = newestFirst.first()
            return NotificationLogEntry(
                key = key,
                type = newest.type,
                senderId = newest.senderId,
                recipientIds = newestFirst.map { it.recipientId }.distinct(),
                campsiteId = newestFirst.firstNotNullOfOrNull { it.campsiteId },
                lotteryId = newestFirst.firstNotNullOfOrNull { it.lotteryId },
                title = newest.title,
                message = newest.message,
                time = newest.createdAt,
                notificationIds = newestFirst.map { it.id },
            )
        }
    }
}
