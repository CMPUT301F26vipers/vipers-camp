/*
 * NotificationType.kt
 * The kinds of notifications the app sends, used to pick the title and the screen a tap opens.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/** What a [Notification] is about. */
enum class NotificationType {
    /** The entrant won a lottery and can reserve. */
    WON,

    /** The entrant was not picked in the draw. */
    NOT_SELECTED,

    /** A reservation was booked. */
    RESERVATION_CONFIRMED,

    /** A reservation was cancelled. */
    RESERVATION_CANCELLED,

    /** The owner rejected a reservation. */
    RESERVATION_REJECTED,

    /** A message from an owner to everyone on a waiting list. */
    OWNER_BROADCAST,

    /** A new message in a reservation's chat. */
    NEW_MESSAGE,

    /** An admin removed a campsite the entrant was involved with. */
    CAMPSITE_REMOVED
}
