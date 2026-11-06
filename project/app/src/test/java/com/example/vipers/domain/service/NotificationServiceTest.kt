/*
 * NotificationServiceTest.kt
 * Unit tests for NotificationService.sendDrawResults (US 01.03.01, 01.03.02): the "You won" and
 * "Not selected" notices a draw adds to inboxes, using the fake repositories and a fixed date format.
 * Known issues: none. The inbox and markRead pass-throughs are covered by NotificationsControllerTest.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.NotificationRepository
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

    // a fixed date format, so the tests don't depend on the phone's language or time zone
    private val service = NotificationService(
        notifications,
        campsites,
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
        NotificationService(counting, campsites, formatDate = { "time $it" })
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
        NotificationService(counting, campsites).sendDrawResults(
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
}
