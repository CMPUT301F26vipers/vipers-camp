/*
 * NotificationServiceTest.kt
 * Unit tests for NotificationService.sendDrawResults (US 01.03.01, 01.03.02): the "You won" and
 * "Not selected" notices a draw adds to inboxes, using the fake repositories and a fixed date format.
 * Also tests optedIn and that the draw skips users who turned notifications off (US 01.10.01),
 * and the "New message" notice sendNewMessage sends to the other side of a conversation
 * (US 02.12.01), and the owner's sent notifications sentNotifications lists (US 02.10.02).
 * Known issues: none. The inbox and markRead pass-throughs are covered by NotificationsControllerTest.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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
        FakeLotteryRepository(),
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
        NotificationService(counting, campsites, FakeProfileRepository(), FakeLotteryRepository(), formatDate = { "t $it" })
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
        NotificationService(counting, campsites, profiles, FakeLotteryRepository()).sendDrawResults(
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
        NotificationService(counting, campsites, profiles, FakeLotteryRepository()).sendDrawResults(
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
        val service = NotificationService(notifications, campsites, flaky, FakeLotteryRepository())

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
        NotificationService(notifications, campsites, flaky, FakeLotteryRepository())
            .sendDrawResults(DrawResult(hintonLottery, listOf("sam"), listOf("jo", "riley")))

        assertEquals(1, notifications.getNotificationsFor("sam").size)
        assertEquals(1, notifications.getNotificationsFor("jo").size)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    private val ownersLottery = Lottery(
        id = "june",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
    )
    private val twoCampsiteLottery = Lottery(
        id = "summer",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton", "banff"),
    )
    private val entrantProfiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "jo", name = "Jo Chen"),
            UserProfile(id = "kim", name = "Kim Lee"),
            UserProfile(id = "riley", name = "Riley Ng", notificationsEnabled = false),
        ),
    )

    private fun entry(
        userId: String,
        joinedAt: Long,
        status: EntryStatus = EntryStatus.WAITING,
        lotteryId: String = "june",
        id: String = LotteryEntry.idFor(lotteryId, userId),
    ) = LotteryEntry(id = id, lotteryId = lotteryId, userId = userId, joinedAt = joinedAt, status = status)

    private fun broadcastService(
        vararg entries: LotteryEntry,
        repo: NotificationRepository = notifications,
        lotteries: List<Lottery> = listOf(ownersLottery, twoCampsiteLottery),
    ) = NotificationService(repo, campsites, entrantProfiles, FakeLotteryRepository(lotteries, entries.toList()))

    private fun countingAddAll(calls: MutableList<List<Notification>>) =
        object : NotificationRepository by notifications {
            override suspend fun addAll(notifications: List<Notification>) {
                calls.add(notifications)
            }
        }

    @Test
    fun sendOwnerBroadcast_everyEntrant_getsOneOwnerBroadcastWithTheRightFields() = runBlocking {
        val result = broadcastService(entry("sam", 1), entry("jo", 2))
            .sendOwnerBroadcast("owner-priya", "june", "  The cookhouse reopens next week.\n", "b1", now = 900L)

        assertEquals(OwnerBroadcastResult.Sent("b1", 2), result)
        for (userId in listOf("sam", "jo")) {
            assertEquals(
                Notification(
                    id = "broadcast_b1_$userId",
                    recipientId = userId,
                    senderId = "owner-priya",
                    campsiteId = "hinton",
                    lotteryId = "june",
                    type = NotificationType.OWNER_BROADCAST,
                    title = "Message from owner",
                    message = "The cookhouse reopens next week.",
                    read = false,
                    shownOnDevice = false,
                    batchId = "b1",
                    createdAt = 900L,
                ),
                notifications.getNotificationsFor(userId).single(),
            )
        }
    }

    @Test
    fun sendOwnerBroadcast_savesEveryoneInOneAddAllWithOneBatchAndTime() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        broadcastService(entry("sam", 1), entry("jo", 2), entry("kim", 3), repo = countingAddAll(calls))
            .sendOwnerBroadcast("owner-priya", "june", "Fire ban this weekend.", now = 900L)

        assertEquals(1, calls.size)
        assertEquals(setOf("sam", "jo", "kim"), calls[0].map { it.recipientId }.toSet())
        assertEquals(1, calls[0].map { it.batchId }.distinct().size)
        assertTrue(calls[0].all { it.batchId!!.isNotBlank() && it.id == "broadcast_${it.batchId}_${it.recipientId}" })
        assertTrue(calls[0].all { it.createdAt == 900L })
    }

    @Test
    fun sendOwnerBroadcast_afterTheDraw_reachesEveryResultOnTheList() = runBlocking {
        val result = broadcastService(
            entry("sam", 1, EntryStatus.WON),
            entry("jo", 2, EntryStatus.LOST),
            entry("kim", 3, EntryStatus.DECLINED),
        ).sendOwnerBroadcast("owner-priya", "june", "Thanks for entering.", "b1")

        assertEquals(OwnerBroadcastResult.Sent("b1", 3), result)
        for (userId in listOf("sam", "jo", "kim")) {
            assertEquals(1, notifications.getNotificationsFor(userId).size)
        }
    }

    @Test
    fun sendOwnerBroadcast_optedOutEntrant_getsNothing() = runBlocking {
        val result = broadcastService(entry("sam", 1), entry("riley", 2))
            .sendOwnerBroadcast("owner-priya", "june", "Gate code changed.", "b1")

        assertEquals(OwnerBroadcastResult.Sent("b1", 1), result)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }

    @Test
    fun sendOwnerBroadcast_entrantWithNoProfile_getsNothing() = runBlocking {
        val result = broadcastService(entry("sam", 1), entry("deleted-user", 2))
            .sendOwnerBroadcast("owner-priya", "june", "Gate code changed.", "b1")

        assertEquals(OwnerBroadcastResult.Sent("b1", 1), result)
        assertTrue(notifications.getNotificationsFor("deleted-user").isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_emptyOrWhitespaceMessage_savesNothing() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val service = broadcastService(entry("sam", 1), repo = countingAddAll(calls))

        assertEquals(OwnerBroadcastResult.EmptyMessage, service.sendOwnerBroadcast("owner-priya", "june", ""))
        assertEquals(OwnerBroadcastResult.EmptyMessage, service.sendOwnerBroadcast("owner-priya", "june", " \n\t "))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_senderIsNotTheOwner_savesNothing() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val result = broadcastService(entry("sam", 1), repo = countingAddAll(calls))
            .sendOwnerBroadcast("sam", "june", "Hello everyone.")

        assertEquals(OwnerBroadcastResult.NotOwner, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_missingLottery_savesNothing() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val result = broadcastService(entry("sam", 1), repo = countingAddAll(calls))
            .sendOwnerBroadcast("owner-priya", "no-such-lottery", "Hello everyone.")

        assertEquals(OwnerBroadcastResult.LotteryNotFound, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_removedLottery_savesNothing() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val result = broadcastService(
            entry("sam", 1),
            repo = countingAddAll(calls),
            lotteries = listOf(ownersLottery.copy(removed = true)),
        ).sendOwnerBroadcast("owner-priya", "june", "Hello everyone.")

        assertEquals(OwnerBroadcastResult.LotteryNotFound, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_lotteryWithSeveralCampsites_leavesCampsiteIdNull() = runBlocking {
        broadcastService(entry("sam", 1, lotteryId = "summer"))
            .sendOwnerBroadcast("owner-priya", "summer", "Hello everyone.", "b1")

        val notice = notifications.getNotificationsFor("sam").single()
        assertNull(notice.campsiteId)
        assertEquals("summer", notice.lotteryId)
    }

    @Test
    fun sendOwnerBroadcast_removedEntry_getsNothing() = runBlocking {
        val result = broadcastService(entry("sam", 1), entry("kim", 2, EntryStatus.REMOVED))
            .sendOwnerBroadcast("owner-priya", "june", "Hello everyone.", "b1")

        assertEquals(OwnerBroadcastResult.Sent("b1", 1), result)
        assertTrue(notifications.getNotificationsFor("kim").isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_entrantListedTwice_getsItOnce() = runBlocking {
        val result = broadcastService(entry("sam", 1), entry("sam", 2, id = "legacy-sam"), entry("jo", 3))
            .sendOwnerBroadcast("owner-priya", "june", "Hello everyone.", "b1")

        assertEquals(OwnerBroadcastResult.Sent("b1", 2), result)
        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }

    @Test
    fun sendOwnerBroadcast_emptyWaitingList_reportsNobodyToNotify() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val result = broadcastService(repo = countingAddAll(calls))
            .sendOwnerBroadcast("owner-priya", "june", "Hello everyone.")

        assertEquals(OwnerBroadcastResult.NobodyToNotify, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_everyoneOptedOut_reportsNobodyToNotify() = runBlocking {
        val calls = mutableListOf<List<Notification>>()
        val result = broadcastService(entry("riley", 1), entry("kim", 2, EntryStatus.REMOVED), repo = countingAddAll(calls))
            .sendOwnerBroadcast("owner-priya", "june", "Hello everyone.")

        assertEquals(OwnerBroadcastResult.NobodyToNotify, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sendOwnerBroadcast_sentAgainWithTheSameBatch_keepsOneReadNoticeEach() = runBlocking {
        val service = broadcastService(entry("sam", 1), entry("jo", 2))
        service.sendOwnerBroadcast("owner-priya", "june", "Hello everyone.", "b1")
        service.markRead("broadcast_b1_sam")

        service.sendOwnerBroadcast("owner-priya", "june", "Hello everyone.", "b1")

        assertEquals(listOf("broadcast_b1_sam"), notifications.getNotificationsFor("sam").map { it.id })
        assertTrue(notifications.getNotificationsFor("sam").single().read)
        assertEquals(1, notifications.getNotificationsFor("jo").size)
    }

    @Test
    fun sendOwnerBroadcast_twoSends_getTwoDifferentBatches() = runBlocking {
        val service = broadcastService(entry("sam", 1))
        val first = service.sendOwnerBroadcast("owner-priya", "june", "First.") as OwnerBroadcastResult.Sent
        val second = service.sendOwnerBroadcast("owner-priya", "june", "Second.") as OwnerBroadcastResult.Sent

        assertTrue(first.batchId != second.batchId)
        assertEquals(2, notifications.getNotificationsFor("sam").map { it.batchId }.distinct().size)
    }

    @Test
    fun waitingListRecipients_skipsRemovedAndDuplicatesButKeepsOptedOut() = runBlocking {
        val recipients = broadcastService(
            entry("jo", 2, EntryStatus.LOST),
            entry("sam", 1),
            entry("kim", 3, EntryStatus.REMOVED),
            entry("riley", 4),
            entry("sam", 5, id = "legacy-sam"),
        ).waitingListRecipients("june")

        assertEquals(listOf("sam", "jo", "riley"), recipients)
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
        val svc = NotificationService(counting, campsites, profiles, FakeLotteryRepository(), formatDate = { "time $it" })

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
        val notice = NotificationService(counting, campsites, profiles, FakeLotteryRepository(), formatDate = { "time $it" })
            .prepareReplacementWinNotice(hintonLottery, "riley", promotedAt = 1_750L)

        assertNull(notice)
        assertFalse(addAllCalled)
        assertTrue(notifications.getNotificationsFor("riley").isEmpty())
    }

    private fun sentBroadcast(
        recipientId: String,
        batchId: String,
        lotteryId: String,
        campsiteId: String?,
        createdAt: Long,
        senderId: String = "owner-priya",
        message: String = "The cookhouse reopens next week.",
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

    private val mayWeekends = ownersLottery.copy(nightsDescription = "May weekends", nightsOffered = listOf("2027-05-08"))

    private fun sentService(
        vararg saved: Notification,
        lotteries: List<Lottery> = listOf(mayWeekends, twoCampsiteLottery),
        campsiteRepo: CampsiteRepository = campsites,
        lotteryRepo: LotteryRepository = FakeLotteryRepository(lotteries),
        repo: NotificationRepository = FakeNotificationRepository(saved.toList()),
    ) = NotificationService(repo, campsiteRepo, entrantProfiles, lotteryRepo)

    @Test
    fun sentNotifications_twoSends_comeBackNewestFirstWithTheirRecipientCounts() = runBlocking {
        val lotteries = FakeLotteryRepository(
            listOf(mayWeekends),
            listOf(entry("sam", 1), entry("jo", 2), entry("riley", 3)),
        )
        val service = NotificationService(notifications, campsites, entrantProfiles, lotteries)
        service.sendOwnerBroadcast("owner-priya", "june", "First.", "b1", now = 100L)
        service.sendOwnerBroadcast("owner-priya", "june", "Second.", "b2", now = 200L)

        val items = service.sentNotifications("owner-priya")

        assertEquals(listOf("Second.", "First."), items.map { it.sent.message })
        assertEquals(listOf(200L, 100L), items.map { it.sent.sentAt })
        assertEquals(listOf(2, 2), items.map { it.sent.recipientCount })
    }

    @Test
    fun sentNotifications_otherOwnersSendsAndNonBroadcasts_areLeftOut() = runBlocking {
        val items = sentService(
            sentBroadcast("sam", "b1", "june", "hinton", 100),
            sentBroadcast("sam", "b2", "june", "hinton", 200, senderId = "owner-daniel"),
            Notification(
                id = "rejected_r1", recipientId = "sam", senderId = "owner-priya",
                type = NotificationType.RESERVATION_REJECTED, createdAt = 300,
            ),
            Notification(
                id = "message_m1", recipientId = "sam", senderId = "owner-priya",
                type = NotificationType.NEW_MESSAGE, createdAt = 400,
            ),
        ).sentNotifications("owner-priya")

        assertEquals(listOf("b1"), items.map { it.sent.batchId })
    }

    @Test
    fun sentNotifications_oneCampsiteLottery_isLabelledWithTheCampsiteName() = runBlocking {
        val item = sentService(sentBroadcast("sam", "b1", "june", "hinton", 100))
            .sentNotifications("owner-priya").single()

        assertEquals("Hinton Ridge Campground", item.campsiteLabel)
    }

    @Test
    fun sentNotifications_twoCampsiteLottery_namesBothCampsitesInOrder() = runBlocking {
        val item = sentService(sentBroadcast("sam", "b1", "summer", null, 100))
            .sentNotifications("owner-priya").single()

        assertEquals("Hinton Ridge Campground, Bow River Sites", item.campsiteLabel)
    }

    @Test
    fun sentNotifications_lotteryLabel_usesTheNightsDescription() = runBlocking {
        val item = sentService(sentBroadcast("sam", "b1", "june", "hinton", 100))
            .sentNotifications("owner-priya").single()

        assertEquals("May weekends", item.lotteryLabel)
    }

    @Test
    fun sentNotifications_noOrBlankNightsDescription_usesTheNightsLabel() = runBlocking {
        val items = sentService(
            sentBroadcast("sam", "b1", "june", "hinton", 200),
            sentBroadcast("sam", "b2", "summer", null, 100),
            lotteries = listOf(
                mayWeekends.copy(nightsDescription = null),
                twoCampsiteLottery.copy(nightsDescription = "  ", nightsOffered = listOf("2027-07-03", "2027-07-04")),
            ),
        ).sentNotifications("owner-priya")

        assertEquals(listOf("2027-05-08", "2027-07-03"), items.map { it.lotteryLabel })
    }

    @Test
    fun sentNotifications_removedOrMissingCampsite_isLabelledRemovedCampsite() = runBlocking {
        val campsitesWithRemoved = FakeCampsiteRepository(
            listOf(
                Campsite(id = "hinton", name = "Hinton Ridge Campground", removed = true),
                Campsite(id = "banff", name = "Bow River Sites"),
            ),
        )
        val items = sentService(
            sentBroadcast("sam", "b1", "june", "hinton", 300),
            sentBroadcast("sam", "b2", "summer", null, 200),
            sentBroadcast("sam", "b3", "june", "gone", 100),
            campsiteRepo = campsitesWithRemoved,
        ).sentNotifications("owner-priya")

        assertEquals(
            listOf("Removed campsite", "Removed campsite, Bow River Sites", "Removed campsite"),
            items.map { it.campsiteLabel },
        )
    }

    @Test
    fun sentNotifications_missingLottery_isLabelledRemovedLotteryAndStillListed() = runBlocking {
        val item = sentService(
            sentBroadcast("sam", "b1", "deleted", "hinton", 100),
            lotteries = emptyList(),
        ).sentNotifications("owner-priya").single()

        assertEquals("b1", item.sent.batchId)
        assertEquals("Removed lottery", item.lotteryLabel)
        assertEquals("Hinton Ridge Campground", item.campsiteLabel)
    }

    @Test
    fun sentNotifications_campsiteOrLotteryReadFails_stillListsTheSend() = runBlocking {
        val failingCampsites = object : CampsiteRepository by campsites {
            override suspend fun getCampsite(id: String): Campsite? = throw IOException("offline")
        }
        val failingLotteries = object : LotteryRepository by FakeLotteryRepository(listOf(mayWeekends)) {
            override suspend fun getLottery(id: String): Lottery? = throw IOException("offline")
        }
        val item = sentService(
            sentBroadcast("sam", "b1", "june", "hinton", 100),
            campsiteRepo = failingCampsites,
            lotteryRepo = failingLotteries,
        ).sentNotifications("owner-priya").single()

        assertEquals("Removed campsite", item.campsiteLabel)
        assertEquals("Removed lottery", item.lotteryLabel)
    }

    @Test
    fun sentNotifications_sendsSharingALotteryAndCampsites_loadEachOnce() = runBlocking {
        val campsiteCalls = mutableListOf<String>()
        val lotteryCalls = mutableListOf<String>()
        val countingCampsites = object : CampsiteRepository by campsites {
            override suspend fun getCampsite(id: String): Campsite? {
                campsiteCalls.add(id)
                return campsites.getCampsite(id)
            }
        }
        val fakeLotteries = FakeLotteryRepository(listOf(mayWeekends, twoCampsiteLottery))
        val countingLotteries = object : LotteryRepository by fakeLotteries {
            override suspend fun getLottery(id: String): Lottery? {
                lotteryCalls.add(id)
                return fakeLotteries.getLottery(id)
            }
        }
        sentService(
            sentBroadcast("sam", "b1", "june", "hinton", 100),
            sentBroadcast("jo", "b1", "june", "hinton", 100),
            sentBroadcast("sam", "b2", "june", "hinton", 200),
            sentBroadcast("sam", "b3", "summer", null, 300),
            sentBroadcast("sam", "b4", "summer", null, 400),
            campsiteRepo = countingCampsites,
            lotteryRepo = countingLotteries,
        ).sentNotifications("owner-priya")

        assertEquals(listOf("june", "summer"), lotteryCalls.sorted())
        assertEquals(listOf("banff", "hinton"), campsiteCalls.sorted())
    }

    @Test
    fun sentNotifications_ownerWithNoSends_isEmpty() = runBlocking {
        val items = sentService(sentBroadcast("sam", "b1", "june", "hinton", 100))
            .sentNotifications("owner-daniel")

        assertTrue(items.isEmpty())
    }

    @Test
    fun sentNotifications_broadcastsReadFails_throws() {
        val offline = object : NotificationRepository by notifications {
            override suspend fun getOwnerBroadcasts(senderId: String): List<Notification> =
                throw IOException("offline")
        }

        assertThrows(IOException::class.java) {
            runBlocking { sentService(repo = offline).sentNotifications("owner-priya") }
        }
    }
}
