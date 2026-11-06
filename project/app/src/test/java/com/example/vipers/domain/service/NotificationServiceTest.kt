/*
 * NotificationServiceTest.kt
 * Unit tests for NotificationService.sendDrawResults (US 01.03.01): the "You won" notices a draw
 * adds to winners' inboxes, using the fake repositories and a fixed date format.
 * Known issues: none. The inbox and markRead pass-throughs are covered by NotificationsControllerTest.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        now = { 999L },
        formatDate = { time -> "time $time" },
    )

    // a lottery for the Hinton campsite, drawn at time 500, whose booking ends at time 2000
    private val hintonLottery = Lottery(
        id = "may",
        campsiteIds = listOf("hinton"),
        bookingEnd = 2000L,
        drawnAt = 500L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        // the losers' notices come with US 01.03.02
        assertTrue(notifications.getNotificationsFor("jo").isEmpty())
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
        service.sendDrawResults(DrawResult(hintonLottery.copy(drawnAt = null), listOf("sam"), emptyList()))

        assertEquals(999L, notifications.getNotificationsFor("sam").single().createdAt)
    }
}
