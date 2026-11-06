/*
 * NotificationTest.kt
 * Unit tests for the screen each kind of notification opens and the category the inbox shows (T-13).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTest {

    // a notification with every ID set, so each type picks the one it needs
    private fun withAllIds(type: NotificationType) = Notification(
        type = type,
        campsiteId = "hinton",
        lotteryId = "may",
        reservationId = "r1",
    )

    @Test
    fun target_won_opensReserveForTheLottery() {
        assertEquals(
            NotificationTarget.OpenReserve("may"),
            withAllIds(NotificationType.WON).target,
        )
    }

    @Test
    fun target_notSelected_opensTheCampsite() {
        assertEquals(
            NotificationTarget.OpenCampsite("hinton"),
            withAllIds(NotificationType.NOT_SELECTED).target,
        )
    }

    @Test
    fun target_reservationConfirmed_opensTheReservation() {
        assertEquals(
            NotificationTarget.OpenReservation("r1"),
            withAllIds(NotificationType.RESERVATION_CONFIRMED).target,
        )
    }

    @Test
    fun target_reservationCancelled_opensTheReservation() {
        assertEquals(
            NotificationTarget.OpenReservation("r1"),
            withAllIds(NotificationType.RESERVATION_CANCELLED).target,
        )
    }

    @Test
    fun target_reservationRejected_opensTheReservation() {
        assertEquals(
            NotificationTarget.OpenReservation("r1"),
            withAllIds(NotificationType.RESERVATION_REJECTED).target,
        )
    }

    @Test
    fun target_ownerBroadcast_opensTheCampsite() {
        assertEquals(
            NotificationTarget.OpenCampsite("hinton"),
            withAllIds(NotificationType.OWNER_BROADCAST).target,
        )
    }

    @Test
    fun target_newMessage_opensTheReservationsThread() {
        assertEquals(
            NotificationTarget.OpenThread("r1"),
            withAllIds(NotificationType.NEW_MESSAGE).target,
        )
    }

    @Test
    fun target_campsiteRemoved_opensTheCampsite() {
        assertEquals(
            NotificationTarget.OpenCampsite("hinton"),
            withAllIds(NotificationType.CAMPSITE_REMOVED).target,
        )
    }

    @Test
    fun target_wonWithNoLotteryId_isNull() {
        val win = withAllIds(NotificationType.WON).copy(lotteryId = null)
        assertNull(win.target)
    }

    @Test
    fun displayCategory_eachType_matchesTheInboxLabel() {
        val expected = mapOf(
            NotificationType.WON to "Won",
            NotificationType.NOT_SELECTED to "Not selected",
            NotificationType.RESERVATION_CONFIRMED to "Reservation",
            NotificationType.RESERVATION_CANCELLED to "Reservation",
            NotificationType.RESERVATION_REJECTED to "Reservation",
            NotificationType.OWNER_BROADCAST to "Owner message",
            NotificationType.NEW_MESSAGE to "Message",
            NotificationType.CAMPSITE_REMOVED to "Campsite removed",
        )
        // every type is covered, so a new type fails here until it gets a label
        assertEquals(NotificationType.entries.toSet(), expected.keys)
        for ((type, label) in expected) {
            assertEquals(label, Notification(type = type).displayCategory)
        }
    }
}
