/*
 * NotificationsControllerTest.kt
 * Unit tests for NotificationsController (T-13): the live inbox, a failed load and opening a
 * notification, using the fake notification repository. Marking read is a suspend fun, so the
 * tests call it with runBlocking instead of going through viewModelScope.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.Subscription
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsControllerTest {

    // Sam's inbox, saved out of order, plus one of Alex's
    private val samsWin = Notification(
        id = "win", recipientId = "sam", type = NotificationType.WON, lotteryId = "may",
        createdAt = 200,
    )
    private val samsOldNews = Notification(
        id = "old", recipientId = "sam", type = NotificationType.NOT_SELECTED, campsiteId = "hinton",
        read = true, createdAt = 100,
    )
    private val samsNewest = Notification(
        id = "new", recipientId = "sam", type = NotificationType.RESERVATION_CONFIRMED,
        reservationId = "r1", createdAt = 300,
    )
    private val alexsWin = Notification(
        id = "alex-win", recipientId = "alex", type = NotificationType.WON, createdAt = 400,
    )

    private fun controllerFor(repo: NotificationRepository, userId: String = "sam") =
        NotificationsController(NotificationService(repo, FakeCampsiteRepository()), userId)

    /** A repository whose inbox listener is told the load failed, like Firestore denying the read. */
    private fun failingToLoad(before: List<Notification>? = null) =
        object : NotificationRepository by FakeNotificationRepository() {
            override fun listenToInbox(recipientId: String, listener: InboxListener): Subscription {
                before?.let { listener.onInboxChanged(it) }
                listener.onInboxError()
                return Subscription {}
            }
        }

    @Test
    fun items_onSubscribe_areTheUsersOwnNewestFirst() {
        val repo = FakeNotificationRepository(listOf(samsWin, alexsWin, samsOldNews, samsNewest))
        val controller = controllerFor(repo)

        assertEquals(listOf("new", "win", "old"), controller.items.map { it.id })
        assertFalse(controller.isLoading)
        assertNull(controller.error)
    }

    @Test
    fun isLoading_beforeTheInboxArrives_isTrue() {
        // a repository whose listener hasn't been called yet, like Firestore before the first snapshot
        val silent = object : NotificationRepository by FakeNotificationRepository() {
            override fun listenToInbox(recipientId: String, listener: InboxListener) =
                Subscription {}
        }
        val controller = controllerFor(silent)

        assertTrue(controller.isLoading)
        assertTrue(controller.items.isEmpty())
    }

    @Test
    fun items_noticeAddedAfterSubscribing_appearsWithoutReloading() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val controller = controllerFor(repo)

        val id = repo.addNotification(
            Notification(recipientId = "sam", type = NotificationType.OWNER_BROADCAST, createdAt = 500),
        )

        assertEquals(listOf(id, "win"), controller.items.map { it.id })
    }

    @Test
    fun onInboxError_beforeTheInboxArrives_endsLoadingAndSetsError() {
        val controller = controllerFor(failingToLoad())

        assertFalse(controller.isLoading)
        assertEquals("Couldn't load notifications", controller.error)
        assertTrue(controller.items.isEmpty())
    }

    @Test
    fun onInboxError_afterTheInboxArrived_keepsTheItems() {
        val controller = controllerFor(failingToLoad(before = listOf(samsWin)))

        assertEquals("Couldn't load notifications", controller.error)
        assertEquals(listOf("win"), controller.items.map { it.id })
    }

    @Test
    fun markRead_unreadItem_marksItRead() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val controller = controllerFor(repo)

        controller.markRead(samsWin)

        assertTrue(repo.getNotificationsFor("sam").single().read)
        assertTrue(controller.items.single().read)
    }

    @Test
    fun markRead_whenTheRepositoryFails_leavesItUnread() = runBlocking {
        val failing = object : NotificationRepository by FakeNotificationRepository(listOf(samsNewest)) {
            override suspend fun markRead(id: String) = throw Exception("offline")
        }
        val controller = controllerFor(failing)

        controller.markRead(samsNewest)

        assertFalse(controller.items.single().read)
    }

    @Test
    fun open_readItem_doesNotMarkItAgainAndReturnsItsTarget() {
        var markReadCalls = 0
        val counting = object : NotificationRepository by FakeNotificationRepository(listOf(samsOldNews)) {
            override suspend fun markRead(id: String) {
                markReadCalls++
            }
        }
        val controller = controllerFor(counting)

        val target = controller.open(samsOldNews)

        assertEquals(0, markReadCalls)
        assertEquals(NotificationTarget.OpenCampsite("hinton"), target)
    }

    @Test
    fun open_readWin_returnsOpenReserveForTheLottery() {
        val readWin = samsWin.copy(read = true)
        val controller = controllerFor(FakeNotificationRepository(listOf(readWin)))

        assertEquals(NotificationTarget.OpenReserve("may"), controller.open(readWin))
    }
}
