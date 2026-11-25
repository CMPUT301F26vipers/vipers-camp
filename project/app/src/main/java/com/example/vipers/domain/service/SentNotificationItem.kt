/*
 * SentNotificationItem.kt
 * One row of the owner's Sent Notifications list (mockup O13, US 02.10.02), handed from
 * NotificationService to the screen's controller: a send with the campsite and lottery labels
 * looked up for it.
 * Known issues: none. The time is formatted by the controller, not here.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.SentNotification

/**
 * One send on the owner's Sent Notifications list (US 02.10.02).
 *
 * @property sent The send: its message, time and recipient count.
 * @property campsiteLabel The campsite's name, or the names of all the lottery's campsites joined
 * with ", "; "Removed campsite" for one that can't be loaded or was removed.
 * @property lotteryLabel The lottery's nights description, or its nights label when it has none;
 * "Removed lottery" if it can't be loaded or was removed.
 */
data class SentNotificationItem(
    val sent: SentNotification,
    val campsiteLabel: String,
    val lotteryLabel: String,
)
