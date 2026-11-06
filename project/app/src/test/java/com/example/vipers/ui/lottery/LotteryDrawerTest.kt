/*
 * LotteryDrawerTest.kt
 * Unit tests for LotteryDrawer covering winner selection, concurrency, recovery,
 * terminal status preservation, notification delivery, and multi-campsite formatting, plus the
 * losers' "Not selected" notices (US 01.03.02).
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.NotificationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

class LotteryDrawerTest {

    private lateinit var lotteryRepository: FakeLotteryRepository
    private lateinit var notificationRepository: FakeNotificationRepository
    private lateinit var campsiteRepository: FakeCampsiteRepository
    private lateinit var drawer: LotteryDrawer

    private val now = 2_000_000_000L
    private val regStart = 1_000_000_000L
    private val regEnd = 1_500_000_000L // registration closed at 'now'
    private val bookStart = 1_600_000_000L
    private val bookEnd = 2_500_000_000L

    @Before
    fun setUp() = runTest {
        lotteryRepository = FakeLotteryRepository()
        notificationRepository = FakeNotificationRepository()
        campsiteRepository = FakeCampsiteRepository()
        campsiteRepository.addCampsite(Campsite(id = "camp-1", name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", name = "Maple Grove"))

        drawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            random = Random(42),
        )
    }

    private suspend fun createTestLottery(
        campsiteIds: List<String> = listOf("camp-1"),
        winnersCount: Int = 2,
        nightsDesc: String? = null,
    ): Lottery {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = campsiteIds,
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            nightsDescription = nightsDesc,
            registrationStart = regStart,
            registrationEnd = regEnd,
            bookingStart = bookStart,
            bookingEnd = bookEnd,
            winnersCount = winnersCount,
        )
        val id = lotteryRepository.addLottery(lottery)
        return lotteryRepository.getLottery(id)!!
    }

    @Test
    fun draw_exactConfiguredWinnersCount_partitionsCorrectly() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        for (i in 1..5) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-$i", status = EntryStatus.WAITING)
            )
        }

        val result = drawer.drawLottery(lottery.id, now)
        assertTrue(result is LotteryDrawResult.Success)
        val success = result as LotteryDrawResult.Success
        assertEquals(2, success.winnerCount)
        assertEquals(5, success.totalCount)

        val updatedEntries = lotteryRepository.getEntries(lottery.id)
        val wonCount = updatedEntries.count { it.status == EntryStatus.WON }
        val lostCount = updatedEntries.count { it.status == EntryStatus.LOST }
        assertEquals(2, wonCount)
        assertEquals(3, lostCount)

        val updatedLottery = lotteryRepository.getLottery(lottery.id)!!
        assertEquals(now, updatedLottery.drawnAt)
        assertEquals(2, updatedLottery.winnerCount)
        assertEquals(5, updatedLottery.participantCount)
    }

    @Test
    fun draw_fewerEntrantsThanWinnersCount_selectsAllEntrants() = runTest {
        val lottery = createTestLottery(winnersCount = 5)
        for (i in 1..2) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-$i", status = EntryStatus.WAITING)
            )
        }

        val result = drawer.drawLottery(lottery.id, now)
        assertTrue(result is LotteryDrawResult.Success)
        val success = result as LotteryDrawResult.Success
        assertEquals(2, success.winnerCount)
        assertEquals(2, success.totalCount)

        val updatedEntries = lotteryRepository.getEntries(lottery.id)
        assertEquals(2, updatedEntries.count { it.status == EntryStatus.WON })
        assertEquals(0, updatedEntries.count { it.status == EntryStatus.LOST })
    }

    @Test
    fun draw_emptyWaitingList_closesCleanlyWithoutErrorOrNotifications() = runTest {
        val lottery = createTestLottery(winnersCount = 3)

        val result = drawer.drawLottery(lottery.id, now)
        assertEquals(LotteryDrawResult.EmptyList, result)

        val updatedLottery = lotteryRepository.getLottery(lottery.id)!!
        assertEquals(now, updatedLottery.drawnAt)
        assertEquals(0, updatedLottery.winnerCount)
        assertEquals(0, updatedLottery.participantCount)

        val notifs = notificationRepository.getNotificationsFor("user-1")
        assertTrue(notifs.isEmpty())

        // Repeated draw/recovery call remains successful and sends no notifications
        val repeatedResult = drawer.drawLottery(lottery.id, now)
        assertEquals(LotteryDrawResult.EmptyList, repeatedResult)
        assertTrue(notificationRepository.getNotificationsFor("user-1").isEmpty())
    }

    @Test
    fun draw_registrationStillOpen_returnsError() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING)
        )

        // Draw attempted before registrationEnd
        val result = drawer.drawLottery(lottery.id, regEnd - 1000)
        assertTrue(result is LotteryDrawResult.Error)
        assertEquals("Registration is still open", (result as LotteryDrawResult.Error).message)

        val untouchedLottery = lotteryRepository.getLottery(lottery.id)!!
        assertNull(untouchedLottery.drawnAt)
    }

    @Test
    fun draw_reproducibleWithSameSeed_differentWithDifferentSeed() = runTest {
        val lotteryA = createTestLottery(winnersCount = 2)
        val lotteryB = createTestLottery(winnersCount = 2)
        val lotteryC = createTestLottery(winnersCount = 2)

        for (i in 1..10) {
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryA.id, userId = "user-$i", status = EntryStatus.WAITING))
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryB.id, userId = "user-$i", status = EntryStatus.WAITING))
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryC.id, userId = "user-$i", status = EntryStatus.WAITING))
        }

        val drawer1 = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, Random(12345))
        val drawer2 = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, Random(12345))
        val drawer3 = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, Random(99999))

        drawer1.drawLottery(lotteryA.id, now)
        drawer2.drawLottery(lotteryB.id, now)
        drawer3.drawLottery(lotteryC.id, now)

        val winnersA = lotteryRepository.getEntries(lotteryA.id).filter { it.status == EntryStatus.WON }.map { it.userId }
        val winnersB = lotteryRepository.getEntries(lotteryB.id).filter { it.status == EntryStatus.WON }.map { it.userId }
        val winnersC = lotteryRepository.getEntries(lotteryC.id).filter { it.status == EntryStatus.WON }.map { it.userId }

        assertEquals(winnersA, winnersB) // same seed -> identical winners
        assertNotEquals(winnersA, winnersC) // different seed -> different winners
    }

    @Test
    fun draw_concurrentAttempts_onlyOneDrawCommitted() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        for (i in 1..6) {
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-$i", status = EntryStatus.WAITING))
        }

        val drawers = (1..5).map {
            LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, Random(it.toLong()))
        }

        // Launch concurrent draws
        val results = drawers.map { d ->
            async { d.drawLottery(lottery.id, now) }
        }.awaitAll()

        // All should observe success and identical winner count
        assertTrue(results.all { it is LotteryDrawResult.Success })

        val finalLottery = lotteryRepository.getLottery(lottery.id)!!
        val entries = lotteryRepository.getEntries(lottery.id)
        val wonEntries = entries.filter { it.status == EntryStatus.WON }
        assertEquals(2, wonEntries.size)

        // Manifest chunks in repository must match the single committed drawId
        val committedDrawId = finalLottery.committedDrawId!!
        val chunks = lotteryRepository.loadManifestChunks(lottery.id, committedDrawId)
        val manifestWinners = chunks.flatMap { it.winnerUserIds }
        assertEquals(2, manifestWinners.size)
        assertEquals(manifestWinners.toSet(), wonEntries.map { it.userId }.toSet())
    }

    @Test
    fun draw_versionMismatchDuringStaging_retriesAndCapturesFreshSnapshot() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u1", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u2", status = EntryStatus.WAITING))

        // Intercepting repository that simulates an entrant mutation on the first draw attempt
        var attempted = false
        val customRepo = object : com.example.vipers.domain.repository.LotteryRepository by lotteryRepository {
            override suspend fun runDrawTransaction(
                lotteryId: String,
                candidateDrawId: String,
                expectedVersion: Long,
                chunkCount: Int,
                participantCount: Int,
                winnerCount: Int,
                drawnAt: Long,
                now: Long,
            ): com.example.vipers.domain.repository.DrawTransactionResult {
                if (!attempted) {
                    attempted = true
                    // Simulate a concurrent join incrementing entriesVersion
                    val current = lotteryRepository.getLottery(lotteryId)!!
                    lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryId, userId = "u3", status = EntryStatus.WAITING))
                    return com.example.vipers.domain.repository.DrawTransactionResult.VersionMismatch
                }
                return lotteryRepository.runDrawTransaction(
                    lotteryId, candidateDrawId, expectedVersion, chunkCount, participantCount, winnerCount, drawnAt, now
                )
            }
        }

        val dynamicDrawer = LotteryDrawer(customRepo, notificationRepository, campsiteRepository, Random(42))
        val result = dynamicDrawer.drawLottery(lottery.id, now)
        assertTrue(result is LotteryDrawResult.Success)
        assertTrue(attempted)

        val updated = lotteryRepository.getEntries(lottery.id)
        assertEquals(3, updated.size)
        assertEquals(2, updated.count { it.status == EntryStatus.WON })
        assertEquals(1, updated.count { it.status == EntryStatus.LOST })
    }

    @Test
    fun recovery_afterPhase1Commit_finishesStatusUpdatesAndNotifications() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        for (i in 1..4) {
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-$i", status = EntryStatus.WAITING))
        }

        // Simulate crash right after Phase 1 commit:
        // lottery.drawnAt and committedDrawId are set, manifest chunks exist, but entries are still WAITING
        val drawId = "crash-draw-123"
        val chunk = DrawManifestChunk(
            drawId = drawId,
            lotteryId = lottery.id,
            chunkIndex = 0,
            winnerUserIds = listOf("user-1", "user-2"),
            loserUserIds = listOf("user-3", "user-4"),
        )
        lotteryRepository.stageManifestChunks(lottery.id, listOf(chunk))
        lotteryRepository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = lottery.entriesVersion,
            chunkCount = 1,
            participantCount = 4,
            winnerCount = 2,
            drawnAt = now,
            now = now,
        )

        // Verify entries are still WAITING before recovery
        val preRecoveryEntries = lotteryRepository.getEntries(lottery.id)
        assertTrue(preRecoveryEntries.all { it.status == EntryStatus.WAITING })

        // Execute draw / recovery
        val recoveryResult = drawer.drawLottery(lottery.id, now)
        assertTrue(recoveryResult is LotteryDrawResult.Success)

        // Verify entries are now updated according to the committed manifest
        val postRecovery = lotteryRepository.getEntries(lottery.id)
        assertEquals(EntryStatus.WON, postRecovery.first { it.userId == "user-1" }.status)
        assertEquals(EntryStatus.WON, postRecovery.first { it.userId == "user-2" }.status)
        assertEquals(EntryStatus.LOST, postRecovery.first { it.userId == "user-3" }.status)
        assertEquals(EntryStatus.LOST, postRecovery.first { it.userId == "user-4" }.status)

        // Verify notifications delivered
        val notifsUser1 = notificationRepository.getNotificationsFor("user-1")
        assertEquals(1, notifsUser1.size)
        assertEquals(NotificationType.WON, notifsUser1[0].type)
        val notifsUser3 = notificationRepository.getNotificationsFor("user-3")
        assertEquals(1, notifsUser3.size)
        assertEquals(NotificationType.NOT_SELECTED, notifsUser3[0].type)
    }

    @Test
    fun recovery_preservesProgressedStatuses_neverOverwritesReservedOrDeclined() = runTest {
        val lottery = createTestLottery(winnersCount = 2)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-2", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-3", status = EntryStatus.WAITING))

        // Initial draw completes
        drawer.drawLottery(lottery.id, now)

        val winners = lotteryRepository.getEntries(lottery.id).filter { it.status == EntryStatus.WON }
        val winnerUser = winners[0].userId

        // Winner progresses to RESERVED
        lotteryRepository.updateEntry(
            LotteryEntry(lotteryId = lottery.id, userId = winnerUser, status = EntryStatus.RESERVED)
        )

        // Loser was REMOVED by admin
        val loserUser = lotteryRepository.getEntries(lottery.id).first { it.status == EntryStatus.LOST }.userId
        lotteryRepository.updateEntry(
            LotteryEntry(lotteryId = lottery.id, userId = loserUser, status = EntryStatus.REMOVED)
        )

        // Re-run draw (simulating recovery or subsequent worker check)
        drawer.drawLottery(lottery.id, now)

        val entriesAfter = lotteryRepository.getEntries(lottery.id)
        assertEquals(EntryStatus.RESERVED, entriesAfter.first { it.userId == winnerUser }.status)
        assertEquals(EntryStatus.REMOVED, entriesAfter.first { it.userId == loserUser }.status)
    }

    @Test
    fun notifications_deterministicIds_preventDuplicatesOnMultipleDrawRuns() = runTest {
        val lottery = createTestLottery(winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-2", status = EntryStatus.WAITING))

        drawer.drawLottery(lottery.id, now)
        drawer.drawLottery(lottery.id, now) // second call
        drawer.drawLottery(lottery.id, now) // third call

        val notifs1 = notificationRepository.getNotificationsFor("user-1")
        val notifs2 = notificationRepository.getNotificationsFor("user-2")
        assertEquals(1, notifs1.size)
        assertEquals(1, notifs2.size)
        assertEquals("draw_${lottery.id}_user-1", notifs1[0].id)
        assertEquals("draw_${lottery.id}_user-2", notifs2[0].id)
    }

    @Test
    fun notifications_multiCampsite_namesEveryCampsiteWithNullCampsiteId() = runTest {
        // Multi-campsite with nightsDescription: the win still names the campsites (US 01.03.01)
        val lotteryA = createTestLottery(
            campsiteIds = listOf("camp-1", "camp-2"),
            winnersCount = 1,
            nightsDesc = "July Long Weekend"
        )
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryA.id, userId = "user-1", status = EntryStatus.WAITING))
        drawer.drawLottery(lotteryA.id, now)

        val notifA = notificationRepository.getNotificationsFor("user-1")[0]
        assertNull(notifA.campsiteId) // null for multi-campsite
        assertTrue(notifA.message.contains("Pine Ridge, Maple Grove"))

        // Multi-campsite without nightsDescription -> uses concatenated names
        val lotteryB = createTestLottery(
            campsiteIds = listOf("camp-1", "camp-2"),
            winnersCount = 1,
            nightsDesc = null
        )
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lotteryB.id, userId = "user-2", status = EntryStatus.WAITING))
        drawer.drawLottery(lotteryB.id, now)

        val notifB = notificationRepository.getNotificationsFor("user-2")[0]
        assertNull(notifB.campsiteId)
        assertTrue(notifB.message.contains("Pine Ridge, Maple Grove"))
    }

    @Test
    fun notifications_singleCampsite_includesCampsiteIdAndName() = runTest {
        val lottery = createTestLottery(campsiteIds = listOf("camp-1"), winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING))
        drawer.drawLottery(lottery.id, now)

        val notif = notificationRepository.getNotificationsFor("user-1")[0]
        assertEquals("camp-1", notif.campsiteId)
        assertTrue(notif.message.contains("Pine Ridge"))
    }

    // US 01.03.02: the losers' "Not selected" notices, drawn with Random(42) from setUp

    private val fiveEntrants = (1..5).map { "user-$it" }

    /** A one-campsite lottery picking 2 winners, with [fiveEntrants] on its waiting list. */
    private suspend fun lotteryWithFiveEntrants(): Lottery {
        val lottery = createTestLottery(campsiteIds = listOf("camp-1"), winnersCount = 2)
        for (userId in fiveEntrants) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lottery.id, userId = userId, status = EntryStatus.WAITING),
            )
        }
        return lottery
    }

    private suspend fun noticesOf(type: NotificationType): List<Notification> =
        fiveEntrants.flatMap { notificationRepository.getNotificationsFor(it) }.filter { it.type == type }

    @Test
    fun drawLottery_fiveEntrantsTwoWinners_sendsThreeNotSelectedOnePerLoser() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now)

        val losers = lotteryRepository.getEntries(lottery.id)
            .filter { it.status == EntryStatus.LOST }.map { it.userId }
        val notSelected = noticesOf(NotificationType.NOT_SELECTED)
        assertEquals(3, notSelected.size)
        assertEquals(losers.toSet(), notSelected.map { it.recipientId }.toSet())
    }

    @Test
    fun drawLottery_anyDraw_noUserGetsBothWonAndNotSelected() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now)

        val wonIds = noticesOf(NotificationType.WON).map { it.recipientId }.toSet()
        val lostIds = noticesOf(NotificationType.NOT_SELECTED).map { it.recipientId }.toSet()
        assertEquals(2, wonIds.size)
        assertTrue(wonIds.intersect(lostIds).isEmpty())
    }

    @Test
    fun drawLottery_runTwice_everyEntrantKeepsExactlyOneNotice() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now)
        val first = fiveEntrants.associateWith { notificationRepository.getNotificationsFor(it) }
        for (userId in fiveEntrants) {
            assertEquals(userId, 1, first.getValue(userId).size)
        }

        drawer.drawLottery(lottery.id, now)

        for (userId in fiveEntrants) {
            val notices = notificationRepository.getNotificationsFor(userId)
            assertEquals(userId, listOf("draw_${lottery.id}_$userId"), notices.map { it.id })
            assertEquals(userId, first.getValue(userId).single().type, notices.single().type)
        }
    }

    @Test
    fun drawLottery_oneCampsite_notSelectedNamesItAndOpensIt() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now)

        for (notice in noticesOf(NotificationType.NOT_SELECTED)) {
            assertTrue(notice.message, notice.message.contains("Pine Ridge"))
            assertEquals("camp-1", notice.campsiteId)
            assertEquals(NotificationTarget.OpenCampsite("camp-1"), notice.target)
        }
    }

    @Test
    fun drawLottery_emptyWaitingList_sendsNothing() = runBlocking {
        var saved = 0
        val counting = object : NotificationRepository by notificationRepository {
            override suspend fun addAll(notifications: List<Notification>) {
                saved += notifications.size
            }

            override suspend fun saveNotification(notification: Notification): String {
                saved++
                return notification.id
            }

            override suspend fun addNotification(notification: Notification): String {
                saved++
                return notification.id
            }
        }
        val countingDrawer = LotteryDrawer(lotteryRepository, counting, campsiteRepository, Random(42))
        val lottery = createTestLottery(winnersCount = 2)

        assertEquals(LotteryDrawResult.EmptyList, countingDrawer.drawLottery(lottery.id, now))
        assertEquals(0, saved)
    }
}
