/*
 * DeviceNotifier.kt
 * Shows inbox items as phone notifications (T-14) without the domain knowing about Android: an
 * interface that NotificationService calls, with the real version in data/device and a fake in
 * tests (Adapter pattern).
 * Known issues: the Android version is added in a later T-14 step.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Notification

/** Posts system notifications for inbox items (T-14). */
interface DeviceNotifier {

    /**
     * True if the app may show system notifications right now: the permission is granted and
     * the user hasn't blocked them in the phone's settings (T-14).
     */
    fun canPost(): Boolean

    /** Posts one system notification for the inbox item [notification] (T-14). */
    fun show(notification: Notification)
}
