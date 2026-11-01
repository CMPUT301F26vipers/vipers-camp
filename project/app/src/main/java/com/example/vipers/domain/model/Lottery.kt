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
 * @property nightsOffered The nights winners can reserve.
 * @property registrationStart When entrants can start joining.
 * @property registrationEnd When joining closes; the draw runs after this.
 * @property bookingStart When winners can start reserving.
 * @property bookingEnd When reserving closes; unused wins expire after this.
 * @property winnersCount How many entrants the draw picks; at least 1.
 * @property waitlistCapacity Most entrants allowed to join, or null for no limit.
 * @property drawnAt When the draw ran, or null if it hasn't.
 * @property removed True once an admin removes it.
 */
data class Lottery(
    val id: String = "",
    val ownerId: String = "",
    val campsiteIds: List<String> = emptyList(),
    val nightsOffered: List<String> = emptyList(),
    val registrationStart: Long = 0,
    val registrationEnd: Long = 0,
    val bookingStart: Long = 0,
    val bookingEnd: Long = 0,
    val winnersCount: Int = 1,
    val waitlistCapacity: Int? = null,
    val drawnAt: Long? = null,
    val removed: Boolean = false,
) {
    /**
     * The lottery's stage at time [now] (milliseconds since 1970), worked out from its
     * periods and whether it has been drawn. A lottery with no times set counts as CLOSED.
     */
    fun status(now: Long): LotteryStatus = when {
        now > bookingEnd -> LotteryStatus.CLOSED
        drawnAt != null -> LotteryStatus.DRAWN
        now < registrationStart -> LotteryStatus.UPCOMING
        now <= registrationEnd -> LotteryStatus.OPEN
        else -> LotteryStatus.CLOSED
    }
}
