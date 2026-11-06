/*
 * InboxListener.kt
 * Gets a user's inbox each time it changes, so the Notifications tab stays up to date (T-13),
 * or hears that it couldn't be loaded.
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Notification

/** Told about a user's inbox by [NotificationRepository.listenToInbox] (T-13). */
interface InboxListener {

    /** Called with the user's whole inbox, newest first, straight away and on every change (T-13). */
    fun onInboxChanged(items: List<Notification>)

    /**
     * Called if the inbox can't be loaded, e.g. Firestore denies the read (T-13). No more inboxes
     * arrive after this.
     */
    fun onInboxError()
}
