/*
 * ShowNewOnDeviceTest.kt
 * Unit tests for NotificationService.showNewOnDevice (T-14): which inbox items are shown as phone
 * notifications, at most once each, for opted-out users (US 01.10.01), when permission is refused,
 * and when one item fails. Uses the fake repositories and a recording DeviceNotifier.
 * Known issues: none.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.NotificationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowNewOnDeviceTest {

    private class RecordingNotifier(
        var allowed: Boolean = true,
        private val failOn: Set<String> = emptySet(),
    ) : DeviceNotifier {
        val shown = mutableListOf<String>()
        override fun canPost(): Boolean = allowed
        override fun show(notification: Notification) {
            if (notification.id in failOn) throw IllegalStateException("could not post")
            shown.add(notification.id)
        }
    }

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "alex", name = "Alex Kim"),
            UserProfile(id = "riley", name = "Riley Ng", notificationsEnabled = false),
        ),
    )

    private fun item(id: String, recipientId: String, type: NotificationType, createdAt: Long, shown: Boolean = false) =
        Notification(
            id = id, recipientId = recipientId, type = type, title = id, createdAt = createdAt,
            shownOnDevice = shown,
        )

    private val samsItems = listOf(
        item("won", "sam", NotificationType.WON, 800),
        item("not-selected", "sam", NotificationType.NOT_SELECTED, 100),
        item("confirmed", "sam", NotificationType.RESERVATION_CONFIRMED, 700),
        item("cancelled", "sam", NotificationType.RESERVATION_CANCELLED, 200),
        item("rejected", "sam", NotificationType.RESERVATION_REJECTED, 600),
        item("broadcast", "sam", NotificationType.OWNER_BROADCAST, 300),
        item("message", "sam", NotificationType.NEW_MESSAGE, 500),
        item("removed", "sam", NotificationType.CAMPSITE_REMOVED, 400),
    )

    private val oldestFirst = listOf(
        "not-selected", "cancelled", "broadcast", "removed", "message", "rejected", "confirmed", "won",
    )

    private fun serviceWith(notifications: FakeNotificationRepository) =
        NotificationService(notifications, FakeCampsiteRepository(), profiles, FakeLotteryRepository())

    @Test
    fun showNewOnDevice_showsEveryUnshownItemOnceOldestFirstAndMarksThemShown() = runBlocking {
        val notifications = FakeNotificationRepository(samsItems)
        val notifier = RecordingNotifier()

        val count = serviceWith(notifications).showNewOnDevice("sam", notifier)

        assertEquals(8, count)
        assertEquals(oldestFirst, notifier.shown)
        assertTrue(notifications.getNotificationsFor("sam").all { it.shownOnDevice })
    }

    @Test
    fun showNewOnDevice_runTwice_showsNothingNewTheSecondTime() = runBlocking {
        val service = serviceWith(FakeNotificationRepository(samsItems))
        val notifier = RecordingNotifier()
        service.showNewOnDevice("sam", notifier)

        val second = service.showNewOnDevice("sam", notifier)

        assertEquals(0, second)
        assertEquals(oldestFirst, notifier.shown)
    }

    @Test
    fun showNewOnDevice_alreadyShownItem_isNeverShown() = runBlocking {
        val notifications = FakeNotificationRepository(
            listOf(
                item("old", "sam", NotificationType.WON, 100, shown = true),
                item("new", "sam", NotificationType.NEW_MESSAGE, 200),
            ),
        )
        val notifier = RecordingNotifier()

        val count = serviceWith(notifications).showNewOnDevice("sam", notifier)

        assertEquals(1, count)
        assertEquals(listOf("new"), notifier.shown)
    }

    @Test
    fun showNewOnDevice_optedOutUser_showsNothingAndMarksTheirItemsShown() = runBlocking {
        val notifications = FakeNotificationRepository(
            listOf(
                item("riley-confirmed", "riley", NotificationType.RESERVATION_CONFIRMED, 100),
                item("riley-cancelled", "riley", NotificationType.RESERVATION_CANCELLED, 200),
            ),
        )
        val notifier = RecordingNotifier()

        val count = serviceWith(notifications).showNewOnDevice("riley", notifier)

        assertEquals(0, count)
        assertTrue(notifier.shown.isEmpty())
        assertTrue(notifications.listUnshownOnDevice("riley").isEmpty())
    }

    @Test
    fun showNewOnDevice_cannotPost_showsNothingAndShowsThemOncePermissionIsGranted() = runBlocking {
        val notifications = FakeNotificationRepository(samsItems)
        val service = serviceWith(notifications)
        val notifier = RecordingNotifier(allowed = false)

        val refused = service.showNewOnDevice("sam", notifier)

        assertEquals(0, refused)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(8, notifications.listUnshownOnDevice("sam").size)

        notifier.allowed = true
        val granted = service.showNewOnDevice("sam", notifier)

        assertEquals(8, granted)
        assertEquals(oldestFirst, notifier.shown)
    }

    @Test
    fun showNewOnDevice_onlyShowsTheGivenUsersItems() = runBlocking {
        val notifications = FakeNotificationRepository(
            listOf(
                item("sam-win", "sam", NotificationType.WON, 200),
                item("alex-loss", "alex", NotificationType.NOT_SELECTED, 100),
            ),
        )
        val notifier = RecordingNotifier()

        serviceWith(notifications).showNewOnDevice("sam", notifier)

        assertEquals(listOf("sam-win"), notifier.shown)
        assertFalse(notifications.getNotificationsFor("alex").single().shownOnDevice)
    }

    @Test
    fun showNewOnDevice_noProfile_doesNothing() = runBlocking {
        val notifications = FakeNotificationRepository(
            listOf(item("ghost-win", "ghost", NotificationType.WON, 100)),
        )
        val notifier = RecordingNotifier()

        val count = serviceWith(notifications).showNewOnDevice("ghost", notifier)

        assertEquals(0, count)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(1, notifications.listUnshownOnDevice("ghost").size)
    }

    @Test
    fun showNewOnDevice_showFailsForOneItem_stillShowsTheOthersWithoutThrowing() = runBlocking {
        val notifications = FakeNotificationRepository(samsItems)
        val notifier = RecordingNotifier(failOn = setOf("removed"))

        val count = serviceWith(notifications).showNewOnDevice("sam", notifier)

        assertEquals(7, count)
        assertEquals(oldestFirst - "removed", notifier.shown)
    }

    @Test
    fun showNewOnDevice_markFailsForOneItem_stillShowsTheOthersWithoutThrowing() = runBlocking {
        val inner = FakeNotificationRepository(samsItems)
        val notifications = object : NotificationRepository by inner {
            override suspend fun markShownOnDevice(notificationId: String): Boolean {
                if (notificationId == "broadcast") throw IllegalStateException("offline")
                return inner.markShownOnDevice(notificationId)
            }
        }
        val notifier = RecordingNotifier()

        val count = NotificationService(notifications, FakeCampsiteRepository(), profiles, FakeLotteryRepository())
            .showNewOnDevice("sam", notifier)

        assertEquals(7, count)
        assertEquals(oldestFirst - "broadcast", notifier.shown)
    }
}
