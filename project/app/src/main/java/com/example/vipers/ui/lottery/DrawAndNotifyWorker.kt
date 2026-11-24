/*
 * DrawAndNotifyWorker.kt
 * Keeps lotteries moving without the owner (US 02.05.01b): whenever the app is opened, AppRoot
 * runs it, and it starts the draw for every lottery whose registration has ended. The draw
 * transaction makes sure each lottery is drawn only once, even if many phones run this together.
 * Known issues: it only runs when someone opens the app, since there is no background schedule
 * (WorkManager). Expiring unused wins and showing device notifications (T-14) aren't added yet.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.CancellationException

/**
 * Starts the draws that are due.
 *
 * @param lotteryRepository Finds the lotteries whose registration has ended.
 * @param lotteryDrawer Runs each draw and sends the results to the inbox.
 */
class DrawAndNotifyWorker(
    private val lotteryRepository: LotteryRepository,
    private val lotteryDrawer: LotteryDrawer,
) {

    /**
     * Draws every lottery whose registration ended before [now] and returns how many were drawn.
     * One lottery failing doesn't stop the others; the next check tries it again.
     */
    suspend fun runDueDraws(now: Long = System.currentTimeMillis()): Int {
        val due = try {
            lotteryRepository.getLotteriesToDraw(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. the phone is offline; nothing is lost, it checks again next time
            return 0
        }
        var drawn = 0
        for (lottery in due) {
            val result = try {
                lotteryDrawer.drawLottery(lottery.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LotteryDrawResult.Error(e.message ?: "Draw failed")
            }
            if (result !is LotteryDrawResult.Error) drawn++
        }
        return drawn
    }
}
