/*
 * DrawAndNotifyWorker.kt
 * The WorkManager background job that keeps lotteries moving without the owner (US 02.05.01b) and
 * shows new inbox items as phone notifications (T-14). MainActivity schedules it about every 15
 * minutes and once whenever the app opens. Each run starts the draw for every lottery whose
 * registration has ended (DueDraws), then shows the user's unshown inbox items through
 * NotificationService, at most once each, so a draw's results appear in the same run. The draw
 * transaction makes sure each lottery is drawn only once, even if many phones run this together.
 * AppRoot also runs DueDraws directly each time the app comes to the front.
 * Known issues: expiring unused wins isn't added yet.
 */
package com.example.vipers.ui.lottery

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.vipers.CampApp
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.service.NotificationService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Runs the draws that are due and shows new inbox items on the phone, using the app's
 * [com.example.vipers.AppContainer]. WorkManager creates it; use [schedule] to start it.
 */
class DrawAndNotifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /**
     * Draws the lotteries that are due, then shows this device user's new inbox items as phone
     * notifications (T-14). Returns success even when there is nothing to do or no user yet, and
     * retry only if the run couldn't start at all.
     */
    override suspend fun doWork(): Result =
        try {
            val container = (applicationContext as CampApp).container
            container.dueDraws.runDueDraws()
            val userId = container.deviceIdentityManager.deviceId
            if (userId.isNotBlank()) {
                NotificationService(
                    container.notificationRepository,
                    container.campsiteRepository,
                    container.profileRepository,
                ).showNewOnDevice(userId, container.deviceNotifier)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }

    /**
     * Starts the draws that are due, without WorkManager, so AppRoot and tests can run it directly.
     *
     * @param lotteryRepository Finds the lotteries whose registration has ended.
     * @param lotteryDrawer Runs each draw and sends the results to the inbox.
     */
    class DueDraws(
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

    companion object {
        /** Unique name of the check that runs about every 15 minutes. */
        const val PERIODIC_WORK_NAME = "draw_and_notify_periodic"

        /** Unique name of the check that runs once when the app opens. */
        const val APP_OPEN_WORK_NAME = "draw_and_notify_app_open"

        /**
         * Schedules the check every 15 minutes (WorkManager's minimum), keeping a schedule that
         * already exists, and runs one check now, replacing any that hasn't run yet (T-14). Both
         * wait for a network connection.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DrawAndNotifyWorker>(
                    PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS,
                ).setConstraints(constraints).build(),
            )
            workManager.enqueueUniqueWork(
                APP_OPEN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DrawAndNotifyWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
