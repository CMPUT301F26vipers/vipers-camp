/*
 * MessageControllerTest.kt
 * Unit tests for MessageController (US 02.12.01): who can open a conversation, the live list of
 * messages, sending (and not sending empty text), the "New message" notice to the other side, and
 * what happens when loading or sending fails. Uses the fake repositories, never Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.messages

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.repository.Subscription
import com.example.vipers.domain.repository.ThreadListener
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageControllerTest {

    // Priya owns the Hinton campsite; Sam reserved May 8-10 there
    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "owner-priya", name = "Priya Sharma"),
            UserProfile(id = "sam", name = "Sam Patel"),
        ),
    )
    private val samsStay = Reservation(
        id = "res-hinton",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
    )
    private val thanks = Message("m1", "res-hinton", "owner-priya", "Thanks for booking!", 1_000)

    private val messages = FakeMessageRepository(listOf(thanks))
    private val notifications = FakeNotificationRepository()
    private val reservations = FakeReservationRepository(listOf(samsStay))

    private fun controllerFor(
        userId: String,
        reservationId: String = "res-hinton",
        messageRepository: MessageRepository = messages,
        reservationRepository: ReservationRepository = reservations,
        notificationRepository: NotificationRepository = notifications,
    ) = MessageController(
        messageRepository = messageRepository,
        reservationRepository = reservationRepository,
        profileRepository = profiles,
        notificationService = NotificationService(
            notificationRepository,
            FakeCampsiteRepository(),
            profiles,
            FakeLotteryRepository(),
        ),
        userId = userId,
        reservationId = reservationId,
    )

    @Test
    fun fetchThread_owner_loadsTheConversationAndTheEntrantsName() = runBlocking {
        val controller = controllerFor("owner-priya")

        controller.fetchThread()

        assertEquals(samsStay, controller.reservation)
        assertEquals("Sam Patel", controller.otherName)
        assertEquals(listOf(thanks), controller.messages)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchThread_entrant_seesTheOwnersName() = runBlocking {
        val controller = controllerFor("sam")

        controller.fetchThread()

        assertEquals("Priya Sharma", controller.otherName)
        assertEquals(listOf(thanks), controller.messages)
    }

    @Test
    fun fetchThread_anotherOwner_cantOpenTheConversation() = runBlocking {
        val controller = controllerFor("owner-daniel")

        controller.fetchThread()

        assertNull(controller.reservation)
        assertTrue(controller.messages.isEmpty())
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchThread_missingReservation_leavesItNull() = runBlocking {
        val controller = controllerFor("owner-priya", reservationId = "nope")

        controller.fetchThread()

        assertNull(controller.reservation)
        assertFalse(controller.loading)
    }

    @Test
    fun fetchThread_deletedEntrant_showsDeletedUser() = runBlocking {
        val deleted = FakeReservationRepository(listOf(samsStay.copy(userId = "no-profile")))
        val controller = controllerFor("owner-priya", reservationRepository = deleted)

        controller.fetchThread()

        assertEquals("Deleted user", controller.otherName)
    }

    @Test
    fun fetchThread_loadFails_showsAnError() = runBlocking {
        val failing = object : ReservationRepository by reservations {
            override suspend fun getReservation(id: String): Reservation? =
                throw IllegalStateException("offline")
        }
        val controller = controllerFor("owner-priya", reservationRepository = failing)

        controller.fetchThread()

        assertEquals("Couldn't load the messages", controller.error)
        assertFalse(controller.loading)
        assertNull(controller.reservation)
    }

    @Test(expected = CancellationException::class)
    fun fetchThread_cancelled_isNotShownAsAnError() {
        val cancelled = object : ReservationRepository by reservations {
            override suspend fun getReservation(id: String): Reservation? =
                throw CancellationException("screen closed")
        }
        val controller = controllerFor("owner-priya", reservationRepository = cancelled)
        runBlocking { controller.fetchThread() }
    }

    @Test
    fun onThreadError_showsAnErrorAndKeepsTheMessages() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.fetchThread()

        controller.onThreadError()

        assertEquals("Couldn't load the messages", controller.error)
        assertEquals(listOf(thanks), controller.messages)
    }

    @Test
    fun fetchThread_twice_listensOnlyOnce() = runBlocking {
        var listening = 0
        val counting = object : MessageRepository by messages {
            override fun listenToThread(
                reservationId: String,
                listener: ThreadListener,
            ): Subscription {
                listening++
                val subscription = messages.listenToThread(reservationId, listener)
                return Subscription {
                    listening--
                    subscription.cancel()
                }
            }
        }
        val controller = controllerFor("owner-priya", messageRepository = counting)

        controller.fetchThread()
        controller.fetchThread()

        assertEquals(1, listening)
    }

    @Test
    fun senderName_isYouForTheUsersOwnMessagesAndTheOtherNameOtherwise() = runBlocking {
        val controller = controllerFor("sam")
        controller.fetchThread()

        assertEquals("Priya Sharma", controller.senderName(thanks))
        assertEquals("You", controller.senderName(thanks.copy(senderId = "sam")))
        assertTrue(controller.isMine(thanks.copy(senderId = "sam")))
        assertFalse(controller.isMine(thanks))
    }

    @Test
    fun canSend_onlyWithTextAfterLoading() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.onDraftChange("Check-in is at 2 PM")
        // not loaded yet
        assertFalse(controller.canSend)

        controller.fetchThread()
        assertTrue(controller.canSend)

        controller.onDraftChange("   ")
        assertFalse(controller.canSend)
    }

    @Test
    fun sendMessage_savesItTrimmedAndClearsTheBox() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.fetchThread()
        controller.onDraftChange("  Check-in is at 2 PM \n")

        controller.sendMessage(now = 2_000)

        val sent = controller.messages.last()
        assertEquals("Check-in is at 2 PM", sent.text)
        assertEquals("owner-priya", sent.senderId)
        assertEquals("res-hinton", sent.reservationId)
        assertEquals(2_000L, sent.createdAt)
        assertEquals("", controller.draft)
        assertFalse(controller.sending)
        assertNull(controller.sendError)
    }

    @Test
    fun sendMessage_newMessagesShowInOrder() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.fetchThread()

        controller.onDraftChange("Check-in is at 2 PM")
        controller.sendMessage(now = 2_000)
        controller.onDraftChange("Firewood is free")
        controller.sendMessage(now = 3_000)

        val texts = controller.messages.map { it.text }
        val expected = listOf("Thanks for booking!", "Check-in is at 2 PM", "Firewood is free")
        assertEquals(expected, texts)
    }

    @Test
    fun sendMessage_emptyText_sendsNothing() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.fetchThread()
        controller.onDraftChange("   ")

        controller.sendMessage(now = 2_000)

        assertEquals(listOf(thanks), messages.getAllThreads().single())
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun sendMessage_byAnotherOwner_sendsNothing() = runBlocking {
        val controller = controllerFor("owner-daniel")
        controller.fetchThread()
        controller.onDraftChange("Hello")

        controller.sendMessage(now = 2_000)

        assertEquals(listOf(thanks), messages.getAllThreads().single())
    }

    @Test
    fun sendMessage_byOwner_notifiesTheEntrant() = runBlocking {
        val controller = controllerFor("owner-priya")
        controller.fetchThread()
        controller.onDraftChange("Check-in is at 2 PM")

        controller.sendMessage(now = 2_000)

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals(NotificationType.NEW_MESSAGE, notice.type)
        assertEquals("res-hinton", notice.reservationId)
        assertEquals("Hinton Ridge Campground: Check-in is at 2 PM", notice.message)
        assertTrue(notifications.getNotificationsFor("owner-priya").isEmpty())
    }

    @Test
    fun sendMessage_byEntrant_notifiesTheOwner() = runBlocking {
        val controller = controllerFor("sam")
        controller.fetchThread()
        controller.onDraftChange("Is there drinking water?")

        controller.sendMessage(now = 2_000)

        assertEquals(1, notifications.getNotificationsFor("owner-priya").size)
    }

    @Test
    fun sendMessage_saveFails_keepsTheTextAndShowsAnError() = runBlocking {
        val failing = object : MessageRepository by messages {
            override suspend fun addMessage(message: Message): String =
                throw IllegalStateException("offline")
        }
        val controller = controllerFor("owner-priya", messageRepository = failing)
        controller.fetchThread()
        controller.onDraftChange("Check-in is at 2 PM")

        controller.sendMessage(now = 2_000)

        assertEquals("Couldn't send. Check your connection and try again.", controller.sendError)
        assertEquals("Check-in is at 2 PM", controller.draft)
        assertFalse(controller.sending)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun onDraftChange_hidesTheSendError() = runBlocking {
        val failing = object : MessageRepository by messages {
            override suspend fun addMessage(message: Message): String =
                throw IllegalStateException("offline")
        }
        val controller = controllerFor("owner-priya", messageRepository = failing)
        controller.fetchThread()
        controller.onDraftChange("Hi")
        controller.sendMessage(now = 2_000)

        controller.onDraftChange("Hi again")

        assertNull(controller.sendError)
    }

    @Test
    fun sendMessage_noticeFails_theMessageIsStillSent() = runBlocking {
        val failing = object : NotificationRepository by notifications {
            override suspend fun saveNotification(notification: Notification): String =
                throw IllegalStateException("offline")
        }
        val controller = controllerFor("owner-priya", notificationRepository = failing)
        controller.fetchThread()
        controller.onDraftChange("Check-in is at 2 PM")

        controller.sendMessage(now = 2_000)

        assertEquals("Check-in is at 2 PM", controller.messages.last().text)
        assertNull(controller.sendError)
        assertEquals("", controller.draft)
    }

    @Test
    fun sendMessage_noticeWaitingForTheConnection_theNextMessageCanBeTyped() = runBlocking {
        // offline, saving the notice only finishes once the phone reconnects
        val noticeStarted = CompletableDeferred<Unit>()
        val reconnected = CompletableDeferred<Unit>()
        val offline = object : NotificationRepository by notifications {
            override suspend fun saveNotification(notification: Notification): String {
                noticeStarted.complete(Unit)
                reconnected.await()
                return notifications.saveNotification(notification)
            }
        }
        val controller = controllerFor("owner-priya", notificationRepository = offline)
        controller.fetchThread()
        controller.onDraftChange("Check-in is at 2 PM")

        val sent = launch { controller.sendMessage(now = 2_000) }
        noticeStarted.await()

        assertEquals("Check-in is at 2 PM", controller.messages.last().text)
        assertFalse(controller.sending)
        assertEquals("", controller.draft)
        controller.onDraftChange("See you then")
        assertTrue(controller.canSend)

        reconnected.complete(Unit)
        sent.join()
        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }
}
