/*
 * MessageMappingTest.kt
 * Unit tests for Message.toMap(): every field of a message is saved under the name
 * doc/data-model.md gives it (US 02.12.01).
 * Known issues: reading a document back (toMessage) needs a real DocumentSnapshot, so it's
 * covered by running the app against Firestore, not here.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageMappingTest {

    @Test
    fun toMap_savesEveryField() {
        val message = Message(
            id = "m1",
            reservationId = "res-hinton",
            senderId = "owner-priya",
            text = "Thanks for booking!",
            createdAt = 1_809_000_000_000,
        )

        assertEquals(
            mapOf(
                "id" to "m1",
                "reservationId" to "res-hinton",
                "senderId" to "owner-priya",
                "text" to "Thanks for booking!",
                "createdAt" to 1_809_000_000_000L,
            ),
            message.toMap(),
        )
    }
}
