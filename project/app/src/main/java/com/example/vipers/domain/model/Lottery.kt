/*
 * Lottery.kt
 * A lottery an owner runs for one or more of their campsites over a set of nights.
 * Stored in the Firestore "lotteries" collection; its entries are in "lotteryEntries".
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.time.LocalDate

/**
 * A lottery for some nights at one or more campsites. Dates are "yyyy-MM-dd" strings.
 *
 * @property id Firestore document ID.
 * @property ownerId Device ID of the owner who created it.
 * @property campsiteIds The campsites it covers.
 * @property nightsOffered The nights winners can reserve.
 * @property registrationStart First day entrants can join.
 * @property registrationEnd Last day entrants can join; the draw runs after this.
 * @property bookingStart First day winners can reserve.
 * @property bookingEnd Last day winners can reserve; unused wins expire after this.
 * @property winnersCount How many entrants the draw picks; at least 1.
 * @property waitlistCapacity Most entrants allowed to join, or null for no limit.
 * @property drawnAt When the draw ran, in milliseconds since 1970, or null if it hasn't.
 * @property removed True once an admin removes it.
 */
data class Lottery(
    val id: String = "",
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val nightsOffered: List<String> = emptyList(),
    val registrationStart: String = "",
    val registrationEnd: String = "",
    val bookingStart: String = "",
    val bookingEnd: String = "",
    val winnersCount: Int = 1,
    val waitlistCapacity: Int? = null,
    val drawnAt: Long? = null,
    val removed: Boolean = false,
) {
    /** The lottery's stage on [today], worked out from its dates and whether it has been drawn. */
    fun status(today: LocalDate): LotteryStatus = when {
        today.isAfter(LocalDate.parse(bookingEnd)) -> LotteryStatus.CLOSED
        drawnAt != null -> LotteryStatus.DRAWN
        today.isBefore(LocalDate.parse(registrationStart)) -> LotteryStatus.UPCOMING
        !today.isAfter(LocalDate.parse(registrationEnd)) -> LotteryStatus.OPEN
        else -> LotteryStatus.CLOSED
    }

    /** Spots left on the waiting list given [entryCount] entries, or null if there is no limit. */
    fun spotsLeft(entryCount: Int): Int? = waitlistCapacity?.let { maxOf(it - entryCount, 0) }
}
