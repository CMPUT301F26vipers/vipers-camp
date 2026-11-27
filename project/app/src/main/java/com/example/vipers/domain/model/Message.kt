/*
 * Message.kt
 * One chat message between an entrant and an owner about a reservation (US 02.12.01).
 * Stored in the Firestore "messages" collection, where admins can also review them (03.12.01).
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
    /** True if the user with this [userId] sent the message, so the screen shows it as "You". */
    fun isFrom(userId: String): Boolean = senderId == userId

    /**
     * The NEW_MESSAGE inbox notice that tells [recipientId] about this message, sent about
     * [reservation] (US 02.12.01). Call it on the saved message: the ID is "message_<id>", so
     * the notice is only saved once. Tapping it opens the conversation ([Notification.target]),
     * and its text starts with the campsite name, like the other inbox items.
     */
    fun notice(reservation: Reservation, recipientId: String): Notification = Notification(
        id = "message_$id",
        recipientId = recipientId,
        senderId = senderId,
        campsiteId = reservation.campsiteId,
        reservationId = reservationId,
        type = NotificationType.NEW_MESSAGE,
        title = "New message",
        message = "${reservation.campsiteName}: $text",
        createdAt = createdAt,
    )

    companion object {

        /**
         * True if [text] can be sent: it has something other than spaces and line breaks
         * (US 02.12.01).
         */
        fun canSend(text: String): Boolean = text.isNotBlank()

        /**
         * [messages] in the order a conversation shows them: oldest first, and by ID when two were
         * sent at the same time, so the order never jumps around (US 02.12.01).
         */
        fun inOrder(messages: List<Message>): List<Message> =
            messages.sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.id })

        /**
         * [messages] split into conversations, one per reservation, for the admin message log
         * (03.12.01). Each conversation is oldest first ([inOrder]), and the conversation with the
         * newest message comes first.
         */
        fun threads(messages: List<Message>): List<List<Message>> =
            messages.groupBy { it.reservationId }
                .values
                .map { inOrder(it) }
                .sortedByDescending { it.last().createdAt }
    }
}
