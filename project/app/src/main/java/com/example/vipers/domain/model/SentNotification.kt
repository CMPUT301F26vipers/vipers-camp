/*
 * SentNotification.kt
 * One send of an owner's message, as a row on the owner's Sent Notifications list (US 02.10.02):
 * the notifications from one send grouped by their batch ID, with how many entrants got it.
 * The admin notification log (03.08.01) can group notifications the same way.
 * Known issues: none. Worked out from saved notifications, never stored.
 */
package com.example.vipers.domain.model

/**
 * One send: every [Notification] saved by one owner broadcast, which share one batch ID
 * (US 02.10.02). A notification with no batch ID counts as a send of its own.
 *
 * @property batchId The batch ID the send's notifications share, or the notification's own ID
 * when it has no batch ID.
 * @property senderId Device ID of who sent it.
 * @property message The text that was sent.
 * @property lotteryId The lottery whose waiting list got it, if any.
 * @property campsiteId The campsite it is about, if the lottery covers only one.
 * @property sentAt When it was sent: the earliest createdAt in the send, in milliseconds since 1970.
 * @property recipientCount How many different users got it.
 */
data class SentNotification(
    val batchId: String = "",
    val senderId: String = "",
    val message: String = "",
    val lotteryId: String? = null,
    val campsiteId: String? = null,
    val sentAt: Long = 0,
    val recipientCount: Int = 0,
) {
    companion object {

        /**
         * [notifications] grouped into sends, one [SentNotification] per batch ID (US 02.10.02).
         * A notification with a null or blank batch ID is a send of its own, keyed by its ID.
         * The recipient count is the number of different recipients in a send, so a user saved
         * twice in one send counts once. The message, sender, lottery and campsite come from the
         * send, and [SentNotification.sentAt] is its earliest createdAt. Newest send first, and by
         * batch ID when two were sent at the same time, so the order never jumps around.
         * Doesn't filter by type or sender; callers pass only the notifications they want.
         */
        fun fromNotifications(notifications: List<Notification>): List<SentNotification> =
            notifications.groupBy { it.batchId?.ifBlank { null } ?: it.id }
                .map { (batchId, group) ->
                    val first = group.first()
                    SentNotification(
                        batchId = batchId,
                        senderId = first.senderId,
                        message = first.message,
                        lotteryId = first.lotteryId,
                        campsiteId = first.campsiteId,
                        sentAt = group.minOf { it.createdAt },
                        recipientCount = group.map { it.recipientId }.distinct().size,
                    )
                }
                .sortedWith(compareByDescending<SentNotification> { it.sentAt }.thenBy { it.batchId })
    }
}
