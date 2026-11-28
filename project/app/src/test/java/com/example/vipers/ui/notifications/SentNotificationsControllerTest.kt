/*
 * SentNotificationsControllerTest.kt
 * Unit tests for SentNotificationsController (US 02.10.02): the owner's sends newest first with
 * their message, campsite and lottery, time and recipient count, the year for an older send,
 * only this owner's sends, the empty state, and a failed load that a retry recovers from.
 * Uses the fake repositories with a fixed time and time zone.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SentNotificationsControllerTest {

    private val zone = ZoneOffset.UTC
    private val now = time(2027, 4, 20, 12, 0)

    private val campsites = FakeCampsiteRepository(
        listOf(
            Campsite(id = "hinton", name = "Hinton Ridge Campground"),
            Campsite(id = "banff", name = "Bow River Sites"),
        ),
    )

    private val lotteries = FakeLotteryRepository(
        listOf(
            Lottery(id = "may", ownerId = "owner-priya", campsiteIds = listOf("hinton"), nightsDescription = "May weekends"),
            Lottery(
                id = "summer",
                ownerId = "owner-priya",
                campsiteIds = listOf("hinton", "banff"),
                nightsDescription = "Summer long weekends",
            ),
        ),
    )

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun broadcast(
        recipientId: String,
        batchId: String,
        lotteryId: String,
        campsiteId: String?,
        createdAt: Long,
        message: String,
        senderId: String = "owner-priya",
    ) = Notification(
        id = "broadcast_${batchId}_$recipientId",
        recipientId = recipientId,
        senderId = senderId,
        campsiteId = campsiteId,
        lotteryId = lotteryId,
        type = NotificationType.OWNER_BROADCAST,
        title = "Message from owner",
        message = message,
        batchId = batchId,
        createdAt = createdAt,
    )

    private fun controllerFor(repo: NotificationRepository) = SentNotificationsController(
        NotificationService(repo, campsites, FakeProfileRepository(), lotteries),
        ownerId = "owner-priya",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_showsTheOwnersSendsNewestFirstWithTheirText() = runTest {
        val repo = FakeNotificationRepository(
            listOf(
                broadcast("sam", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners"),
                broadcast("jo", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners"),
                broadcast("kim", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners"),
                broadcast("sam", "b2", "summer", null, time(2027, 4, 18, 16, 30), "Fire ban this weekend"),
                broadcast("jo", "b2", "summer", null, time(2027, 4, 18, 16, 30), "Fire ban this weekend"),
            ),
        )
        val controller = controllerFor(repo)

        controller.load(now, zone)

        assertEquals(
            listOf(
                SentNotificationRow(
                    batchId = "b2",
                    message = "Fire ban this weekend",
                    subtitle = "Hinton Ridge Campground, Bow River Sites · Summer long weekends",
                    detail = "Apr 18, 4:30 PM · sent to 2",
                ),
                SentNotificationRow(
                    batchId = "b1",
                    message = "Reminder to winners",
                    subtitle = "Hinton Ridge Campground · May weekends",
                    detail = "Apr 5, 9:00 AM · sent to 3",
                ),
            ),
            controller.items,
        )
        assertFalse(controller.loading)
        assertNull(controller.error)
        assertFalse(controller.isEmpty)
    }

    @Test
    fun load_sendFromAPreviousYear_showsTheYear() = runTest {
        val repo = FakeNotificationRepository(
            listOf(broadcast("sam", "b1", "may", "hinton", time(2026, 12, 31, 21, 15), "Happy New Year")),
        )
        val controller = controllerFor(repo)

        controller.load(now, zone)

        assertEquals("Dec 31, 2026, 9:15 PM · sent to 1", controller.items.single().detail)
    }

    @Test
    fun load_anotherOwnersSends_areNotListed() = runTest {
        val repo = FakeNotificationRepository(
            listOf(
                broadcast("sam", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners"),
                broadcast("sam", "b2", "may", "hinton", time(2027, 4, 6, 9, 0), "Not mine", senderId = "owner-daniel"),
            ),
        )
        val controller = controllerFor(repo)

        controller.load(now, zone)

        assertEquals(listOf("b1"), controller.items.map { it.batchId })
    }

    @Test
    fun load_ownerSentNothing_isEmpty() = runTest {
        val controller = controllerFor(FakeNotificationRepository())

        controller.load(now, zone)

        assertTrue(controller.items.isEmpty())
        assertNull(controller.error)
        assertFalse(controller.loading)
        assertTrue(controller.isEmpty)
    }

    @Test
    fun load_fails_setsTheErrorAndRetryRecovers() = runTest {
        var offline = true
        val backing = FakeNotificationRepository(
            listOf(broadcast("sam", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners")),
        )
        val flaky = object : NotificationRepository by backing {
            override suspend fun getOwnerBroadcasts(senderId: String): List<Notification> {
                if (offline) throw IOException("offline")
                return backing.getOwnerBroadcasts(senderId)
            }
        }
        val controller = controllerFor(flaky)

        controller.load(now, zone)

        assertEquals(
            "Couldn't load your sent notifications. Check your connection and try again.",
            controller.error,
        )
        assertTrue(controller.items.isEmpty())
        assertFalse(controller.loading)
        assertFalse(controller.isEmpty)

        offline = false
        controller.retry()

        assertNull(controller.error)
        assertEquals(listOf("b1"), controller.items.map { it.batchId })
        assertFalse(controller.loading)
    }

    @Test
    fun load_oneRecipient_saysSentToOne() = runTest {
        val repo = FakeNotificationRepository(
            listOf(broadcast("sam", "b1", "may", "hinton", time(2027, 4, 5, 9, 0), "Reminder to winners")),
        )
        val controller = controllerFor(repo)

        controller.load(now, zone)

        assertEquals("Apr 5, 9:00 AM · sent to 1", controller.items.single().detail)
    }
}
