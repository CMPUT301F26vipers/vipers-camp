/*
 * SendNotificationControllerTest.kt
 * Unit tests for SendNotificationController (US 02.10.01): the waiting-list count, when Send is
 * allowed, a successful send, a failed send that keeps the message, a double tap, a retry that
 * doesn't send a second copy, and sends that reach nobody. Uses the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendNotificationControllerTest {

    private val lotteryId = "hinton-may"

    private val lottery = Lottery(
        id = lotteryId,
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
    )

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "jo", name = "Jo Chen"),
            UserProfile(id = "riley", name = "Riley Ng", notificationsEnabled = false),
        ),
    )

    private val notifications = FakeNotificationRepository()

    private fun entry(userId: String, joinedAt: Long, status: EntryStatus = EntryStatus.WAITING) =
        LotteryEntry(
            id = LotteryEntry.idFor(lotteryId, userId),
            lotteryId = lotteryId,
            userId = userId,
            joinedAt = joinedAt,
            status = status,
        )

    private fun controllerFor(
        vararg entries: LotteryEntry,
        repo: NotificationRepository = notifications,
        senderId: String = "owner-priya",
    ) = SendNotificationController(
        NotificationService(
            repo,
            FakeCampsiteRepository(),
            profiles,
            FakeLotteryRepository(listOf(lottery), entries.toList()),
        ),
        lotteryId,
        senderId,
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
    fun load_countsEveryoneOnTheListExceptRemovedEntries() = runTest {
        val controller = controllerFor(
            entry("sam", 1),
            entry("riley", 2, EntryStatus.LOST),
            entry("jo", 3, EntryStatus.REMOVED),
        )

        assertFalse(controller.loading)
        assertNull(controller.loadError)
        assertEquals(2, controller.recipientCount)
        assertEquals("2 entrants", controller.countText)
    }

    @Test
    fun countText_oneEntrant_isSingular() = runTest {
        assertEquals("1 entrant", controllerFor(entry("sam", 1)).countText)
    }

    @Test
    fun countText_emptyList_saysNoEntrantsYet() = runTest {
        assertEquals("No entrants yet", controllerFor().countText)
    }

    @Test
    fun canSend_emptyOrWhitespaceMessage_isFalse() = runTest {
        val controller = controllerFor(entry("sam", 1))

        assertFalse(controller.canSend)
        controller.onMessageChange("   \n\t ")
        assertFalse(controller.canSend)
    }

    @Test
    fun canSend_messageWithText_isTrue() = runTest {
        val controller = controllerFor(entry("sam", 1))

        controller.onMessageChange("The cookhouse reopens next week.")

        assertTrue(controller.canSend)
    }

    @Test
    fun canSend_nobodyOnTheList_isFalse() = runTest {
        val controller = controllerFor()

        controller.onMessageChange("The cookhouse reopens next week.")

        assertFalse(controller.canSend)
    }

    @Test
    fun sendMessage_success_reportsHowManyGotIt() = runTest {
        val controller = controllerFor(entry("sam", 1), entry("jo", 2), entry("riley", 3))
        controller.onMessageChange("The cookhouse reopens next week.")

        controller.sendMessage(now = 900L)

        assertEquals(2, controller.sentCount)
        assertEquals("Notification sent to 2 entrants", controller.sentText)
        assertNull(controller.sendError)
        assertFalse(controller.canSend)
        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals(NotificationType.OWNER_BROADCAST, notice.type)
        assertEquals("The cookhouse reopens next week.", notice.message)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    @Test
    fun sendMessage_saveFails_keepsTheMessageAndShowsAnError() = runTest {
        val offline = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                throw IllegalStateException("Offline")
            }
        }
        val controller = controllerFor(entry("sam", 1), repo = offline)
        controller.onMessageChange("The cookhouse reopens next week.")

        controller.sendMessage()

        assertNull(controller.sentCount)
        assertNotNull(controller.sendError)
        assertEquals("The cookhouse reopens next week.", controller.message)
        assertTrue(controller.canSend)
    }

    @Test
    fun send_tappedTwiceWhileSending_sendsOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val slow = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                calls++
                gate.await()
            }
        }
        val controller = controllerFor(entry("sam", 1), repo = slow)
        controller.onMessageChange("The cookhouse reopens next week.")

        controller.send()
        assertTrue(controller.isSending)
        controller.send()
        gate.complete(Unit)

        assertEquals(1, calls)
        assertEquals(1, controller.sentCount)
    }

    @Test
    fun sendMessage_retryAfterAPartialFailure_givesNobodyASecondCopy() = runTest {
        var failNext = true
        val flaky = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                if (failNext) {
                    failNext = false
                    this@SendNotificationControllerTest.notifications.addAll(notifications.take(1))
                    throw IllegalStateException("Connection lost")
                }
                this@SendNotificationControllerTest.notifications.addAll(notifications)
            }
        }
        val controller = controllerFor(entry("sam", 1), entry("jo", 2), repo = flaky)
        controller.onMessageChange("The cookhouse reopens next week.")
        controller.sendMessage()
        assertNotNull(controller.sendError)

        controller.sendMessage()

        assertEquals(2, controller.sentCount)
        val samsNotices = notifications.getNotificationsFor("sam")
        val josNotices = notifications.getNotificationsFor("jo")
        assertEquals(1, samsNotices.size)
        assertEquals(1, josNotices.size)
        assertEquals(samsNotices.single().batchId, josNotices.single().batchId)
    }

    @Test
    fun sendMessage_everyoneOptedOut_saysNobodyCouldGetIt() = runTest {
        val controller = controllerFor(entry("riley", 1))
        controller.onMessageChange("The cookhouse reopens next week.")

        controller.sendMessage()

        assertNull(controller.sentCount)
        assertEquals("No one on this waiting list can get notifications right now.", controller.sendError)
    }

    @Test
    fun sendMessage_notTheOwner_sendsNothing() = runTest {
        val controller = controllerFor(entry("sam", 1), senderId = "sam")
        controller.onMessageChange("The cookhouse reopens next week.")

        controller.sendMessage()

        assertNull(controller.sentCount)
        assertEquals("Only the lottery's owner can send notifications.", controller.sendError)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }
}
