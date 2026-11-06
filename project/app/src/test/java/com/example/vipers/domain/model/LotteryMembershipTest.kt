/*
 * LotteryMembershipTest.kt
 * Unit tests verifying waiting-list membership constraints, versioning,
 * registration boundary enforcement, and post-draw rejections.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import com.example.vipers.data.fake.FakeLotteryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LotteryMembershipTest {

    private lateinit var repository: FakeLotteryRepository
    private val now = 2_000_000_000L
    private val regStart = 1_000_000_000L
    private val regEnd = 3_000_000_000L // open at 'now'
    private val bookStart = 3_500_000_000L
    private val bookEnd = 4_000_000_000L

    @Before
    fun setUp() {
        repository = FakeLotteryRepository()
    }

    private suspend fun createOpenLottery(): Lottery {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = regStart,
            registrationEnd = regEnd,
            bookingStart = bookStart,
            bookingEnd = bookEnd,
            winnersCount = 2,
        )
        val id = repository.addLottery(lottery)
        return repository.getLottery(id)!!
    }

    @Test
    fun joinWaitingList_incrementsEntriesVersionAtomically() = runTest {
        val lottery = createOpenLottery()
        assertEquals(0L, lottery.entriesVersion)

        repository.joinWaitingList(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
            now = now
        )

        val updated = repository.getLottery(lottery.id)!!
        assertEquals(1L, updated.entriesVersion)

        val entry = repository.getEntry(lottery.id, "user-1")
        assertEquals(EntryStatus.WAITING, entry?.status)
    }

    @Test
    fun joinWaitingList_duplicateJoin_rejected() = runTest {
        val lottery = createOpenLottery()
        repository.joinWaitingList(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
            now = now
        )

        try {
            repository.joinWaitingList(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
                now = now
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }

        // Version should not increment on failed duplicate
        val updated = repository.getLottery(lottery.id)!!
        assertEquals(1L, updated.entriesVersion)
    }

    @Test
    fun joinWaitingList_afterRegistrationClosed_rejected() = runTest {
        val lottery = createOpenLottery()
        val afterClose = regEnd + 1000

        try {
            repository.joinWaitingList(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
                now = afterClose
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }

        val entries = repository.getEntries(lottery.id)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun joinWaitingList_afterLotteryDrawn_rejected() = runTest {
        val lottery = createOpenLottery()
        // Draw the lottery
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = "draw-1",
            expectedVersion = 0L,
            chunkCount = 0,
            participantCount = 0,
            winnerCount = 0,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.joinWaitingList(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
                now = now
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun leaveWaitingList_removesEntryAndIncrementsVersion() = runTest {
        val lottery = createOpenLottery()
        repository.joinWaitingList(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
            now = now
        )
        assertEquals(1L, repository.getLottery(lottery.id)!!.entriesVersion)

        repository.leaveWaitingList(lottery.id, "user-1")

        val updated = repository.getLottery(lottery.id)!!
        assertEquals(2L, updated.entriesVersion)
        assertNull(repository.getEntry(lottery.id, "user-1"))
    }

    @Test
    fun leaveWaitingList_afterLotteryDrawn_rejected() = runTest {
        val lottery = createOpenLottery()
        repository.joinWaitingList(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1"),
            now = now
        )

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = "draw-1",
            expectedVersion = 1L,
            chunkCount = 0,
            participantCount = 1,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.leaveWaitingList(lottery.id, "user-1")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun updateEntry_rejectsAttemptToSetStatusBackToWaiting() = runTest {
        val lottery = createOpenLottery()
        repository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WON)
        )

        try {
            repository.updateEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING)
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun updateEntry_transitionFromWaitingToRemoved_rejected() = runTest {
        val lottery = createOpenLottery()
        repository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING)
        )

        try {
            repository.updateEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.REMOVED)
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }

        val lotteryAfter = repository.getLottery(lottery.id)!!
        assertEquals(0L, lotteryAfter.entriesVersion)
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "user-1")!!.status)
    }

    @Test
    fun updateEntry_transitionFromWaitingToLost_rejected() = runTest {
        val lottery = createOpenLottery()
        repository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING)
        )

        try {
            repository.updateEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.LOST)
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }

        val lotteryAfter = repository.getLottery(lottery.id)!!
        assertEquals(0L, lotteryAfter.entriesVersion)
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "user-1")!!.status)
    }

    @Test
    fun updateEntry_transitionFromWaitingToWon_rejected() = runTest {
        val lottery = createOpenLottery()
        repository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WAITING)
        )

        try {
            repository.updateEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WON)
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }

        val lotteryAfter = repository.getLottery(lottery.id)!!
        assertEquals(0L, lotteryAfter.entriesVersion)
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "user-1")!!.status)
    }

    @Test
    fun updateEntry_validPostDrawLifecycleTransitions_succeedsWithoutVersionIncrement() = runTest {
        val lottery = createOpenLottery()
        repository.seedEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.WON)
        )
        assertEquals(0L, repository.getLottery(lottery.id)!!.entriesVersion)

        // WON -> RESERVED
        repository.updateEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.RESERVED)
        )
        assertEquals(EntryStatus.RESERVED, repository.getEntry(lottery.id, "user-1")!!.status)
        assertEquals(0L, repository.getLottery(lottery.id)!!.entriesVersion)

        // RESERVED -> DECLINED
        repository.updateEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.DECLINED)
        )
        assertEquals(EntryStatus.DECLINED, repository.getEntry(lottery.id, "user-1")!!.status)
        assertEquals(0L, repository.getLottery(lottery.id)!!.entriesVersion)

        // DECLINED -> EXPIRED
        repository.updateEntry(
            LotteryEntry(lotteryId = lottery.id, userId = "user-1", status = EntryStatus.EXPIRED)
        )
        assertEquals(EntryStatus.EXPIRED, repository.getEntry(lottery.id, "user-1")!!.status)
        assertEquals(0L, repository.getLottery(lottery.id)!!.entriesVersion)
    }

    @Test
    fun updateEntry_nonExistentEntry_rejected() = runTest {
        val lottery = createOpenLottery()
        try {
            repository.updateEntry(
                LotteryEntry(lotteryId = lottery.id, userId = "non-existent", status = EntryStatus.WON)
            )
            fail("Expected NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_rejectsUnknownLottery() = runTest {
        try {
            repository.reconcileEntryStatuses("non-existent", "draw-1")
            fail("Expected NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_rejectsLotteryNotDrawn() = runTest {
        val lottery = createOpenLottery()
        try {
            repository.reconcileEntryStatuses(lottery.id, "draw-1")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_rejectsMismatchedDrawId() = runTest {
        val lottery = createOpenLottery()
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = "draw-1",
            expectedVersion = 0L,
            chunkCount = 0,
            participantCount = 0,
            winnerCount = 0,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, "draw-different")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_ignoresUncommittedOrphanedManifestChunks() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-committed"
        val orphanedDrawId = "draw-orphaned"

        // Stage committed chunk and orphaned chunk
        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(
                    drawId = drawId,
                    lotteryId = lottery.id,
                    chunkIndex = 0,
                    winnerUserIds = listOf("u1"),
                    loserUserIds = listOf("u2"),
                )
            )
        )
        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(
                    drawId = orphanedDrawId,
                    lotteryId = lottery.id,
                    chunkIndex = 0,
                    winnerUserIds = listOf("u3"),
                    loserUserIds = emptyList(),
                )
            )
        )

        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u1", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u2", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u3", status = EntryStatus.WAITING))

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 1,
            participantCount = 2,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        repository.reconcileEntryStatuses(lottery.id, drawId)

        assertEquals(EntryStatus.WON, repository.getEntry(lottery.id, "u1")!!.status)
        assertEquals(EntryStatus.LOST, repository.getEntry(lottery.id, "u2")!!.status)
        // Orphaned u3 from uncommitted chunk remains untouched
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "u3")!!.status)
    }

    @Test
    fun reconcileEntryStatuses_rejectsIncompleteManifestChunkCount() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-1"
        // Lottery committed with chunkCount = 2, but only 1 chunk staged
        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(drawId = drawId, lotteryId = lottery.id, chunkIndex = 0, winnerUserIds = listOf("u1"))
            )
        )
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 2,
            participantCount = 2,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_rejectsNonDisjointWinnerAndLoserSets() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-1"
        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(
                    drawId = drawId,
                    lotteryId = lottery.id,
                    chunkIndex = 0,
                    winnerUserIds = listOf("u1"),
                    loserUserIds = listOf("u1"), // overlapping
                )
            )
        )
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 1,
            participantCount = 1,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_participantRestrictionsAndPreservation() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-1"

        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(
                    drawId = drawId,
                    lotteryId = lottery.id,
                    chunkIndex = 0,
                    winnerUserIds = listOf("winner-1"),
                    loserUserIds = listOf("loser-1"),
                )
            )
        )
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 1,
            participantCount = 2,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        // Seed participants and non-participants with various statuses
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "winner-1", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "loser-1", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "outsider", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "reserved", status = EntryStatus.RESERVED))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "declined", status = EntryStatus.DECLINED))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "expired", status = EntryStatus.EXPIRED))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "removed", status = EntryStatus.REMOVED))

        // Initial reconciliation
        repository.reconcileEntryStatuses(lottery.id, drawId)

        assertEquals(EntryStatus.WON, repository.getEntry(lottery.id, "winner-1")!!.status)
        assertEquals(EntryStatus.LOST, repository.getEntry(lottery.id, "loser-1")!!.status)
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "outsider")!!.status)
        assertEquals(EntryStatus.RESERVED, repository.getEntry(lottery.id, "reserved")!!.status)
        assertEquals(EntryStatus.DECLINED, repository.getEntry(lottery.id, "declined")!!.status)
        assertEquals(EntryStatus.EXPIRED, repository.getEntry(lottery.id, "expired")!!.status)
        assertEquals(EntryStatus.REMOVED, repository.getEntry(lottery.id, "removed")!!.status)

        // Repeated reconciliation is completely idempotent
        repository.reconcileEntryStatuses(lottery.id, drawId)

        assertEquals(EntryStatus.WON, repository.getEntry(lottery.id, "winner-1")!!.status)
        assertEquals(EntryStatus.LOST, repository.getEntry(lottery.id, "loser-1")!!.status)
        assertEquals(EntryStatus.WAITING, repository.getEntry(lottery.id, "outsider")!!.status)
        assertEquals(EntryStatus.RESERVED, repository.getEntry(lottery.id, "reserved")!!.status)
        assertEquals(EntryStatus.DECLINED, repository.getEntry(lottery.id, "declined")!!.status)
        assertEquals(EntryStatus.EXPIRED, repository.getEntry(lottery.id, "expired")!!.status)
        assertEquals(EntryStatus.REMOVED, repository.getEntry(lottery.id, "removed")!!.status)
    }

    @Test
    fun reconcileEntryStatuses_emptyWaitingList_succeedsAndRepeatedCallsIdempotent() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-empty"

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 0,
            participantCount = 0,
            winnerCount = 0,
            drawnAt = now,
            now = regEnd + 1000,
        )

        // Initial empty reconciliation succeeds
        repository.reconcileEntryStatuses(lottery.id, drawId)

        // Repeated reconciliation also succeeds
        repository.reconcileEntryStatuses(lottery.id, drawId)

        val updated = repository.getLottery(lottery.id)!!
        assertEquals(0, updated.participantCount)
        assertEquals(0, updated.winnerCount)
    }

    @Test
    fun reconcileEntryStatuses_emptyDraw_withNonZeroWinnerCount_rejected() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-invalid-empty"

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 0,
            participantCount = 0,
            winnerCount = 1, // Invalid!
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_emptyDraw_withManifestChunksInDatabase_rejected() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-invalid-chunks"

        // Staged chunk for a supposedly empty draw
        repository.stageManifestChunks(
            lottery.id,
            listOf(DrawManifestChunk(drawId = drawId, lotteryId = lottery.id, chunkIndex = 0, winnerUserIds = listOf("u1")))
        )

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 0,
            participantCount = 0,
            winnerCount = 0,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_nonEmptyDraw_withZeroManifestChunkCount_rejected() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-missing-chunk-meta"

        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 0, // Mismatched chunkCount for non-empty draw
            participantCount = 1,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_nonEmptyDraw_withMissingChunksInDatabase_rejected() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-no-chunks-in-db"

        // Commits with chunkCount = 1, but no chunks were staged
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 1,
            participantCount = 1,
            winnerCount = 1,
            drawnAt = now,
            now = regEnd + 1000,
        )

        try {
            repository.reconcileEntryStatuses(lottery.id, drawId)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun reconcileEntryStatuses_zeroWinnersWithParticipants_marksAllLost() = runTest {
        val lottery = createOpenLottery()
        val drawId = "draw-zero-winners"

        repository.stageManifestChunks(
            lottery.id,
            listOf(
                DrawManifestChunk(
                    drawId = drawId,
                    lotteryId = lottery.id,
                    chunkIndex = 0,
                    winnerUserIds = emptyList(), // 0 winners
                    loserUserIds = listOf("u1", "u2"), // 2 losers
                )
            )
        )
        repository.runDrawTransaction(
            lotteryId = lottery.id,
            candidateDrawId = drawId,
            expectedVersion = 0L,
            chunkCount = 1,
            participantCount = 2,
            winnerCount = 0,
            drawnAt = now,
            now = regEnd + 1000,
        )

        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u1", status = EntryStatus.WAITING))
        repository.seedEntry(LotteryEntry(lotteryId = lottery.id, userId = "u2", status = EntryStatus.WAITING))

        repository.reconcileEntryStatuses(lottery.id, drawId)

        assertEquals(EntryStatus.LOST, repository.getEntry(lottery.id, "u1")!!.status)
        assertEquals(EntryStatus.LOST, repository.getEntry(lottery.id, "u2")!!.status)
    }
}
