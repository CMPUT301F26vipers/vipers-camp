/*
 * Message.kt
 * One chat message between an entrant and an owner about a reservation.
 * Stored in the Firestore "messages" collection, where admins can also review them.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * A chat message. All messages with the same [reservationId] form one conversation.
 *
 * @property id Firestore document ID.
 * @property reservationId The reservation the conversation is about.
 * @property senderId Device ID of the sender.
 * @property text The message text; must not be empty.
 * @property createdAt When it was sent, in milliseconds since 1970.
 */
data class Message(
    val id: String = "",
    val reservationId: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Long = 0,
) {
    /** True if the message has some text; empty messages are not sent. */
    fun hasText(): Boolean = text.isNotBlank()
}
