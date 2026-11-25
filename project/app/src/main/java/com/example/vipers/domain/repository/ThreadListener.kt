/*
 * ThreadListener.kt
 * Gets one reservation's messages each time they change, so the Messages screen stays up to date
 * (US 02.12.01), or hears that they couldn't be loaded.
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Message

/** Told about one conversation by [MessageRepository.listenToThread] (US 02.12.01). */
interface ThreadListener {

    /** Called with the whole conversation, oldest first, straight away and on every change. */
    fun onThreadChanged(messages: List<Message>)

    /**
     * Called if the conversation can't be loaded, e.g. Firestore denies the read. No more
     * messages arrive after this.
     */
    fun onThreadError()
}
