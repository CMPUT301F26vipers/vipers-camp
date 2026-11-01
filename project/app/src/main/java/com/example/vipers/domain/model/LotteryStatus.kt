/*
 * LotteryStatus.kt
 * The stages of a lottery. Never stored; Lottery.status() works it out from its periods.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/** Where a [Lottery] is in its life, worked out by [Lottery.status]. */
enum class LotteryStatus {
    /** Registration hasn't started yet. */
    UPCOMING,

    /** Entrants can join the waiting list. */
    OPEN,

    /** Winners have been picked and the booking period is running. */
    DRAWN,

    /** Registration is over and the lottery is finished or waiting for its draw. */
    CLOSED
}
