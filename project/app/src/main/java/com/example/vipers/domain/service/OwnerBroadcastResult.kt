/*
 * OwnerBroadcastResult.kt
 * What NotificationService.sendOwnerBroadcast tells the Send Notification screen (US 02.10.01):
 * how many entrants got the owner's message, or why nothing was sent.
 * Known issues: none. A failed save (e.g. offline) is thrown by NotificationRepository.addAll
 * instead of being one of these results.
 */
package com.example.vipers.domain.service

/** The outcome of one owner broadcast to a lottery's waiting list (US 02.10.01). */
sealed interface OwnerBroadcastResult {

    /**
     * The message was saved to the inboxes of [recipientCount] entrants, all sharing [batchId].
     *
     * @property batchId Groups the notifications of this send (US 02.10.02, 03.08.01).
     * @property recipientCount How many entrants got the message.
     */
    data class Sent(val batchId: String, val recipientCount: Int) : OwnerBroadcastResult

    /** Nothing was sent: the waiting list is empty or everyone on it turned notifications off. */
    data object NobodyToNotify : OwnerBroadcastResult

    /** Nothing was sent: the message was empty or only whitespace. */
    data object EmptyMessage : OwnerBroadcastResult

    /** Nothing was sent: the lottery doesn't exist or was removed. */
    data object LotteryNotFound : OwnerBroadcastResult

    /** Nothing was sent: the sender isn't the lottery's owner. */
    data object NotOwner : OwnerBroadcastResult
}
