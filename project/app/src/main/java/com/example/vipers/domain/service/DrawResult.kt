/*
 * DrawResult.kt
 * Who won and who lost one lottery's committed draw, handed from LotteryDrawer to
 * NotificationService so it can send the draw's notifications (US 01.03.01).
 * Known issues: not the same as LotteryDrawResult, which is what drawLottery returns to its caller.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Lottery

/**
 * The outcome of one lottery's committed draw (US 01.03.01).
 *
 * @property lottery The drawn lottery, with its campsites and booking period.
 * @property winnerIds Device IDs of the entrants the draw picked.
 * @property loserIds Device IDs of the entrants it didn't pick (used by US 01.03.02).
 */
data class DrawResult(
    val lottery: Lottery,
    val winnerIds: List<String>,
    val loserIds: List<String>,
)
