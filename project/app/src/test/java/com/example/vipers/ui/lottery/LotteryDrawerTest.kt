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
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.DrawManifestChunk
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.DeclineTransactionResult
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReserveResult
import com.example.vipers.domain.service.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val profileRepository = FakeProfileRepository()
    private lateinit var drawer: LotteryDrawer

    private val now = 2_000_000_000L
    private val regStart = 1_000_000_000L
    private val regEnd = 1_500_000_000L // registration closed at 'now'
    private val bookStart = 1_600_000_000L
    private val bookEnd = 2_500_000_000L

    @Before
    fun setUp() = runTest {
        notificationRepository = FakeNotificationRepository()
        lotteryRepository = FakeLotteryRepository(notificationRepository = notificationRepository)
        campsiteRepository = FakeCampsiteRepository()
        campsiteRepository.addCampsite(Campsite(id = "camp-1", name = "Pine Ridge"))
        campsiteRepository.addCampsite(Campsite(id = "camp-2", name = "Maple Grove"))

        drawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
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

        val drawer1 = LotteryDrawer(
            lotteryRepository, notificationRepository, campsiteRepository, profileRepository,
            Random(12345),
        )
        val drawer2 = LotteryDrawer(
            lotteryRepository, notificationRepository, campsiteRepository, profileRepository,
            Random(12345),
        )
        val drawer3 = LotteryDrawer(
            lotteryRepository, notificationRepository, campsiteRepository, profileRepository,
            Random(99999),
        )

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
            LotteryDrawer(
                lotteryRepository, notificationRepository, campsiteRepository, profileRepository,
                Random(it.toLong()),
            )
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

        val dynamicDrawer = LotteryDrawer(
            customRepo, notificationRepository, campsiteRepository, profileRepository, Random(42),
        )
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
            assertEquals("Not selected this time", notice.title)
            assertEquals("You weren't selected for Pine Ridge this time.", notice.message)
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
        val countingDrawer = LotteryDrawer(
            lotteryRepository, counting, campsiteRepository, profileRepository, Random(42),
        )
        val lottery = createTestLottery(winnersCount = 2)

        assertEquals(LotteryDrawResult.EmptyList, countingDrawer.drawLottery(lottery.id, now))
        assertEquals(0, saved)
    }

    @Test
    fun drawLottery_multiCampsiteWithDescription_notSelectedNamesEveryCampsite() = runBlocking {
        val lottery = createTestLottery(
            campsiteIds = listOf("camp-1", "camp-2"),
            winnersCount = 1,
            nightsDesc = "July Long Weekend",
        )
        for (userId in listOf("user-1", "user-2")) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lottery.id, userId = userId, status = EntryStatus.WAITING),
            )
        }
        drawer.drawLottery(lottery.id, now)

        val notice = noticesOf(NotificationType.NOT_SELECTED).single()
        assertEquals("You weren't selected for Pine Ridge, Maple Grove this time.", notice.message)
        assertNull(notice.campsiteId)
    }

    @Test
    fun drawLottery_runAgainAfterALoserReadIt_keepsItRead() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now)
        val read = noticesOf(NotificationType.NOT_SELECTED).first()
        notificationRepository.markRead(read.id)

        drawer.drawLottery(lottery.id, now)

        assertTrue(notificationRepository.getNotificationsFor(read.recipientId).single().read)
        // the other losers never opened theirs
        assertFalse(noticesOf(NotificationType.NOT_SELECTED).filter { it.id != read.id }.any { it.read })
    }

    // US 01.04.03 / T-12: drawing a replacement winner when a winner declines

    @Test
    fun declineAndDrawReplacement_selectsDeterministicallyFromSameLotteryLostEntrantsOnly() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        val otherLottery = createTestLottery(winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "waiting-1", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "won-2", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "declined-1", status = EntryStatus.DECLINED))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "reserved-1", status = EntryStatus.RESERVED))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "removed-1", status = EntryStatus.REMOVED))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = otherLottery.id, userId = "other-lost", status = EntryStatus.LOST))
        val lostInLottery = listOf("lost-a", "lost-b", "lost-c")
        for (userId in lostInLottery) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lottery.id, userId = userId, status = EntryStatus.LOST),
            )
        }

        val expectedWinner = lostInLottery.shuffled(Random(99)).first()
        val fixedDrawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
            random = Random(99),
        )

        val result = fixedDrawer.declineAndDrawReplacement(lottery.id, "winner-1", now)
        assertEquals(ReplacementDrawResult.Declined(expectedWinner), result)

        val entries = lotteryRepository.getEntries(lottery.id).associateBy { it.userId }
        assertEquals(EntryStatus.DECLINED, entries.getValue("winner-1").status)
        assertEquals(EntryStatus.WON, entries.getValue(expectedWinner).status)
        assertEquals(EntryStatus.WAITING, entries.getValue("waiting-1").status)
        assertEquals(EntryStatus.WON, entries.getValue("won-2").status)
        assertEquals(EntryStatus.DECLINED, entries.getValue("declined-1").status)
        assertEquals(EntryStatus.RESERVED, entries.getValue("reserved-1").status)
        assertEquals(EntryStatus.REMOVED, entries.getValue("removed-1").status)
        assertEquals(EntryStatus.LOST, lotteryRepository.getEntry(otherLottery.id, "other-lost")?.status)
        for (otherLost in lostInLottery - expectedWinner) {
            assertEquals(EntryStatus.LOST, entries.getValue(otherLost).status)
        }
    }

    @Test
    fun declineAndDrawReplacement_promotedWinnerCanReserveNights() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(campsiteId = "camp-1", openNights = listOf("2027-06-01", "2027-06-02")),
        )
        val reservations = FakeReservationRepository(campsiteRepository, lotteryRepository, notificationRepository)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "loser-1", groupSize = 1, status = EntryStatus.LOST),
        )

        val result = drawer.declineAndDrawReplacement(lottery.id, "winner-1", now)
        assertEquals(ReplacementDrawResult.Declined("loser-1"), result)

        val promoted = lotteryRepository.getEntry(lottery.id, "loser-1")!!
        assertTrue(promoted.canReserve(lottery, now))
        val reserveResult = reservations.createReservation(
            lotteryId = lottery.id,
            campsiteId = "camp-1",
            userId = "loser-1",
            nights = listOf("2027-06-01", "2027-06-02"),
            groupSize = 1,
            now = now + 100L,
        )
        assertTrue("Expected Success but got $reserveResult", reserveResult is ReserveResult.Success)
        assertEquals(EntryStatus.RESERVED, lotteryRepository.getEntry(lottery.id, "loser-1")?.status)
    }

    @Test
    fun declineAndDrawReplacement_updatesOpenInboxWithReplacementWonNoticeAndKeepsOriginalRecord() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WAITING))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.WAITING))
        val fixedNotificationService = NotificationService(
            notificationRepository,
            campsiteRepository,
            profileRepository,
            lotteryRepository,
            formatDate = { "time $it" },
        )
        val fixedDrawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
            random = Random(42),
            notificationService = fixedNotificationService,
        )

        fixedDrawer.drawLottery(lottery.id, now = regEnd + 1_000L)
        val originalWinner = lotteryRepository.getEntries(lottery.id).first { it.status == EntryStatus.WON }.userId
        val originalLoser = lotteryRepository.getEntries(lottery.id).first { it.status == EntryStatus.LOST }.userId

        val inboxUpdates = mutableListOf<List<Notification>>()
        val sub = fixedNotificationService.listenToInbox(
            originalLoser,
            object : InboxListener {
                override fun onInboxChanged(items: List<Notification>) {
                    inboxUpdates.add(items)
                }

                override fun onInboxError() = Unit
            },
        )

        val promotionTime = bookStart + 50_000L
        fixedDrawer.declineAndDrawReplacement(lottery.id, originalWinner, promotionTime)
        sub.cancel()

        val latest = inboxUpdates.last()
        assertEquals(2, latest.size)
        val replacement = latest[0]
        val original = latest[1]
        assertEquals("replacement_${lottery.id}_$originalLoser", replacement.id)
        assertEquals(NotificationType.WON, replacement.type)
        assertEquals("You won the lottery!", replacement.title)
        assertEquals(
            "You won a spot at Pine Ridge. Reserve your nights before time $bookEnd.",
            replacement.message,
        )
        assertEquals(promotionTime, replacement.createdAt)
        assertFalse(replacement.read)
        assertFalse(replacement.shownOnDevice)
        assertEquals(NotificationTarget.OpenReserve(lottery.id), replacement.target)

        assertEquals("draw_${lottery.id}_$originalLoser", original.id)
        assertEquals(NotificationType.NOT_SELECTED, original.type)
    }

    @Test
    fun declineAndDrawReplacement_optedOutCandidate_promotesWithoutSavingNotice() = runBlocking {
        profileRepository.saveProfile(UserProfile(id = "loser-1", name = "Riley", notificationsEnabled = false))
        val lottery = createTestLottery(winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.LOST))

        val result = drawer.declineAndDrawReplacement(lottery.id, "winner-1", now)

        assertEquals(ReplacementDrawResult.Declined("loser-1"), result)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.WON, lotteryRepository.getEntry(lottery.id, "loser-1")?.status)
        assertTrue(notificationRepository.getNotificationsFor("loser-1").isEmpty())

        // Enabling notifications afterwards does not retroactively emit a notice or permit a second decline.
        profileRepository.saveProfile(UserProfile(id = "loser-1", name = "Riley", notificationsEnabled = true))
        assertEquals(ReplacementDrawResult.NotDeclined, drawer.declineAndDrawReplacement(lottery.id, "winner-1", now + 1))
        assertTrue(notificationRepository.getNotificationsFor("loser-1").isEmpty())
    }

    @Test
    fun declineAndDrawReplacement_staleFirstCandidate_skipsNoticeForStaleAndPreparesOnlyAttemptedCandidates() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        val candidates = listOf("cand-a", "cand-b", "cand-c")
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        for (id in candidates) {
            lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = id, status = EntryStatus.LOST))
        }
        val expectedOrder = candidates.shuffled(Random(99))
        val staleFirst = expectedOrder[0]
        val promotedSecond = expectedOrder[1]
        val untouchedThird = expectedOrder[2]

        val queriedProfiles = mutableListOf<String>()
        val trackingProfiles = object : ProfileRepository by profileRepository {
            override suspend fun getProfile(id: String): UserProfile? {
                queriedProfiles.add(id)
                return profileRepository.getProfile(id)
            }
        }
        var racedOnce = false
        val racingLotteries = object : LotteryRepository by lotteryRepository {
            override suspend fun declineTransaction(
                lotteryId: String,
                userId: String,
                candidateUserId: String?,
                replacementNotice: Notification?,
                now: Long,
            ): DeclineTransactionResult {
                if (!racedOnce && candidateUserId == staleFirst) {
                    racedOnce = true
                    lotteryRepository.updateEntry(
                        LotteryEntry(lotteryId = lotteryId, userId = staleFirst, status = EntryStatus.WON),
                    )
                }
                return lotteryRepository.declineTransaction(
                    lotteryId,
                    userId,
                    candidateUserId,
                    replacementNotice,
                    now,
                )
            }
        }
        val fixedDrawer = LotteryDrawer(
            lotteryRepository = racingLotteries,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            profileRepository = trackingProfiles,
            random = Random(99),
        )

        val result = fixedDrawer.declineAndDrawReplacement(lottery.id, "winner-1", now)

        assertEquals(ReplacementDrawResult.Declined(promotedSecond), result)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.WON, lotteryRepository.getEntry(lottery.id, promotedSecond)?.status)
        assertEquals(EntryStatus.LOST, lotteryRepository.getEntry(lottery.id, untouchedThird)?.status)
        assertTrue(notificationRepository.getNotificationsFor(staleFirst).isEmpty())
        assertEquals(1, notificationRepository.getNotificationsFor(promotedSecond).size)
        assertEquals(
            "replacement_${lottery.id}_$promotedSecond",
            notificationRepository.getNotificationsFor(promotedSecond).single().id,
        )
        assertTrue(notificationRepository.getNotificationsFor(untouchedThird).isEmpty())
        assertEquals(listOf(staleFirst, promotedSecond), queriedProfiles)
    }

    @Test
    fun declineAndDrawReplacement_allCandidatesStaleBeforeTx_commitsDeclineAloneWithNoNotices() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "cand-1", status = EntryStatus.LOST))

        val racingLotteries = object : LotteryRepository by lotteryRepository {
            override suspend fun declineTransaction(
                lotteryId: String,
                userId: String,
                candidateUserId: String?,
                replacementNotice: Notification?,
                now: Long,
            ): DeclineTransactionResult {
                if (candidateUserId == "cand-1") {
                    lotteryRepository.updateEntry(
                        LotteryEntry(lotteryId = lotteryId, userId = "cand-1", status = EntryStatus.RESERVED),
                    )
                }
                return lotteryRepository.declineTransaction(
                    lotteryId,
                    userId,
                    candidateUserId,
                    replacementNotice,
                    now,
                )
            }
        }
        val fixedDrawer = LotteryDrawer(
            lotteryRepository = racingLotteries,
            notificationRepository = notificationRepository,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
            random = Random(42),
        )

        val result = fixedDrawer.declineAndDrawReplacement(lottery.id, "winner-1", now)

        assertEquals(ReplacementDrawResult.Declined(null), result)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.RESERVED, lotteryRepository.getEntry(lottery.id, "cand-1")?.status)
        assertTrue(notificationRepository.getNotificationsFor("cand-1").isEmpty())
    }

    @Test
    fun declineAndDrawReplacement_whenNoticePersistenceFails_leavesWinIntactAndRetrySucceedsOnce() = runBlocking {
        val backingNotifications = FakeNotificationRepository()
        var failSend = true
        val flakyNotifications = object : NotificationRepository by backingNotifications {
            override suspend fun addAll(notifications: List<Notification>) {
                if (failSend) throw IllegalStateException("offline")
                backingNotifications.addAll(notifications)
            }
        }
        val atomicLotteries = FakeLotteryRepository(notificationRepository = flakyNotifications)
        val lottery = atomicLotteries.getLottery(
            atomicLotteries.addLottery(
                Lottery(
                    ownerId = "owner-1",
                    campsiteIds = listOf("camp-1"),
                    nightsOffered = listOf("2027-06-01", "2027-06-02"),
                    registrationStart = regStart,
                    registrationEnd = regEnd,
                    bookingStart = bookStart,
                    bookingEnd = bookEnd,
                    winnersCount = 1,
                ),
            ),
        )!!
        atomicLotteries.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        atomicLotteries.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.LOST))
        atomicLotteries.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-2", status = EntryStatus.LOST))

        val firstDrawer = LotteryDrawer(
            lotteryRepository = atomicLotteries,
            notificationRepository = flakyNotifications,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
            random = Random(42),
        )

        val promotionTime = bookStart + 10_000L
        val failure = runCatching {
            firstDrawer.declineAndDrawReplacement(lottery.id, "winner-1", promotionTime)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)

        // Failed notice persistence rolls back cleanly: original win stays WON and candidates stay LOST.
        assertEquals(EntryStatus.WON, atomicLotteries.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.LOST, atomicLotteries.getEntry(lottery.id, "loser-1")?.status)
        assertEquals(EntryStatus.LOST, atomicLotteries.getEntry(lottery.id, "loser-2")?.status)
        assertTrue(backingNotifications.getNotificationsFor("loser-1").isEmpty())
        assertTrue(backingNotifications.getNotificationsFor("loser-2").isEmpty())

        // Retry succeeds atomically once; a subsequent attempt does not decline or award a second spot.
        failSend = false
        val retryDrawer = LotteryDrawer(
            lotteryRepository = atomicLotteries,
            notificationRepository = flakyNotifications,
            campsiteRepository = campsiteRepository,
            profileRepository = profileRepository,
            random = Random(123),
        )
        val retryResult = retryDrawer.declineAndDrawReplacement(lottery.id, "winner-1", promotionTime)
        val promotedId = (retryResult as ReplacementDrawResult.Declined).promotedUserId!!
        assertEquals(ReplacementDrawResult.NotDeclined, retryDrawer.declineAndDrawReplacement(lottery.id, "winner-1", promotionTime + 1))

        assertEquals(EntryStatus.DECLINED, atomicLotteries.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.WON, atomicLotteries.getEntry(lottery.id, promotedId)?.status)
        val notices = backingNotifications.getNotificationsFor(promotedId)
        assertEquals(1, notices.size)
        assertEquals("replacement_${lottery.id}_$promotedId", notices[0].id)
        assertEquals(promotionTime, notices[0].createdAt)
        val otherLost = if (promotedId == "loser-1") "loser-2" else "loser-1"
        assertEquals(EntryStatus.LOST, atomicLotteries.getEntry(lottery.id, otherLost)?.status)
        assertTrue(backingNotifications.getNotificationsFor(otherLost).isEmpty())
    }

    @Test
    fun declineAndDrawReplacement_competingDeclinesWithTwoCandidates_promoteDistinctAndNotifyOnceEach() = runBlocking {
        val lottery = createTestLottery(winnersCount = 2)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-2", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.LOST))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-2", status = EntryStatus.LOST))

        val drawerA = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, profileRepository, Random(42))
        val drawerB = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, profileRepository, Random(42))

        val results = listOf(
            async(Dispatchers.Default) { drawerA.declineAndDrawReplacement(lottery.id, "winner-1", now) },
            async(Dispatchers.Default) { drawerB.declineAndDrawReplacement(lottery.id, "winner-2", now + 1) },
        ).awaitAll()

        val promoted = results.map { (it as ReplacementDrawResult.Declined).promotedUserId }.toSet()
        assertEquals(setOf("loser-1", "loser-2"), promoted)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-2")?.status)
        assertEquals(1, notificationRepository.getNotificationsFor("loser-1").size)
        assertEquals(1, notificationRepository.getNotificationsFor("loser-2").size)
    }

    @Test
    fun declineAndDrawReplacement_competingDeclinesWithSingleCandidate_onePromotesOtherDeclinesAlone() = runBlocking {
        val lottery = createTestLottery(winnersCount = 2)
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-2", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.LOST))

        val drawerA = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, profileRepository, Random(42))
        val drawerB = LotteryDrawer(lotteryRepository, notificationRepository, campsiteRepository, profileRepository, Random(99))

        val results = listOf(
            async(Dispatchers.Default) { drawerA.declineAndDrawReplacement(lottery.id, "winner-1", now) },
            async(Dispatchers.Default) { drawerB.declineAndDrawReplacement(lottery.id, "winner-2", now + 1) },
        ).awaitAll()

        assertEquals(
            setOf(ReplacementDrawResult.Declined("loser-1"), ReplacementDrawResult.Declined(null)),
            results.toSet(),
        )
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-1")?.status)
        assertEquals(EntryStatus.DECLINED, lotteryRepository.getEntry(lottery.id, "winner-2")?.status)
        assertEquals(EntryStatus.WON, lotteryRepository.getEntry(lottery.id, "loser-1")?.status)
        assertEquals(1, notificationRepository.getNotificationsFor("loser-1").size)
    }

    @Test
    fun declineAndDrawReplacement_existingDeterministicNoticeAlreadyReadAndShown_preservesFlags() = runBlocking {
        val lottery = createTestLottery(winnersCount = 1)
        val noticeId = "replacement_${lottery.id}_loser-1"
        notificationRepository.saveNotification(
            Notification(
                id = noticeId,
                recipientId = "loser-1",
                campsiteId = "camp-1",
                lotteryId = lottery.id,
                type = NotificationType.WON,
                title = "You won the lottery!",
                message = "Earlier",
                read = true,
                shownOnDevice = true,
                createdAt = 1_000L,
            ),
        )
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WON))
        lotteryRepository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.LOST))

        val promotionTime = bookStart + 25_000L
        val res = drawer.declineAndDrawReplacement(lottery.id, "winner-1", promotionTime)
        assertEquals(ReplacementDrawResult.Declined("loser-1"), res)

        val notices = notificationRepository.getNotificationsFor("loser-1")
        assertEquals(1, notices.size)
        assertEquals(noticeId, notices[0].id)
        assertEquals(promotionTime, notices[0].createdAt)
        assertTrue(notices[0].read)
        assertTrue(notices[0].shownOnDevice)
    }

    @Test
    fun drawLottery_replayedAfterReplacement_preservesWonStatusAndReplacementNotice() = runBlocking {
        val lottery = lotteryWithFiveEntrants()
        drawer.drawLottery(lottery.id, now = regEnd + 1_000L)

        val originalWinner = lotteryRepository.getEntries(lottery.id).first { it.status == EntryStatus.WON }.userId
        val promotionTime = bookStart + 1_000L
        val declineResult = drawer.declineAndDrawReplacement(lottery.id, originalWinner, promotionTime)
        val promotedId = (declineResult as ReplacementDrawResult.Declined).promotedUserId!!

        // Replay the original draw recovery path after replacement promotion
        drawer.drawLottery(lottery.id, now = bookStart + 2_000L)

        val entries = lotteryRepository.getEntries(lottery.id).associateBy { it.userId }
        assertEquals(EntryStatus.DECLINED, entries.getValue(originalWinner).status)
        assertEquals(EntryStatus.WON, entries.getValue(promotedId).status)

        val notices = notificationRepository.getNotificationsFor(promotedId)
        val replacementNotices = notices.filter { it.id == "replacement_${lottery.id}_$promotedId" }
        val originalNotices = notices.filter { it.id == "draw_${lottery.id}_$promotedId" }
        assertEquals(1, replacementNotices.size)
        assertEquals(NotificationType.WON, replacementNotices.single().type)
        assertEquals(promotionTime, replacementNotices.single().createdAt)
        assertEquals(1, originalNotices.size)
        assertEquals(NotificationType.NOT_SELECTED, originalNotices.single().type)
    }
}
