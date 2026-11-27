/*
 * DrawAndNotifyWorkerTest.kt
 * Unit tests for DrawAndNotifyWorker (US 02.05.01b): when the app opens it draws every lottery
 * whose registration has ended, never one that is still open, never one twice, and keeps going
 * when one lottery fails.
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawAndNotifyWorkerTest {

    // registration runs from 1000 to 2000, and booking from 3000 to 4000
    private val may = Lottery(
        id = "may",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        registrationStart = 1000,
        registrationEnd = 2000,
        bookingStart = 3000,
        bookingEnd = 4000,
        winnersCount = 1,
    )

    // still open at the times these tests use
    private val june =
        may.copy(id = "june", registrationEnd = 9000, bookingStart = 9500, bookingEnd = 9900)

    private fun waiting(lotteryId: String, userId: String) =
        LotteryEntry(
            id = LotteryEntry.idFor(lotteryId, userId), lotteryId = lotteryId, userId = userId,
            status = EntryStatus.WAITING,
        )

    private val entries = listOf(
        waiting("may", "sam"), waiting("may", "alex"), waiting("june", "jordan"),
    )

    private val notifications = FakeNotificationRepository()

    /** A worker over [repository], with a fixed Random so draws are repeatable. */
    private fun workerFor(repository: LotteryRepository): DrawAndNotifyWorker.DueDraws {
        val campsites =
            FakeCampsiteRepository(listOf(Campsite(id = "hinton", name = "Hinton Ridge")))
        val drawer = LotteryDrawer(
            repository, notifications, campsites, FakeProfileRepository(), random = Random(7),
        )
        return DrawAndNotifyWorker.DueDraws(repository, drawer)
    }

    @Test
    fun runDueDraws_drawsALotteryWhoseRegistrationEnded() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)

        val drawn = workerFor(lotteries).runDueDraws(now = 2500)

        assertEquals(1, drawn)
        assertNotNull(lotteries.getLottery("may")?.drawnAt)
        val statuses = lotteries.getEntries("may").map { it.status }.toSet()
        assertEquals(setOf(EntryStatus.WON, EntryStatus.LOST), statuses)
    }

    @Test
    fun runDueDraws_sendsTheResultsToTheInbox() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)

        workerFor(lotteries).runDueDraws(now = 2500)

        assertEquals(1, notifications.getNotificationsFor("sam").size)
        assertEquals(1, notifications.getNotificationsFor("alex").size)
    }

    @Test
    fun runDueDraws_neverDrawsWhileRegistrationIsOpen() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may, june), entries)

        workerFor(lotteries).runDueDraws(now = 2500)

        assertNull(lotteries.getLottery("june")?.drawnAt)
        assertEquals(EntryStatus.WAITING, lotteries.getEntry("june", "jordan")?.status)
    }

    @Test
    fun runDueDraws_runningAgain_doesNotDrawTwice() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may), entries)
        val worker = workerFor(lotteries)
        worker.runDueDraws(now = 2500)
        val firstDraw = lotteries.getLottery("may")?.drawnAt

        val drawnAgain = worker.runDueDraws(now = 2600)

        assertEquals(0, drawnAgain)
        assertEquals(firstDraw, lotteries.getLottery("may")?.drawnAt)
        assertEquals(1, notifications.getNotificationsFor("sam").size)
    }

    @Test
    fun runDueDraws_withNobodyOnTheWaitingList_stillClosesTheLottery() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(may))

        assertEquals(1, workerFor(lotteries).runDueDraws(now = 2500))

        assertNotNull(lotteries.getLottery("may")?.drawnAt)
    }

    @Test
    fun runDueDraws_whenOneLotteryFails_stillDrawsTheOthers() = runBlocking {
        val july = may.copy(id = "july")
        // a repository that can't read July's entries, like a document that failed to load
        val fake = FakeLotteryRepository(listOf(july, may), entries)
        val lotteries = object : LotteryRepository by fake {
            override suspend fun getEntries(lotteryId: String): List<LotteryEntry> {
                if (lotteryId == "july") throw IllegalStateException("couldn't read entries")
                return fake.getEntries(lotteryId)
            }
        }

        val drawn = workerFor(lotteries).runDueDraws(now = 2500)

        assertEquals(1, drawn)
        assertNotNull(fake.getLottery("may")?.drawnAt)
        assertNull(fake.getLottery("july")?.drawnAt)
    }

    @Test
    fun runDueDraws_whenTheLotteriesCantBeLoaded_drawsNothing() = runBlocking {
        // e.g. the phone is offline when the app opens
        val offline = object : LotteryRepository by FakeLotteryRepository(listOf(may), entries) {
            override suspend fun getLotteriesToDraw(now: Long): List<Lottery> =
                throw IllegalStateException("offline")
        }

        assertEquals(0, workerFor(offline).runDueDraws(now = 2500))
    }

    @Test
    fun runDueDraws_withNothingDue_drawsNothing() = runBlocking {
        val lotteries = FakeLotteryRepository(listOf(june), entries)

        assertEquals(0, workerFor(lotteries).runDueDraws(now = 2500))
        assertTrue(notifications.getNotificationsFor("jordan").isEmpty())
    }
}
