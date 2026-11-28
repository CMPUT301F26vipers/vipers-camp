/*
 * FakeNotificationRepositoryTest.kt
 * Unit tests for FakeNotificationRepository's device-notification methods (T-14): listing the
 * items not yet shown on the phone, oldest first, and marking one shown at most once.
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNotificationRepositoryTest {

    private val samsWin = Notification(
        id = "win", recipientId = "sam", type = NotificationType.WON, lotteryId = "may",
        title = "You won the lottery!", message = "Reserve by May 9", createdAt = 300,
    )
    private val samsBroadcast = Notification(
        id = "broadcast", recipientId = "sam", type = NotificationType.OWNER_BROADCAST,
        campsiteId = "hinton", title = "Message from owner", createdAt = 100,
    )
    private val samsShownMessage = Notification(
        id = "message", recipientId = "sam", type = NotificationType.NEW_MESSAGE,
        reservationId = "r1", shownOnDevice = true, createdAt = 200,
    )
    private val alexsLoss = Notification(
        id = "alex-loss", recipientId = "alex", type = NotificationType.NOT_SELECTED,
        campsiteId = "hinton", createdAt = 50,
    )

    private fun repo() =
        FakeNotificationRepository(listOf(samsWin, samsBroadcast, samsShownMessage, alexsLoss))

    @Test
    fun listUnshownOnDevice_returnsOnlyThatRecipientsUnshownItemsOldestFirst() = runBlocking {
        val unshown = repo().listUnshownOnDevice("sam")

        assertEquals(listOf("broadcast", "win"), unshown.map { it.id })
    }

    @Test
    fun listUnshownOnDevice_recipientWithNothingUnshown_returnsEmptyList() = runBlocking {
        val repo = repo()
        repo.markShownOnDevice("alex-loss")

        assertTrue(repo.listUnshownOnDevice("alex").isEmpty())
        assertTrue(repo.listUnshownOnDevice("nobody").isEmpty())
    }

    @Test
    fun markShownOnDevice_trueFirstTimeFalseSecondTime() = runBlocking {
        val repo = repo()

        assertTrue(repo.markShownOnDevice("win"))
        assertFalse(repo.markShownOnDevice("win"))
    }

    @Test
    fun markShownOnDevice_itemDropsOutOfListUnshownOnDevice() = runBlocking {
        val repo = repo()

        repo.markShownOnDevice("broadcast")

        assertEquals(listOf("win"), repo.listUnshownOnDevice("sam").map { it.id })
    }

    @Test
    fun markShownOnDevice_alreadyShownItem_returnsFalse() = runBlocking {
        assertFalse(repo().markShownOnDevice("message"))
    }

    @Test
    fun markShownOnDevice_unknownId_returnsFalse() = runBlocking {
        val repo = repo()

        assertFalse(repo.markShownOnDevice("missing"))
        assertEquals(listOf("broadcast", "win"), repo.listUnshownOnDevice("sam").map { it.id })
    }

    @Test
    fun markShownOnDevice_changesNoOtherField() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin.copy(read = true), samsBroadcast))

        repo.markShownOnDevice("win")
        repo.markShownOnDevice("broadcast")

        val inbox = repo.getNotificationsFor("sam").associateBy { it.id }
        assertEquals(samsWin.copy(read = true, shownOnDevice = true), inbox.getValue("win"))
        assertEquals(samsBroadcast.copy(shownOnDevice = true), inbox.getValue("broadcast"))
    }
}
