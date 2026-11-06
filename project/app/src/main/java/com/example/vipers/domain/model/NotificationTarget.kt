/*
 * NotificationTarget.kt
 * Where tapping a notification in the inbox takes the user (T-13, mockup E10).
 * Never stored; Notification.target works it out from the notification's type and IDs.
 * Known issues: the Reserve screen (01.04.01) and the message thread (messaging story) aren't
 * built yet, so the inbox can't open OpenReserve and OpenThread until those stories add them.
 */
package com.example.vipers.domain.model

/** The screen a tapped [Notification] opens (T-13). */
sealed class NotificationTarget {

    /** The Reserve screen for the lottery [lotteryId] the user won (US 01.04.01, T-13). */
    data class OpenReserve(val lotteryId: String) : NotificationTarget()

    /** The details of the reservation [reservationId] (T-13). */
    data class OpenReservation(val reservationId: String) : NotificationTarget()

    /** The message thread of the reservation [reservationId] (T-13). */
    data class OpenThread(val reservationId: String) : NotificationTarget()

    /** The details of the campsite [campsiteId] (T-13). */
    data class OpenCampsite(val campsiteId: String) : NotificationTarget()
}
