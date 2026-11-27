/*
 * FakeDeviceNotifier.kt
 * A DeviceNotifier for tests (a test double for the Adapter pattern, T-14): records the items it
 * was asked to show instead of posting Android notifications. AppContainer uses it unless CampApp
 * passes the real AndroidDeviceNotifier, so tests never post to the phone.
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.service.DeviceNotifier

/**
 * Remembers what it was asked to show.
 *
 * @property allowed What [canPost] returns; tests set it to act out a refused permission.
 */
class FakeDeviceNotifier(var allowed: Boolean = true) : DeviceNotifier {

    /** The inbox items [show] was called with, in order. */
    val shown = mutableListOf<Notification>()

    override fun canPost(): Boolean = allowed

    override fun show(notification: Notification) {
        shown.add(notification)
    }
}
