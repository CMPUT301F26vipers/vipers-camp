/*
 * WaitingListControllerTest.kt
 * Unit tests for WaitingListController (US 02.09.01): what the owner's waiting list shows, the
 * count with the limit, results after the draw, and live updates. Uses the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.LotteryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingListControllerTest {

    private val lotteryId = "hinton-may"

    // registration Mar 25 – Apr 1 2027, booking Apr 2 – Apr 9, as on the O09 mockup
    private val lottery = Lottery(
        id = lotteryId,
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        nightsDescription = "May 2027 weekends",
        registrationStart = 1_806_000_000_000L,
        registrationEnd = 1_806_600_000_000L,
        bookingStart = 1_806_700_000_000L,
        bookingEnd = 1_807_300_000_000L,
        winnersCount = 2,
        waitlistCapacity = 10,
    )

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "aisha", name = "Aisha Khan"),
            UserProfile(id = "liam", name = "Liam Chen"),
        ),
    )

    private fun entry(userId: String, joinedAt: Long, groupSize: Int = 2, status: EntryStatus = EntryStatus.WAITING) =
        LotteryEntry(
            id = LotteryEntry.idFor(lotteryId, userId),
            lotteryId = lotteryId,
            userId = userId,
            groupSize = groupSize,
            joinedAt = joinedAt,
            status = status,
        )

    private fun controllerFor(repo: LotteryRepository) = WaitingListController(repo, profiles, lotteryId)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsEntrantsOldestFirstWithNamesAndGroupSizes() = runTest {
        val repo = FakeLotteryRepository(
            listOf(lottery),
            listOf(entry("aisha", joinedAt = 2_000, groupSize = 4), entry("sam", joinedAt = 1_000, groupSize = 2)),
        )
        val controller = controllerFor(repo)

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertEquals(listOf("sam", "aisha"), controller.entries.map { it.userId })
        assertEquals(listOf(2, 4), controller.entries.map { it.groupSize })
        assertEquals("Sam Patel", controller.names["sam"])
        assertEquals("Aisha Khan", controller.names["aisha"])
    }

    @Test
    fun countIncludesTheLimitWhenOneIsSet() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("sam", 1_000), entry("aisha", 2_000)))
        assertEquals("2 of 10 joined", controllerFor(repo).countLabel)
    }

    @Test
    fun countHasNoLimitWhenCapacityIsUnlimited() = runTest {
        val repo = FakeLotteryRepository(
            listOf(lottery.copy(waitlistCapacity = null)),
            listOf(entry("sam", 1_000)),
        )
        assertEquals("1 joined", controllerFor(repo).countLabel)
    }

    @Test
    fun removedEntriesAreNotShownOrCounted() = runTest {
        val repo = FakeLotteryRepository(
            listOf(lottery),
            listOf(entry("sam", 1_000), entry("aisha", 2_000, status = EntryStatus.REMOVED)),
        )
        val controller = controllerFor(repo)

        assertEquals(listOf("sam"), controller.entries.map { it.userId })
        assertEquals("1 of 10 joined", controller.countLabel)
    }

    @Test
    fun afterTheDrawEachEntrantsResultIsShown() = runTest {
        val repo = FakeLotteryRepository(
            listOf(lottery.copy(drawnAt = 1_806_650_000_000L)),
            listOf(
                entry("sam", 1_000, status = EntryStatus.WON),
                entry("aisha", 2_000, status = EntryStatus.LOST),
                entry("liam", 3_000, status = EntryStatus.DECLINED),
            ),
        )
        val controller = controllerFor(repo)

        assertTrue(controller.isDrawn)
        assertEquals(
            listOf(EntryStatus.WON, EntryStatus.LOST, EntryStatus.DECLINED),
            controller.entries.map { it.status },
        )
    }

    @Test
    fun beforeTheDrawIsDrawnIsFalse() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("sam", 1_000)))
        assertFalse(controllerFor(repo).isDrawn)
    }

    @Test
    fun listUpdatesLiveWhenAnEntrantJoins() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("sam", 1_000)))
        val controller = controllerFor(repo)
        assertEquals(1, controller.entries.size)

        repo.saveEntry(entry("liam", 5_000, groupSize = 3))

        assertEquals(listOf("sam", "liam"), controller.entries.map { it.userId })
        assertEquals("Liam Chen", controller.names["liam"])
        assertEquals("2 of 10 joined", controller.countLabel)
    }

    @Test
    fun listUpdatesLiveWhenAnEntrantLeaves() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("sam", 1_000), entry("aisha", 2_000)))
        val controller = controllerFor(repo)

        repo.saveEntry(entry("aisha", 2_000, status = EntryStatus.REMOVED))

        assertEquals(listOf("sam"), controller.entries.map { it.userId })
    }

    @Test
    fun listUpdatesLiveWhenTheDrawResultsArrive() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("sam", 1_000)))
        val controller = controllerFor(repo)

        repo.saveEntry(entry("sam", 1_000, status = EntryStatus.WON))

        assertEquals(EntryStatus.WON, controller.entries.single().status)
    }

    @Test
    fun entrantWithoutAProfileShowsDeletedUser() = runTest {
        val repo = FakeLotteryRepository(listOf(lottery), listOf(entry("gone", 1_000)))
        assertEquals("Deleted user", controllerFor(repo).names["gone"])
    }

    @Test
    fun emptyWaitingListShowsNoEntrants() = runTest {
        val controller = controllerFor(FakeLotteryRepository(listOf(lottery)))

        assertFalse(controller.loading)
        assertTrue(controller.entries.isEmpty())
        assertEquals("0 of 10 joined", controller.countLabel)
    }

    @Test
    fun missingLotteryShowsAnError() = runTest {
        val controller = controllerFor(FakeLotteryRepository())

        assertFalse(controller.loading)
        assertEquals("Lottery not found.", controller.error)
    }

    @Test
    fun failedLoadShowsAnError() = runTest {
        val failing = object : LotteryRepository by FakeLotteryRepository(listOf(lottery)) {
            override suspend fun getLottery(id: String): Lottery? = throw IllegalStateException("offline")
        }
        val controller = controllerFor(failing)

        assertFalse(controller.loading)
        assertEquals("Couldn't load the waiting list. Please try again.", controller.error)
    }
}
