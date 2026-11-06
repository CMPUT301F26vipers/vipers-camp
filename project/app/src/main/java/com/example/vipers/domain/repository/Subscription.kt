/*
 * Subscription.kt
 * A handle for stopping a live listener, such as the inbox listener (T-13). Keeps Firestore's
 * ListenerRegistration out of domain/, so the domain classes don't depend on Firebase.
 * Known issues: none.
 */
package com.example.vipers.domain.repository

/** A live listener that can be stopped, returned by [NotificationRepository.listenToInbox] (T-13). */
fun interface Subscription {

    /** Stops the listener; it isn't called again after this (T-13). */
    fun cancel()
}
