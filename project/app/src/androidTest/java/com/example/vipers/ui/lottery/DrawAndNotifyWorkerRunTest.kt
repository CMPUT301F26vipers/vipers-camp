/*
 * DrawAndNotifyWorkerRunTest.kt
 * Instrumented tests for T-14: runs DrawAndNotifyWorker the way WorkManager would (with
 * TestListenableWorkerBuilder) on the fake repositories and checks which inbox items it shows on the
 * phone, including a draw's result from the same run, and that a refused permission shows nothing.
 * Uses FakeDeviceNotifier, so no real notification is posted.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeDeviceNotifier
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawAndNotifyWorkerRunTest {

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp

    private val hour = 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private val reservationConfirmed = Notification(
        id = "confirmed", recipientId = "test-device", type = NotificationType.RESERVATION_CONFIRMED,
        reservationId = "r1", title = "Reservation confirmed", message = "Hinton Ridge: May 8 to May 10",
        createdAt = now - hour,
    )

    private val notifications = FakeNotificationRepository(listOf(reservationConfirmed))

    private fun useFakes(
        notifier: FakeDeviceNotifier,
        lotteries: FakeLotteryRepository = FakeLotteryRepository(),
    ) {
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = FakeCampsiteRepository(
                listOf(Campsite(id = "hinton", ownerId = "owner-priya", name = "Hinton Ridge")),
            ),
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = notifications,
            imageRepository = FakeImageRepository(),
            deviceNotifier = notifier,
        )
    }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<DrawAndNotifyWorker>(app).build().doWork()
    }

    @Test
    fun runningTheWorker_showsTheUnshownInboxItemAndMarksItShown() {
        val notifier = FakeDeviceNotifier()
        useFakes(notifier)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("confirmed"), notifier.shown.map { it.id })
        val saved = runBlocking { notifications.getNotificationsFor("test-device") }.single()
        assertTrue(saved.shownOnDevice)
    }

    @Test
    fun runningTheWorkerTwice_showsEachItemOnlyOnce() {
        val notifier = FakeDeviceNotifier()
        useFakes(notifier)

        runWorker()
        val second = runWorker()

        assertEquals(ListenableWorker.Result.success(), second)
        assertEquals(listOf("confirmed"), notifier.shown.map { it.id })
    }

    @Test
    fun runningTheWorker_showsADrawResultFromTheSameRun() {
        val ended = Lottery(
            id = "ended", ownerId = "owner-priya", campsiteIds = listOf("hinton"),
            registrationStart = now - 48 * hour, registrationEnd = now - hour,
            bookingStart = now + 24 * hour, bookingEnd = now + 48 * hour, winnersCount = 1,
        )
        val entry = LotteryEntry(
            id = LotteryEntry.idFor("ended", "test-device"), lotteryId = "ended",
            userId = "test-device", status = EntryStatus.WAITING,
        )
        val notifier = FakeDeviceNotifier()
        useFakes(notifier, FakeLotteryRepository(listOf(ended), listOf(entry)))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("confirmed", "draw_ended_test-device"), notifier.shown.map { it.id })
        assertEquals(NotificationType.WON, notifier.shown.last().type)
    }

    @Test
    fun runningTheWorkerWithoutPermission_showsNothingAndLeavesItUnshown() {
        val notifier = FakeDeviceNotifier(allowed = false)
        useFakes(notifier)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(notifier.shown.isEmpty())
        val saved = runBlocking { notifications.getNotificationsFor("test-device") }.single()
        assertFalse(saved.shownOnDevice)
    }
}
