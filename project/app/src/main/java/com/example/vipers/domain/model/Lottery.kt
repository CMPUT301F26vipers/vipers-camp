/*
 * Lottery.kt
 * A lottery an owner runs for one or more of their campsites over a set of nights.
 * Stored in the Firestore "lotteries" collection; its entries are in "lotteryEntries".
 * Known issues: none. The registration and booking periods are times (not just dates) so
 * registration can close at a set time and the draw can run soon after (US 02.04.03, 02.05.01b).
 */
package com.example.vipers.domain.model

/**
 * A lottery for some nights at one or more campsites.
 * Period start and end times are milliseconds since 1970; nights are "yyyy-MM-dd" strings.
 *
 * @property id Firestore document ID.
 * @property ownerId Device ID of the owner who created it.
 * @property campsiteIds The campsites it covers.
 * @property nightsOffered The concrete ISO "yyyy-MM-dd" nights winners can reserve.
 * @property nightsDescription Optional human-readable description (e.g. "May 2027 weekends").
 * @property registrationStart When entrants can start joining.
 * @property registrationEnd When joining closes; the draw runs after this.
 * @property bookingStart When winners can start reserving.
 * @property bookingEnd When reserving closes; unused wins expire after this.
 * @property winnersCount How many entrants the draw picks; at least 1.
 * @property waitlistCapacity Most entrants allowed to join, or null for no limit.
 * @property drawnAt When the draw ran, or null if it hasn't.
 * @property removed True once an admin removes it.
 * @property entriesVersion Counter incremented on waiting-list membership changes.
 * @property committedDrawId ID of the authoritative draw manifest, or null if not yet drawn.
 * @property manifestChunkCount Number of manifest chunks saved for this draw.
 * @property participantCount Total eligible participants in this draw.
 * @property winnerCount Total winners selected in this draw.
 */
data class Lottery(
    val id: String = "",
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val nightsOffered: List<String> = emptyList(),
    val nightsDescription: String? = null,
    val registrationStart: Long = 0,
    val registrationEnd: Long = 0,
    val bookingStart: Long = 0,
    val bookingEnd: Long = 0,
    val winnersCount: Int = 1,
    val waitlistCapacity: Int? = null,
    val drawnAt: Long? = null,
    val removed: Boolean = false,
    val entriesVersion: Long = 0L,
    val committedDrawId: String? = null,
    val manifestChunkCount: Int = 0,
    val participantCount: Int = 0,
    val winnerCount: Int = 0,
) {
    /**
     * The lottery's stage at time [now] (milliseconds since 1970), worked out from its
     * periods and whether it has been drawn. A lottery with no times set counts as CLOSED.
     */
    fun status(now: Long = System.currentTimeMillis()): LotteryStatus = when {
        removed -> LotteryStatus.CLOSED
        now > bookingEnd && bookingEnd > 0L -> LotteryStatus.CLOSED
        drawnAt != null -> LotteryStatus.DRAWN
        registrationStart <= 0L || registrationEnd <= 0L -> LotteryStatus.CLOSED
        registrationEnd <= registrationStart -> LotteryStatus.CLOSED
        now < registrationStart -> LotteryStatus.UPCOMING
        now <= registrationEnd -> LotteryStatus.OPEN
        else -> LotteryStatus.CLOSED
    }

    /**
     * Checks if registration is currently open for entrants at time [now] (milliseconds since 1970).
     * Registration is open between [registrationStart] and [registrationEnd] (inclusive).
     */
    fun canEntrantJoin(now: Long = System.currentTimeMillis()): Boolean =
        status(now) == LotteryStatus.OPEN

    /**
     * Checks if the booking period is currently open at time [now] (milliseconds since 1970).
     * Reserving is open between [bookingStart] and [bookingEnd] (inclusive) when the lottery has
     * not been removed and booking timestamps are valid ([bookingEnd] > [bookingStart] > 0).
     */
    fun isBookingOpen(now: Long = System.currentTimeMillis()): Boolean =
        !removed && bookingStart > 0L && bookingEnd > 0L && bookingEnd > bookingStart && now in bookingStart..bookingEnd

    /**
     * Checks if the booking period has expired at time [now] (milliseconds since 1970).
     * Expired when a valid booking period exists ([bookingStart] > 0 and [bookingEnd] > [bookingStart])
     * and [now] is strictly after [bookingEnd].
     */
    fun isBookingExpired(now: Long = System.currentTimeMillis()): Boolean =
        bookingStart > 0L && bookingEnd > bookingStart && now > bookingEnd
}
