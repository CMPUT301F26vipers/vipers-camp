/*
 * FakeMessageRepositoryTest.kt
 * Unit tests for FakeMessageRepository (US 02.12.01): saving messages, the live conversation
 * listener (which must hear every new message, like Firestore's), and the admin's list of all
 * conversations (03.12.01).
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.repository.ThreadListener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMessageRepositoryTest {

    /** Remembers every conversation it is sent. */
    private class RecordingListener : ThreadListener {
        val received = mutableListOf<List<Message>>()
        override fun onThreadChanged(messages: List<Message>) {
            received.add(messages)
        }
        override fun onThreadError() {}
    }

    // Priya (the owner) and Sam (the entrant) talking about Sam's May stay at Hinton
    private val thanks = Message(
        reservationId = "res-hinton",
        senderId = "owner-priya",
        text = "Thanks for booking!",
        createdAt = 1_000,
    )
    private val question = Message(
        reservationId = "res-hinton",
        senderId = "sam",
        text = "Is there drinking water?",
        createdAt = 2_000,
    )

    @Test
    fun addMessage_givesEachMessageItsOwnId() = runBlocking {
        val repository = FakeMessageRepository()

        val first = repository.addMessage(thanks)
        val second = repository.addMessage(question)

        assertNotEquals(first, second)
        assertEquals(listOf(first, second), repository.getAllThreads().single().map { it.id })
    }

    @Test
    fun listenToThread_sendsTheConversationOldestFirstStraightAway() {
        val repository = FakeMessageRepository(
            listOf(question.copy(id = "m2"), thanks.copy(id = "m1")),
        )
        val listener = RecordingListener()

        repository.listenToThread("res-hinton", listener)

        assertEquals(listOf("m1", "m2"), listener.received.single().map { it.id })
    }

    @Test
    fun listenToThread_hearsEveryNewMessage() = runBlocking {
        val repository = FakeMessageRepository()
        val listener = RecordingListener()
        repository.listenToThread("res-hinton", listener)

        repository.addMessage(thanks)
        repository.addMessage(question)

        // once straight away, then once per message
        assertEquals(3, listener.received.size)
        val latest = listener.received.last().map { it.text }
        assertEquals(listOf("Thanks for booking!", "Is there drinking water?"), latest)
    }

    @Test
    fun listenToThread_doesNotHearOtherConversations() = runBlocking {
        val repository = FakeMessageRepository()
        val listener = RecordingListener()
        repository.listenToThread("res-hinton", listener)

        repository.addMessage(question.copy(reservationId = "res-jasper"))

        assertEquals(listOf(emptyList<Message>()), listener.received)
    }

    @Test
    fun listenToThread_stopsAfterCancel() = runBlocking {
        val repository = FakeMessageRepository()
        val listener = RecordingListener()
        val subscription = repository.listenToThread("res-hinton", listener)

        subscription.cancel()
        repository.addMessage(thanks)

        assertEquals(1, listener.received.size)
    }

    @Test
    fun getAllThreads_groupsByReservationWithTheNewestConversationFirst() = runBlocking {
        val repository = FakeMessageRepository()
        repository.addMessage(thanks)
        val jasper = question.copy(reservationId = "res-jasper", senderId = "jo", createdAt = 5_000)
        repository.addMessage(jasper)
        repository.addMessage(question)

        val threads = repository.getAllThreads()

        assertEquals(listOf("res-jasper", "res-hinton"), threads.map { it.first().reservationId })
        assertEquals(2, threads[1].size)
    }

    @Test
    fun getAllThreads_empty() = runBlocking {
        assertTrue(FakeMessageRepository().getAllThreads().isEmpty())
    }
}
