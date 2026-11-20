/*
 * MessageRepository.kt
 * What the app can do with owner-entrant messages, without saying how they are stored
 * (Repository pattern). Used by the Messages screen (US 02.12.01, 01.14.01) and the admin
 * message log (03.12.01).
 * Known issues: none. Messages are never edited or deleted, so admins can always review them.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Message

/** Saves and loads [Message]s. All messages with the same reservation ID form one conversation. */
interface MessageRepository {

    /** Saves a new [message] and returns the ID it was given (US 02.12.01). */
    suspend fun addMessage(message: Message): String

    /**
     * Sends the messages of reservation [reservationId], oldest first ([Message.inOrder]), to
     * [listener] straight away and again on every change, until the returned [Subscription] is
     * cancelled (US 02.12.01).
     */
    fun listenToThread(reservationId: String, listener: ThreadListener): Subscription

    /**
     * Every conversation, for the admin message log (03.12.01): each one oldest first, and the
     * one with the newest message first ([Message.threads]).
     */
    suspend fun getAllThreads(): List<List<Message>>
}
