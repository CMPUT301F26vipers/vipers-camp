/*
 * NotificationLogEntryTest.kt
 * Unit tests for grouping notifications into admin Notification Log rows and their type labels (US 03.08.01).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogEntryTest {

    private val drawTime = 1_795_000_000_000L
    private val minute = 60_000L

    private fun broadcast(userId: String, createdAt: Long = drawTime, message: String = "Hinton Ridge Campground: Bring bear spray.") =
        Notification(
            id = "broadcast_batch-1_$userId",
            recipientId = userId,
            senderId = "seed-owner-priya",
            campsiteId = "hinton-ridge",
            lotteryId = "lottery-hinton-open",
            type = NotificationType.OWNER_BROADCAST,
            title = "Message from owner",
            message = message,
            batchId = "batch-1",
            createdAt = createdAt,
        )

    private fun drawResult(userId: String, type: NotificationType) = Notification(
        id = "draw_lottery-banff_$userId",
        recipientId = userId,
        campsiteId = "bow-river",
        lotteryId = "lottery-banff",
        type = type,
        title = if (type == NotificationType.WON) "You won the lottery!" else "Not selected this time",
        message = if (type == NotificationType.WON) {
            "You won a spot at Bow River Sites. Reserve your nights before May 10, 2027."
        } else {
            "You weren't selected for Bow River Sites this time."
        },
        createdAt = drawTime,
    )

    private fun reservationNotice(reservationId: String, type: NotificationType, createdAt: Long) = Notification(
        id = "${if (type == NotificationType.RESERVATION_REJECTED) "rejected" else "cancelled"}_$reservationId",
        recipientId = "seed-entrant-sam",
        senderId = if (type == NotificationType.RESERVATION_REJECTED) "seed-owner-daniel" else "system",
        campsiteId = "kananaskis-lakes",
        reservationId = reservationId,
        type = type,
        title = "Reservation update",
        message = "Kananaskis Lakes Sites: 2027-06-01 to 2027-06-04",
        createdAt = createdAt,
    )

    @Test
    fun fromNotifications_ownerBroadcastToThreeRecipients_isOneRowWithThreeRecipients() {
        val rows = NotificationLogEntry.fromNotifications(
            listOf(broadcast("seed-entrant-sam"), broadcast("user-alex"), broadcast("user-kim")),
        )

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("batch:batch-1", row.key)
        assertEquals(NotificationType.OWNER_BROADCAST, row.type)
        assertEquals("seed-owner-priya", row.senderId)
        assertEquals(3, row.recipientCount)
        assertEquals(setOf("seed-entrant-sam", "user-alex", "user-kim"), row.recipientIds.toSet())
        assertEquals("hinton-ridge", row.campsiteId)
        assertEquals("lottery-hinton-open", row.lotteryId)
        assertEquals("Hinton Ridge Campground: Bring bear spray.", row.message)
        assertEquals(3, row.notificationIds.size)
    }

    @Test
    fun fromNotifications_drawWithTwoWinnersAndThreeLosers_isOneWonRowAndOneNotSelectedRow() {
        val rows = NotificationLogEntry.fromNotifications(
            listOf(
                drawResult("seed-entrant-sam", NotificationType.WON),
                drawResult("user-alex", NotificationType.WON),
                drawResult("user-kim", NotificationType.NOT_SELECTED),
                drawResult("user-lee", NotificationType.NOT_SELECTED),
                drawResult("user-ravi", NotificationType.NOT_SELECTED),
            ),
        )

        assertEquals(2, rows.size)
        val won = rows.single { it.type == NotificationType.WON }
        val lost = rows.single { it.type == NotificationType.NOT_SELECTED }
        assertEquals("draw:lottery-banff:WON", won.key)
        assertEquals(2, won.recipientCount)
        assertEquals("Won", won.typeLabel)
        assertEquals("system", won.senderId)
        assertEquals("lottery-banff", won.lotteryId)
        assertEquals("draw:lottery-banff:NOT_SELECTED", lost.key)
        assertEquals(3, lost.recipientCount)
        assertEquals("Not selected", lost.typeLabel)
    }

    @Test
    fun fromNotifications_wonWithoutDrawId_staysItsOwnRow() {
        val replacement = drawResult("user-kim", NotificationType.WON)
            .copy(id = "replacement_lottery-banff_user-kim", createdAt = drawTime + minute)

        val rows = NotificationLogEntry.fromNotifications(
            listOf(
                drawResult("seed-entrant-sam", NotificationType.WON),
                drawResult("user-alex", NotificationType.WON),
                replacement,
            ),
        )

        assertEquals(2, rows.size)
        assertEquals("notification:replacement_lottery-banff_user-kim", rows[0].key)
        assertEquals(listOf("user-kim"), rows[0].recipientIds)
        assertEquals(2, rows[1].recipientCount)
    }

    @Test
    fun fromNotifications_reservationNotices_areEachTheirOwnRow() {
        val rows = NotificationLogEntry.fromNotifications(
            listOf(
                reservationNotice("res-20417", NotificationType.RESERVATION_CANCELLED, drawTime),
                reservationNotice("res-20431", NotificationType.RESERVATION_REJECTED, drawTime + minute),
                reservationNotice("res-19882", NotificationType.RESERVATION_CANCELLED, drawTime + 2 * minute),
            ),
        )

        assertEquals(
            listOf("notification:cancelled_res-19882", "notification:rejected_res-20431", "notification:cancelled_res-20417"),
            rows.map { it.key },
        )
        assertTrue(rows.all { it.recipientCount == 1 })
        assertEquals("seed-owner-daniel", rows[1].senderId)
    }

    @Test
    fun fromNotifications_rowsAreNewestFirstWithTimeAndTextFromTheNewestItem() {
        val reservation = reservationNotice("res-20417", NotificationType.RESERVATION_CANCELLED, drawTime + 2 * minute)
        val rows = NotificationLogEntry.fromNotifications(
            listOf(
                drawResult("seed-entrant-sam", NotificationType.WON),
                broadcast("user-alex", createdAt = drawTime + minute, message = "Hinton Ridge Campground: Old text"),
                reservation,
                broadcast("user-kim", createdAt = drawTime + 3 * minute, message = "Hinton Ridge Campground: New text"),
            ),
        )

        assertEquals(
            listOf("batch:batch-1", "notification:cancelled_res-20417", "draw:lottery-banff:WON"),
            rows.map { it.key },
        )
        assertEquals(drawTime + 3 * minute, rows[0].time)
        assertEquals("Hinton Ridge Campground: New text", rows[0].message)
        assertEquals(listOf("broadcast_batch-1_user-kim", "broadcast_batch-1_user-alex"), rows[0].notificationIds)
    }

    @Test
    fun fromNotifications_rowsAtTheSameTime_areOrderedByKey() {
        val notifications = listOf(
            reservationNotice("res-20431", NotificationType.RESERVATION_REJECTED, drawTime),
            drawResult("seed-entrant-sam", NotificationType.WON),
            broadcast("user-alex"),
        )

        val rows = NotificationLogEntry.fromNotifications(notifications)

        assertEquals(
            listOf("batch:batch-1", "draw:lottery-banff:WON", "notification:rejected_res-20431"),
            rows.map { it.key },
        )
        assertEquals(rows, NotificationLogEntry.fromNotifications(notifications.reversed()))
    }

    @Test
    fun typeLabel_everyType_isTheLogLabel() {
        val expected = mapOf(
            NotificationType.WON to "Won",
            NotificationType.NOT_SELECTED to "Not selected",
            NotificationType.RESERVATION_CONFIRMED to "Reservation confirmed",
            NotificationType.RESERVATION_CANCELLED to "Reservation cancelled",
            NotificationType.RESERVATION_REJECTED to "Reservation rejected",
            NotificationType.OWNER_BROADCAST to "Owner message",
            NotificationType.NEW_MESSAGE to "Message",
            NotificationType.CAMPSITE_REMOVED to "Campsite removed",
        )

        assertEquals(NotificationType.entries.toSet(), expected.keys)
        for ((type, label) in expected) {
            val row = NotificationLogEntry.fromNotifications(listOf(Notification(id = "n-$type", type = type))).single()
            assertEquals(label, row.typeLabel)
        }
    }

    @Test
    fun fromNotifications_emptyList_givesNoRows() {
        assertTrue(NotificationLogEntry.fromNotifications(emptyList()).isEmpty())
    }
}
