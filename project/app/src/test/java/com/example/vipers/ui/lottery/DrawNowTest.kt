/*
 * DrawNowTest.kt
 * Unit tests for the owner's Draw now (US 02.05.01b): LotteryDrawer.drawNow closes registration
 * first when the owner draws early, and LotteryDetailsController only lets the owner draw, says
 * what happened, and reloads the lottery.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository
import java.util.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawNowTest {

    // registration runs from 1000 to 2000, and booking from 3000 to 4000
    private val may = Lottery(
        id = "may",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        registrationStart = 1000,
        registrationEnd = 2000,
        bookingStart = 3000,
        bookingEnd = 4000,
        winnersCount = 2,
    )

    private fun waiting(userId: String) =
        LotteryEntry(
            id = LotteryEntry.idFor("may", userId), lotteryId = "may", userId = userId,
            status = EntryStatus.WAITING,
        )

    private val entries = listOf(waiting("sam"), waiting("alex"), waiting("jordan"))

    private fun drawerFor(repository: LotteryRepository) = LotteryDrawer(
        repository,
        FakeNotificationRepository(),
        FakeCampsiteRepository(listOf(Campsite(id = "hinton", name = "Hinton Ridge"))),
        FakeProfileRepository(),
        random = Random(3),
    )

    /** A loaded Lottery Details controller for [userId] looking at the May lottery. */
    private fun controllerFor(
        repository: LotteryRepository,
        userId: String = "owner-priya",
    ): LotteryDetailsController = runBlocking {
        val controller = LotteryDetailsController(
            lotteryRepository = repository,
            profileRepository = FakeProfileRepository(),
            lotteryId = "may",
            currentUserId = userId,
            lotteryDrawer = drawerFor(repository),
        )
        controller.fetchData()
        controller
    }

    @Test
    fun drawNow_whileRegistrationIsOpen_closesItAndDraws() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)

        val result = drawerFor(lotteries).drawNow("may", now = 1500)

        assertEquals(LotteryDrawResult.Success(winnerCount = 2, totalCount = 3), result)
        val drawn = lotteries.getLottery("may")!!
        assertEquals(1500L, drawn.registrationEnd)
        assertNotNull(drawn.drawnAt)
        assertEquals(2, lotteries.getEntries("may").count { it.status == EntryStatus.WON })
    }

    @Test
    fun drawNow_afterRegistrationEnds_keepsItsEndTime() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)

        val result = drawerFor(lotteries).drawNow("may", now = 2500)

        assertTrue(result is LotteryDrawResult.Success)
        assertEquals(2000L, lotteries.getLottery("may")?.registrationEnd)
    }

    @Test
    fun drawNow_beforeRegistrationStarts_doesNotDraw() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)

        val result = drawerFor(lotteries).drawNow("may", now = 500)

        assertTrue(result is LotteryDrawResult.Error)
        assertNull(lotteries.getLottery("may")?.drawnAt)
    }

    @Test
    fun drawNow_onceDrawn_doesNotDrawAgain() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)
        val drawer = drawerFor(lotteries)
        drawer.drawNow("may", now = 1500)
        val firstDraw = lotteries.getLottery("may")?.drawnAt

        val again = drawer.drawNow("may", now = 1600)

        assertTrue(again is LotteryDrawResult.Error)
        assertEquals(firstDraw, lotteries.getLottery("may")?.drawnAt)
    }

    @Test
    fun canDraw_forTheOwnerWhileOpen_isTrue() {
        val controller = controllerFor(FakeLotteryRepository(listOf(may), entries))

        assertTrue(controller.canDraw(now = 1500))
    }

    @Test
    fun canDraw_forAnEntrant_isFalse() {
        val controller = controllerFor(FakeLotteryRepository(listOf(may), entries), userId = "sam")

        assertFalse(controller.canDraw(now = 1500))
    }

    @Test
    fun canDraw_onceDrawn_isFalse() {
        val controller = controllerFor(FakeLotteryRepository(listOf(may.copy(drawnAt = 1800))))

        assertFalse(controller.canDraw(now = 2500))
    }

    @Test
    fun performDrawNow_saysHowManyWonAndReloads() = runBlocking {
        val controller = controllerFor(FakeLotteryRepository(listOf(may), entries))

        controller.performDrawNow(now = 1500)

        assertEquals("Draw done: 2 of 3 entrants won", controller.drawMessage)
        assertFalse(controller.drawFailed)
        assertFalse(controller.isDrawing)
        assertNotNull(controller.lottery?.drawnAt)
        assertEquals(0, controller.waitingEntries.size)
    }

    @Test
    fun performDrawNow_withNobodyWaiting_saysSo() = runBlocking {
        val controller = controllerFor(FakeLotteryRepository(listOf(may)))

        controller.performDrawNow(now = 2500)

        assertEquals("Draw done: nobody had joined the waiting list", controller.drawMessage)
        assertFalse(controller.drawFailed)
    }

    @Test
    fun performDrawNow_byAnEntrant_doesNothing() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)
        val controller = controllerFor(lotteries, userId = "sam")

        controller.performDrawNow(now = 1500)

        assertNull(controller.drawMessage)
        assertNull(lotteries.getLottery("may")?.drawnAt)
    }

    @Test
    fun performDrawNow_whenTheDrawFails_showsAnError() = runBlocking {
        // a repository that can't read the entries, like Firestore when the phone goes offline
        val fake = FakeLotteryRepository(listOf(may), entries)
        var offline = false
        val failing = object : LotteryRepository by fake {
            override suspend fun getEntries(lotteryId: String): List<LotteryEntry> {
                if (offline) throw IllegalStateException("offline")
                return fake.getEntries(lotteryId)
            }
        }
        val controller = controllerFor(failing)
        offline = true

        controller.performDrawNow(now = 2500)

        assertEquals("Couldn't run the draw. Try again.", controller.drawMessage)
        assertTrue(controller.drawFailed)
        assertFalse(controller.isDrawing)
    }

    @Test
    fun performDrawNow_whenCancelled_rethrowsWithoutAMessage() {
        val fake = FakeLotteryRepository(listOf(may), entries)
        val leaving = object : LotteryRepository by fake {
            override suspend fun closeRegistration(lotteryId: String, now: Long): Boolean =
                throw CancellationException("left the screen")
        }
        val controller = controllerFor(leaving)

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.performDrawNow(now = 1500) }
        }

        assertNull(controller.drawMessage)
        assertFalse(controller.isDrawing)
    }
}
