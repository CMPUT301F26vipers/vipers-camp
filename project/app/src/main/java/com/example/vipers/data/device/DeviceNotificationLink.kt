/*
 * DeviceNotificationLink.kt
 * Writes and reads the extras of the Intent a phone notification opens (T-14), so tapping it opens
 * the same screen as tapping the item in the inbox. The screen is worked out by Notification.target,
 * so the type-to-screen rules stay in one place.
 * Known issues: none.
 */
package com.example.vipers.data.device

import android.content.Context
import android.content.Intent
import com.example.vipers.MainActivity
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType

/** Puts an inbox item into a MainActivity Intent and reads it back (T-14). */
object DeviceNotificationLink {

    private const val EXTRA_ID = "com.example.vipers.notification.ID"
    private const val EXTRA_TYPE = "com.example.vipers.notification.TYPE"
    private const val EXTRA_LOTTERY_ID = "com.example.vipers.notification.LOTTERY_ID"
    private const val EXTRA_RESERVATION_ID = "com.example.vipers.notification.RESERVATION_ID"
    private const val EXTRA_CAMPSITE_ID = "com.example.vipers.notification.CAMPSITE_ID"

    /**
     * A tapped phone notification: the inbox item's [notificationId] and the screen it opens.
     *
     * @property notificationId ID of the inbox item, so it can be marked read.
     * @property target The screen to open, the same as the inbox's.
     */
    data class Tap(val notificationId: String, val target: NotificationTarget)

    /**
     * An Intent that brings the open MainActivity to the front (or starts it) with [notification]'s
     * ID, type and the IDs its target needs (T-14).
     */
    fun toIntent(context: Context, notification: Notification): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ID, notification.id)
            putExtra(EXTRA_TYPE, notification.type.name)
            putExtra(EXTRA_LOTTERY_ID, notification.lotteryId)
            putExtra(EXTRA_RESERVATION_ID, notification.reservationId)
            putExtra(EXTRA_CAMPSITE_ID, notification.campsiteId)
        }

    /**
     * Reads a [Tap] from [intent], or returns null if it didn't come from a phone notification, its
     * type is unknown, or the item has no screen to open ([Notification.target] is null) (T-14).
     */
    fun fromIntent(intent: Intent?): Tap? {
        val id = intent?.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() } ?: return null
        val typeName = intent.getStringExtra(EXTRA_TYPE) ?: return null
        val type = NotificationType.entries.firstOrNull { it.name == typeName } ?: return null
        val target = Notification(
            id = id,
            type = type,
            lotteryId = intent.getStringExtra(EXTRA_LOTTERY_ID),
            reservationId = intent.getStringExtra(EXTRA_RESERVATION_ID),
            campsiteId = intent.getStringExtra(EXTRA_CAMPSITE_ID),
        ).target ?: return null
        return Tap(id, target)
    }

    /** Removes the phone-notification extras from [intent], so they're handled only once (T-14). */
    fun clear(intent: Intent) {
        listOf(EXTRA_ID, EXTRA_TYPE, EXTRA_LOTTERY_ID, EXTRA_RESERVATION_ID, EXTRA_CAMPSITE_ID)
            .forEach(intent::removeExtra)
    }
}
