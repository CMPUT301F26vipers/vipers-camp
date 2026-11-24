/*
 * MessageTest.kt
 * Unit tests for the Message rules (US 02.12.01): which text can be sent, who sent a message,
 * the order a conversation shows in, and splitting messages into conversations for the admin
 * log (03.12.01).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    // Priya (the owner) and Sam (the entrant) talking about Sam's May stay at Hinton
    private val thanks = Message("m1", "res-hinton", "owner-priya", "Thanks for booking!", 1_000)
    private val question = Message("m2", "res-hinton", "sam", "Is there drinking water?", 2_000)
    private val answer = Message("m3", "res-hinton", "owner-priya", "Yes, by the cookhouse.", 3_000)

    @Test
    fun canSend_textWithWords() {
        assertTrue(Message.canSend("Is there drinking water?"))
    }

    @Test
    fun cannotSend_emptyText() {
        assertFalse(Message.canSend(""))
    }

    @Test
    fun cannotSend_onlySpacesAndLineBreaks() {
        assertFalse(Message.canSend("   \n\t "))
    }

    @Test
    fun isFrom_onlyTheSender() {
        assertTrue(thanks.isFrom("owner-priya"))
        assertFalse(thanks.isFrom("sam"))
    }

    @Test
    fun inOrder_putsTheOldestFirst() {
        val ordered = Message.inOrder(listOf(answer, thanks, question))

        assertEquals(listOf(thanks, question, answer), ordered)
    }

    @Test
    fun inOrder_sameTime_sortsByIdSoTheOrderStaysTheSame() {
        val first = question.copy(id = "a", createdAt = 5_000)
        val second = answer.copy(id = "b", createdAt = 5_000)

        assertEquals(listOf(first, second), Message.inOrder(listOf(second, first)))
    }

    @Test
    fun threads_oneConversationPerReservation_newestConversationFirst() {
        // Jo wrote about a Jasper stay after Priya's last Hinton message
        val jasper = Message("m4", "res-jasper", "jo", "Can we arrive late?", 4_000)

        val threads = Message.threads(listOf(answer, jasper, thanks, question))

        assertEquals(listOf(listOf(jasper), listOf(thanks, question, answer)), threads)
    }

    @Test
    fun threads_noMessages_noConversations() {
        assertTrue(Message.threads(emptyList()).isEmpty())
    }
}
