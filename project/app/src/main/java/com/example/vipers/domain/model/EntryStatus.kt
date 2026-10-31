/*
 * EntryStatus.kt
 * The stages a lottery entry goes through, from joining the waiting list to reserving.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/** Where a [LotteryEntry] is in the lottery process. */
enum class EntryStatus {
    /** On the waiting list; the draw hasn't happened yet. */
    WAITING,

    /** Picked in the draw; can reserve nights during the booking period. */
    WON,

    /** Not picked in the draw; stays on the list in case a winner declines. */
    LOST,

    /** Won, but gave up the win; the spot goes to someone else. */
    DECLINED,

    /** Won and reserved nights. */
    RESERVED,

    /** Won, but didn't reserve before the booking period ended. */
    EXPIRED,

    /** Removed by an admin, or because the campsite was removed. */
    REMOVED
}
