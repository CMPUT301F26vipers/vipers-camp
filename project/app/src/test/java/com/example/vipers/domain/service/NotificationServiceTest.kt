/*
 * NotificationServiceTest.kt
 * Unit tests for NotificationService.sendDrawResults (US 01.03.01, 01.03.02): the "You won" and
 * "Not selected" notices a draw adds to inboxes, using the fake repositories and a fixed date format.
 * Also tests optedIn and that the draw skips users who turned notifications off (US 01.10.01),
 * the "New message" notice sendNewMessage sends to the other side of a conversation
 * (US 02.12.01), and the "Reservation rejected" notice sendReservationRejected sends to the
 * entrant (US 02.08.01).
 * Known issues: none. The inbox and markRead pass-throughs are covered by NotificationsControllerTest.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class NotificationServiceTest {

    private val notifications = FakeNotificationRepository()
    private val campsites = FakeCampsiteRepository(
        listOf(
            Campsite(id = "hinton", name = "Hinton Ridge Campground"),
            Campsite(id = "banff", name = "Bow River Sites"),
        ),
    )

    // Sam and Jo keep notifications on (the default); Riley turned them off (US 01.10.01)
    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "jo", name = "Jo Chen"),
            UserProfile(id = "riley", name = "Riley Ng", notificationsEnabled = false),
        ),
    )

    // a fixed date format, so the tests don't depend on the phone's language or time zone
    private val service = NotificationService(
        notifications,
        campsites,
        profiles,
        formatDate = { time -> "time $time" },
    )

    // a lottery for the Hinton campsite, drawn at time 500, whose booking ends at time 2000
    private val hintonLottery = Lottery(
        id = "may",
        campsiteIds = listOf("hinton"),
        bookingEnd = 2000L,
        drawnAt = 500L,
    )

    @Test
    fun sendDrawResults_twoWinners_addsOneWonPerWinnerThatOpensReserve() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("sam", "alex"), listOf("jo")))

        for (winner in listOf("sam", "alex")) {
            val notice = notifications.getNotificationsFor(winner).single()
            assertEquals("draw_may_$winner", notice.id)
            assertEquals(NotificationType.WON, notice.type)
            assertEquals("system", notice.senderId)
            assertEquals("may", notice.lotteryId)
            assertNull(notice.batchId)
            assertEquals(500L, notice.createdAt) // when the draw ran
            assertEquals(NotificationTarget.OpenReserve("may"), notice.target)
        }
        // the loser gets a "Not selected" notice instead
        assertEquals(NotificationType.NOT_SELECTED, notifications.getNotificationsFor("jo").single().type)
    }

    @Test
    fun sendDrawResults_oneCampsite_namesItAndTheBookingEnd() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("sam"), emptyList()))

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals("You won the lottery!", notice.title)
        assertEquals(
            "You won a spot at Hinton Ridge Campground. Reserve your nights before time 2000.",
            notice.message,
        )
        assertEquals("hinton", notice.campsiteId)
    }

    @Test
    fun sendDrawResults_twoCampsites_namesBothWithNullCampsiteId() = runBlocking {
        val bothLottery = hintonLottery.copy(
            campsiteIds = listOf("hinton", "banff"),
            nightsDescription = "May 2027 weekends",
        )
        service.sendDrawResults(DrawResult(bothLottery, listOf("sam"), emptyList()))

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals(
            "You won a spot at Hinton Ridge Campground, Bow River Sites. " +
                "Reserve your nights before time 2000.",
            notice.message,
        )
        assertNull(notice.campsiteId)
    }

    @Test
    fun sendDrawResults_sameDrawTwice_leavesOneNoticePerWinner() = runBlocking {
        val result = DrawResult(hintonLottery, listOf("sam", "alex"), emptyList())
        service.sendDrawResults(result)
        service.sendDrawResults(result)

        assertEquals(listOf("draw_may_sam"), notifications.getNotificationsFor("sam").map { it.id })
        assertEquals(listOf("draw_may_alex"), notifications.getNotificationsFor("alex").map { it.id })
    }

    @Test
    fun sendDrawResults_againAfterTheWinnerReadIt_keepsItRead() = runBlocking {
        val result = DrawResult(hintonLottery, listOf("sam"), emptyList())
        service.sendDrawResults(result)
        service.markRead("draw_may_sam")

        service.sendDrawResults(result)

        assertTrue(notifications.getNotificationsFor("sam").single().read)
    }

    @Test
    fun sendDrawResults_lotteryNotDrawnYet_usesNowAsTheTime() = runBlocking {
        service.sendDrawResults(
            DrawResult(hintonLottery.copy(drawnAt = null), listOf("sam"), emptyList()),
            now = 999L,
        )

        assertEquals(999L, notifications.getNotificationsFor("sam").single().createdAt)
    }

    // US 01.03.02: the losers' "Not selected" notices

    @Test
    fun sendDrawResults_twoLosers_addsOneNotSelectedPerLoserNamingTheCampsite() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("jo", "alex")))

        for (loser in listOf("jo", "alex")) {
            val notice = notifications.getNotificationsFor(loser).single()
            assertEquals("draw_may_$loser", notice.id)
            assertEquals(NotificationType.NOT_SELECTED, notice.type)
            assertEquals("system", notice.senderId)
            assertEquals("may", notice.lotteryId)
            assertNull(notice.batchId)
            assertEquals(500L, notice.createdAt) // when the draw ran
            assertEquals("Not selected this time", notice.title)
            assertEquals(
                "You weren't selected for Hinton Ridge Campground this time.",
                notice.message,
            )
            assertEquals("hinton", notice.campsiteId)
            assertEquals(NotificationTarget.OpenCampsite("hinton"), notice.target)
        }
    }

    @Test
    fun sendDrawResults_loserOfTwoCampsites_namesBothWithNullCampsiteId() = runBlocking {
        val bothLottery = hintonLottery.copy(
            campsiteIds = listOf("hinton", "banff"),
            nightsDescription = "May 2027 weekends",
        )
        service.sendDrawResults(DrawResult(bothLottery, emptyList(), listOf("jo")))

        val notice = notifications.getNotificationsFor("jo").single()
        assertEquals(
            "You weren't selected for Hinton Ridge Campground, Bow River Sites this time.",
            notice.message,
        )
        assertNull(notice.campsiteId)
    }

    @Test
    fun sendDrawResults_noWinners_stillNotifiesTheLosers() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, emptyList(), listOf("jo")))

        assertEquals(NotificationType.NOT_SELECTED, notifications.getNotificationsFor("jo").single().type)
    }

    @Test
    fun sendDrawResults_sameDrawTwice_leavesOneNoticePerLoser() = runBlocking {
        val result = DrawResult(hintonLottery, listOf("sam"), listOf("jo", "jo"))
        service.sendDrawResults(result)
        service.sendDrawResults(result)

        assertEquals(listOf("draw_may_jo"), notifications.getNotificationsFor("jo").map { it.id })
    }

    @Test
    fun sendDrawResults_userInBothLists_getsOnlyTheWin() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("sam", "jo")))

        assertEquals(NotificationType.WON, notifications.getNotificationsFor("sam").single().type)
    }

    @Test
    fun sendDrawResults_winnersAndLosers_savesThemInOneAddAll() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val counting = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                calls.add(notifications)
            }
        }
        NotificationService(counting, campsites, FakeProfileRepository(), formatDate = { "t $it" })
            .sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("jo", "alex")))

        assertEquals(1, calls.size)
        assertEquals(setOf("draw_may_sam", "draw_may_jo", "draw_may_alex"), calls[0].map { it.id }.toSet())
    }

    @Test
    fun sendDrawResults_emptyDraw_savesNothing() = runBlocking {
        var called = false
        val counting = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                called = true
            }
        }
        NotificationService(counting, campsites, profiles).sendDrawResults(
            DrawResult(hintonLottery, emptyList(), emptyList()),
        )

        assertFalse(called)
    }

    // 5 entrants, 2 of them picked with Random(42), like LotteryDrawer's draw
    private val fiveEntrants = listOf("sam", "alex", "jo", "kim", "lee")
    private val fiveEntrantDraw = fiveEntrants.shuffled(Random(42)).let { shuffled ->
        DrawResult(hintonLottery, shuffled.take(2), shuffled.drop(2))
    }

    private suspend fun noticesFor(userIds: List<String>) =
        userIds.flatMap { notifications.getNotificationsFor(it) }

    @Test
    fun sendDrawResults_fiveEntrantsTwoWinners_addsThreeNotSelectedOnePerLoser() = runBlocking {
        service.sendDrawResults(fiveEntrantDraw)

        val notSelected = noticesFor(fiveEntrants).filter { it.type == NotificationType.NOT_SELECTED }
        assertEquals(3, notSelected.size)
        assertEquals(fiveEntrantDraw.loserIds.toSet(), notSelected.map { it.recipientId }.toSet())
    }

    @Test
    fun sendDrawResults_fiveEntrants_noUserGetsBothWonAndNotSelected() = runBlocking {
        service.sendDrawResults(fiveEntrantDraw)

        for (userId in fiveEntrants) {
            val types = notifications.getNotificationsFor(userId).map { it.type }
            val expected = if (userId in fiveEntrantDraw.winnerIds) NotificationType.WON else NotificationType.NOT_SELECTED
            assertEquals(userId, listOf(expected), types)
        }
    }

    @Test
    fun sendDrawResults_fiveEntrantsSentTwice_everyEntrantKeepsExactlyOneNotice() = runBlocking {
        service.sendDrawResults(fiveEntrantDraw)
        service.sendDrawResults(fiveEntrantDraw)

        for (userId in fiveEntrants) {
            assertEquals(userId, listOf("draw_may_$userId"), notifications.getNotificationsFor(userId).map { it.id })
        }
    }

    @Test
    fun sendDrawResults_againAfterTheLoserReadIt_keepsItRead() = runBlocking {
        val result = DrawResult(hintonLottery, listOf("sam"), listOf("jo"))
        service.sendDrawResults(result)
        service.markRead("draw_may_jo")

        service.sendDrawResults(result)

        assertTrue(notifications.getNotificationsFor("jo").single().read)
    }

    @Test
    fun optedIn_dropsUsersWhoTurnedNotificationsOff() = runBlocking {
        assertEquals(listOf("sam", "jo"), service.optedIn(listOf("sam", "riley", "jo")))
    }

    @Test
    fun optedIn_keepsUsersWithNoProfileAndRemovesDuplicates() = runBlocking {
        val kept = service.optedIn(listOf("sam", "no-profile", "sam"))

        assertEquals(listOf("sam", "no-profile"), kept)
    }

    @Test
    fun optedIn_nobodyLeft_returnsAnEmptyList() = runBlocking {
        assertTrue(service.optedIn(listOf("riley")).isEmpty())
        assertTrue(service.optedIn(emptyList()).isEmpty())
    }

    @Test
    fun sendDrawResults_winnerWhoOptedOut_getsNoNoticeButOthersDo() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("riley", "sam"), listOf("jo")))

        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
        assertEquals(NotificationType.WON, notifications.getNotificationsFor("sam").single().type)
        val joNotice = notifications.getNotificationsFor("jo").single()
        assertEquals(NotificationType.NOT_SELECTED, joNotice.type)
    }

    @Test
    fun sendDrawResults_loserWhoOptedOut_getsNoNotice() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("riley", "jo")))

        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
        assertEquals(1, notifications.getNotificationsFor("jo").size)
    }

    @Test
    fun sendDrawResults_optedOutUserInBothLists_getsNeitherNotice() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("riley"), listOf("riley", "jo")))

        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
        assertEquals(1, notifications.getNotificationsFor("jo").size)
    }

    @Test
    fun sendDrawResults_everyoneOptedOut_savesNothing() = runBlocking {
        var called = false
        val counting = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                called = true
            }
        }
        NotificationService(counting, campsites, profiles).sendDrawResults(
            DrawResult(hintonLottery, listOf("riley"), emptyList()),
        )

        assertFalse(called)
    }

    @Test
    fun sendDrawResults_afterTurningNotificationsBackOn_sendsTheNextDraw() = runBlocking {
        service.sendDrawResults(DrawResult(hintonLottery, listOf("riley"), emptyList()))
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())

        profiles.saveProfile(profiles.getProfile("riley")!!.copy(notificationsEnabled = true))
        val juneLottery = hintonLottery.copy(id = "june")
        service.sendDrawResults(DrawResult(juneLottery, listOf("riley"), emptyList()))

        val rileyIds = notifications.getNotificationsFor("riley").map { it.id }
        assertEquals(listOf("draw_june_riley"), rileyIds)
    }

    @Test
    fun optedIn_profileThatCantBeRead_keepsThatUserAndChecksTheOthers() = runBlocking {
        // Jo's profile read fails (e.g. offline); Riley's says notifications are off
        val flaky = object : ProfileRepository by profiles {
            override suspend fun getProfile(id: String): UserProfile? {
                if (id == "jo") throw IllegalStateException("Read failed")
                return profiles.getProfile(id)
            }
        }
        val service = NotificationService(notifications, campsites, flaky)

        assertEquals(listOf("sam", "jo"), service.optedIn(listOf("sam", "jo", "riley")))
    }

    @Test
    fun sendDrawResults_oneProfileReadFails_stillSendsEveryoneElsesNotices() = runBlocking {
        val flaky = object : ProfileRepository by profiles {
            override suspend fun getProfile(id: String): UserProfile? {
                if (id == "sam") throw IllegalStateException("Read failed")
                return profiles.getProfile(id)
            }
        }
        NotificationService(notifications, campsites, flaky)
            .sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("jo", "riley")))

        assertEquals(1, notifications.getNotificationsFor("sam").size)
        assertEquals(1, notifications.getNotificationsFor("jo").size)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    // Sam's May stay at Priya's Hinton campsite, and a message about it saved as "m1"
    private val samsStay = Reservation(
        id = "res-hinton",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "hinton",
        campsiteName = "Hinton Ridge Campground",
    )
    private val fromOwner = Message(
        id = "m1",
        reservationId = "res-hinton",
        senderId = "owner-priya",
        text = "Thanks for booking!",
        createdAt = 1_000,
    )

    @Test
    fun sendNewMessage_fromTheOwner_addsANewMessageNoticeForTheEntrant() = runBlocking {
        service.sendNewMessage(samsStay, fromOwner)

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals("message_m1", notice.id)
        assertEquals(NotificationType.NEW_MESSAGE, notice.type)
        assertEquals("owner-priya", notice.senderId)
        assertEquals("hinton", notice.campsiteId)
        assertEquals("New message", notice.title)
        assertEquals("Hinton Ridge Campground: Thanks for booking!", notice.message)
        assertEquals(1_000L, notice.createdAt)
        assertFalse(notice.read)
        // tapping it opens the conversation
        assertEquals(NotificationTarget.OpenThread("res-hinton"), notice.target)
        assertTrue(notifications.getNotificationsFor("owner-priya").isEmpty())
    }

    @Test
    fun sendNewMessage_fromTheEntrant_notifiesTheOwner() = runBlocking {
        service.sendNewMessage(samsStay, fromOwner.copy(senderId = "sam", text = "Is there water?"))

        val notice = notifications.getNotificationsFor("owner-priya").single()
        assertEquals("Hinton Ridge Campground: Is there water?", notice.message)
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun sendNewMessage_toSomeoneWhoTurnedNotificationsOff_sendsNothing() = runBlocking {
        // Riley reserved too, but turned notifications off (US 01.10.01)
        service.sendNewMessage(samsStay.copy(userId = "riley"), fromOwner)

        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    @Test
    fun sendNewMessage_fromSomeoneOutsideTheConversation_sendsNothing() = runBlocking {
        service.sendNewMessage(samsStay, fromOwner.copy(senderId = "owner-daniel"))

        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
        assertTrue(notifications.getNotificationsFor("owner-priya").isEmpty())
    }

    @Test
    fun sendNewMessage_sameMessageTwice_keepsOneNotice() = runBlocking {
        service.sendNewMessage(samsStay, fromOwner)
        service.sendNewMessage(samsStay, fromOwner)

        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }

    private val samsRejectedStay = samsStay.copy(
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
        status = ReservationStatus.REJECTED,
        rejectionReason = "Forest fire warning",
    )

    @Test
    fun sendReservationRejected_addsOneRejectedNoticeFromTheOwnerWithTheReason() = runBlocking {
        assertTrue(service.sendReservationRejected(samsRejectedStay, now = 3_000))

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals("rejected_res-hinton", notice.id)
        assertEquals(NotificationType.RESERVATION_REJECTED, notice.type)
        assertEquals("owner-priya", notice.senderId)
        assertEquals("res-hinton", notice.reservationId)
        assertEquals("hinton", notice.campsiteId)
        assertNull(notice.lotteryId)
        assertNull(notice.batchId)
        assertEquals("Reservation rejected", notice.title)
        assertEquals(
            "Hinton Ridge Campground: 2027-05-08 to 2027-05-10. Reason: Forest fire warning",
            notice.message,
        )
        assertEquals(3_000L, notice.createdAt)
        assertFalse(notice.read)
        assertFalse(notice.shownOnDevice)
        assertTrue(notifications.getNotificationsFor("owner-priya").isEmpty())
    }

    @Test
    fun sendReservationRejected_noticeOpensTheReservation() = runBlocking {
        service.sendReservationRejected(samsRejectedStay, now = 3_000)

        val notice = notifications.getNotificationsFor("sam").single()
        assertEquals(NotificationTarget.OpenReservation("res-hinton"), notice.target)
    }

    @Test
    fun sendReservationRejected_twice_keepsOneNotice() = runBlocking {
        service.sendReservationRejected(samsRejectedStay, now = 3_000)
        service.sendReservationRejected(samsRejectedStay, now = 4_000)

        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }

    @Test
    fun sendReservationRejected_toSomeoneWhoTurnedNotificationsOff_sendsNothing() = runBlocking {
        assertFalse(service.sendReservationRejected(samsRejectedStay.copy(userId = "riley"), now = 3_000))

        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    @Test
    fun sendReservationRejected_notRejected_sendsNothing() = runBlocking {
        assertFalse(service.sendReservationRejected(samsStay, now = 3_000))
        assertFalse(
            service.sendReservationRejected(
                samsRejectedStay.copy(status = ReservationStatus.CANCELLED),
                now = 3_000,
            ),
        )

        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    @Test
    fun sendReservationRejected_withoutAReason_sendsNothing() = runBlocking {
        assertFalse(service.sendReservationRejected(samsRejectedStay.copy(rejectionReason = "  "), now = 3_000))

        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
    }

    // US 01.04.03 / T-12: preparing replacement win notices

    @Test
    fun prepareReplacementWinNotice_oneCampsite_usesReplacementIdPromotionTimeAndWinWordingWithoutWriting() = runBlocking {
        var writeCalled = false
        val counting = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                writeCalled = true
            }

            override suspend fun saveNotification(notification: Notification): String {
                writeCalled = true
                return notification.id
            }

            override suspend fun addNotification(notification: Notification): String {
                writeCalled = true
                return notification.id
            }
        }
        val svc = NotificationService(counting, campsites, profiles, formatDate = { "time $it" })

        val notice = svc.prepareReplacementWinNotice(hintonLottery, "jo", promotedAt = 1_750L)!!
        assertFalse(writeCalled)
        assertEquals("replacement_may_jo", notice.id)
        assertEquals("jo", notice.recipientId)
        assertEquals(NotificationType.WON, notice.type)
        assertEquals("system", notice.senderId)
        assertEquals("hinton", notice.campsiteId)
        assertEquals("may", notice.lotteryId)
        assertEquals("You won the lottery!", notice.title)
        assertEquals(
            "You won a spot at Hinton Ridge Campground. Reserve your nights before time 2000.",
            notice.message,
        )
        assertEquals(1_750L, notice.createdAt)
        assertFalse(notice.read)
        assertFalse(notice.shownOnDevice)
        assertEquals(NotificationTarget.OpenReserve("may"), notice.target)
    }

    @Test
    fun prepareReplacementWinNotice_twoCampsites_namesBothWithNullCampsiteId() = runBlocking {
        val bothLottery = hintonLottery.copy(campsiteIds = listOf("hinton", "banff"))
        val notice = service.prepareReplacementWinNotice(bothLottery, "jo", promotedAt = 1_800L)!!

        assertEquals(
            "You won a spot at Hinton Ridge Campground, Bow River Sites. " +
                "Reserve your nights before time 2000.",
            notice.message,
        )
        assertNull(notice.campsiteId)
    }

    @Test
    fun prepareReplacementWinNotice_optedOutRecipient_returnsNullWithoutWriting() = runBlocking {
        var addAllCalled = false
        val counting = object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                addAllCalled = true
            }
        }
        val notice = NotificationService(counting, campsites, profiles, formatDate = { "time $it" })
            .prepareReplacementWinNotice(hintonLottery, "riley", promotedAt = 1_750L)

        assertNull(notice)
        assertFalse(addAllCalled)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }
}
