/*
 * FakeMessageRepository.kt
 * An in-memory MessageRepository for unit and intent tests (a test double for the Repository
 * pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Message
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.Subscription
import com.example.vipers.domain.repository.ThreadListener

/** Keeps messages in a list instead of Firestore. Pass [initial] to start with some. */
class FakeMessageRepository(initial: List<Message> = emptyList()) : MessageRepository {

    private val messages = initial.toMutableList()
    private var nextId = 1

    // each live thread listener, with the reservation whose messages it gets
    private val listeners = mutableListOf<Pair<String, ThreadListener>>()

    override suspend fun addMessage(message: Message): String {
        val id = "message-${nextId++}"
        messages.add(message.copy(id = id))
        // like Firestore, the open conversation hears about the new message straight away
        notifyListeners(message.reservationId)
        return id
    }

    override fun listenToThread(reservationId: String, listener: ThreadListener): Subscription {
        val entry = reservationId to listener
        listeners.add(entry)
        listener.onThreadChanged(threadOf(reservationId))
        return Subscription { listeners.remove(entry) }
    }

    override suspend fun getAllThreads(): List<List<Message>> = Message.threads(messages)

    private fun threadOf(reservationId: String): List<Message> =
        Message.inOrder(messages.filter { it.reservationId == reservationId })

    // Only the listeners on the changed conversation hear about it.
    private fun notifyListeners(reservationId: String) {
        for ((thread, listener) in listeners.toList()) {
            if (thread == reservationId) listener.onThreadChanged(threadOf(reservationId))
        }
    }
}
