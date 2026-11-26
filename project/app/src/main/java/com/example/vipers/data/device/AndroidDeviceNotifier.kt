/*
 * AndroidDeviceNotifier.kt
 * The real DeviceNotifier (T-14): posts an inbox item as an Android system notification on the
 * app's channel, and tapping it opens MainActivity at the item's screen (DeviceNotificationLink).
 * Each item is posted with its ID as the tag, so posting it again replaces it instead of adding a
 * second one. Adapter between NotificationService and Android's NotificationManagerCompat.
 * Known issues: none. If notifications aren't allowed, show() does nothing and the in-app inbox
 * still has the item.
 */
package com.example.vipers.data.device

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.vipers.R
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.service.DeviceNotifier

/** Posts system notifications with [context]'s NotificationManagerCompat (T-14). */
class AndroidDeviceNotifier(context: Context) : DeviceNotifier {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    override fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun show(notification: Notification) {
        val built = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(notification.createdAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(notification))
            .build()
        if (!canPost()) return
        try {
            manager.notify(notification.id, NOTIFICATION_ID, built)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun contentIntent(notification: Notification): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            notification.id.hashCode(),
            DeviceNotificationLink.toIntent(appContext, notification),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val CHANNEL_ID = "camp_updates"
        private const val NOTIFICATION_ID = 1

        /**
         * Creates the app's notification channel, named and described from strings.xml (T-14).
         * Safe to call on every launch; an existing channel is left as the user set it.
         */
        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_name))
                .setDescription(context.getString(R.string.notification_channel_description))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}
