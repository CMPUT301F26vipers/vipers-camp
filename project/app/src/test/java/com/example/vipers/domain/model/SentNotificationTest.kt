/*
 * SentNotificationTest.kt
 * Unit tests for grouping notifications into sends for the owner's Sent Notifications list
 * (US 02.10.02): one row per batch with its recipient count, newest first in a stable order,
 * duplicate recipients counted once, and notifications with no batch ID as sends of their own.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentNotificationTest {

    private fun broadcast(
        id: String,
        recipientId: String,
        batchId: String?,
        createdAt: Long,
        message: String = "The cookhouse reopens next week.",
        lotteryId: String? = "hinton-may",
        campsiteId: String? = "hinton",
    ) = Notification(
        id = id,
        recipientId = recipientId,
        senderId = "owner-priya",
        campsiteId = campsiteId,
        lotteryId = lotteryId,
        type = NotificationType.OWNER_BROADCAST,
        title = "Message from owner",
        message = message,
        batchId = batchId,
        createdAt = createdAt,
    )

    @Test
    fun fromNotifications_oneBatchOfThree_isOneRowWithThreeRecipients() {
        val rows = SentNotification.fromNotifications(
            listOf(
                broadcast("broadcast_b1_sam", "sam", "b1", 1_000),
                broadcast("broadcast_b1_jo", "jo", "b1", 1_000),
                broadcast("broadcast_b1_riley", "riley", "b1", 1_000),
            ),
        )

        assertEquals(
            listOf(
                SentNotification(
                    batchId = "b1",
                    senderId = "owner-priya",
                    message = "The cookhouse reopens next week.",
                    lotteryId = "hinton-may",
                    campsiteId = "hinton",
                    sentAt = 1_000,
                    recipientCount = 3,
                ),
            ),
            rows,
        )
    }

    @Test
    fun fromNotifications_sentAtIsTheEarliestTimeInTheBatch() {
        val rows = SentNotification.fromNotifications(
            listOf(
                broadcast("broadcast_b1_sam", "sam", "b1", 2_000),
                broadcast("broadcast_b1_jo", "jo", "b1", 1_000),
            ),
        )

        assertEquals(1_000L, rows.single().sentAt)
    }

    @Test
    fun fromNotifications_twoBatches_newestFirst() {
        val rows = SentNotification.fromNotifications(
            listOf(
                broadcast("broadcast_old_sam", "sam", "old", 1_000, message = "First"),
                broadcast("broadcast_new_sam", "sam", "new", 5_000, message = "Second"),
                broadcast("broadcast_old_jo", "jo", "old", 1_000, message = "First"),
            ),
        )

        assertEquals(listOf("new", "old"), rows.map { it.batchId })
        assertEquals(listOf("Second", "First"), rows.map { it.message })
        assertEquals(listOf(1, 2), rows.map { it.recipientCount })
    }

    @Test
    fun fromNotifications_sameSentAt_comesBackInTheSameOrderWhateverTheInputOrder() {
        val a = broadcast("broadcast_a_sam", "sam", "a", 1_000)
        val b = broadcast("broadcast_b_sam", "sam", "b", 1_000)
        val c = broadcast("broadcast_c_sam", "sam", "c", 1_000)

        val forwards = SentNotification.fromNotifications(listOf(a, b, c))
        val backwards = SentNotification.fromNotifications(listOf(c, b, a))

        assertEquals(listOf("a", "b", "c"), forwards.map { it.batchId })
        assertEquals(forwards, backwards)
    }

    @Test
    fun fromNotifications_duplicateRecipientInOneBatch_isCountedOnce() {
        val rows = SentNotification.fromNotifications(
            listOf(
                broadcast("broadcast_b1_sam", "sam", "b1", 1_000),
                broadcast("copy_b1_sam", "sam", "b1", 1_000),
                broadcast("broadcast_b1_jo", "jo", "b1", 1_000),
            ),
        )

        assertEquals(2, rows.single().recipientCount)
    }

    @Test
    fun fromNotifications_nullAndBlankBatchIds_areSeparateOneRecipientRows() {
        val rows = SentNotification.fromNotifications(
            listOf(
                broadcast("n1", "sam", null, 2_000),
                broadcast("n2", "jo", "", 1_000),
                broadcast("n3", "riley", "  ", 3_000),
            ),
        )

        assertEquals(listOf("n3", "n1", "n2"), rows.map { it.batchId })
        assertTrue(rows.all { it.recipientCount == 1 })
    }

    @Test
    fun fromNotifications_noBatchId_keepsTheNotificationsOwnFields() {
        val row = SentNotification.fromNotifications(
            listOf(broadcast("n1", "sam", null, 2_000, lotteryId = null, campsiteId = null)),
        ).single()

        assertEquals("owner-priya", row.senderId)
        assertEquals("The cookhouse reopens next week.", row.message)
        assertEquals(null, row.lotteryId)
        assertEquals(null, row.campsiteId)
        assertEquals(2_000L, row.sentAt)
    }

    @Test
    fun fromNotifications_emptyList_isEmpty() {
        assertTrue(SentNotification.fromNotifications(emptyList()).isEmpty())
    }
}
