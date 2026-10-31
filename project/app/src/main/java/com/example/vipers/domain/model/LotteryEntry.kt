/*
 * LotteryEntry.kt
 * One user's place on a lottery's waiting list.
 * Stored in the Firestore "lotteryEntries" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * A user's entry in a lottery. Its ID is "lotteryId_userId", so a user can only join once.
 *
 * @property id "lotteryId_userId"; see [idFor].
 * @property lotteryId The lottery joined.
 * @property userId Device ID of the entrant.
 * @property userName Entrant's name, shown on the owner's waiting list.
 * @property groupSize How many people are in the entrant's group.
 * @property joinedAt When they joined, in milliseconds since 1970.
 * @property status Where the entry is in the lottery.
 */
data class LotteryEntry(
    val id: String = "",
    val lotteryId: String = "",
    val userId: String = "",
    val userName: String = "",
    val groupSize: Int = 1,
    val joinedAt: Long = 0,
    val status: EntryStatus = EntryStatus.WAITING,
) {
    companion object {
        /** The entry ID for [userId] in [lotteryId]. */
        fun idFor(lotteryId: String, userId: String): String = "${lotteryId}_$userId"
    }
}
