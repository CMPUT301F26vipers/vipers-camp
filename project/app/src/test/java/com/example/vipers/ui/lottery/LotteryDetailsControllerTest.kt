/*
 * LotteryDetailsControllerTest.kt
 * Unit tests for LotteryDetailsController data loading, waitlist resolution, and error handling.
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
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LotteryDetailsControllerTest {

    private lateinit var lotteryRepository: FakeLotteryRepository
    private lateinit var profileRepository: FakeProfileRepository

    private val lotteryId = "lottery-1"
    private val campsiteId = "camp-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        lotteryRepository = FakeLotteryRepository()
        profileRepository = FakeProfileRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun setupData(): Lottery {
        val lottery = Lottery(
            id = lotteryId,
            ownerId = "owner-1",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            nightsDescription = "June Weekend",
            registrationStart = 1000L,
            registrationEnd = 2000L,
            bookingStart = 2000L,
            bookingEnd = 3000L,
            winnersCount = 2,
            waitlistCapacity = 10,
        )
        lotteryRepository.addLottery(lottery)

        profileRepository.saveProfile(UserProfile(id = "user-1", name = "Alice"))
        profileRepository.saveProfile(UserProfile(id = "user-2", name = "Bob"))

        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-1", status = EntryStatus.WAITING, groupSize = 2)
        )
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-2", status = EntryStatus.WAITING, groupSize = 1)
        )
        // Add a non-waiting entry that should not appear in waitingEntries
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-3", status = EntryStatus.WON, groupSize = 4)
        )

        return lottery
    }

    @Test
    fun fetchData_loadsLotteryDetailsAndResolvesWaitlist() = runTest {
        val original = setupData()
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId)

        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertNotNull(controller.lottery)
        assertEquals(original.id, controller.lottery!!.id)
        assertEquals("June Weekend", controller.lottery!!.nightsDescription)
        assertEquals(2, controller.waitingEntries.size)
        assertTrue(controller.waitingEntries.all { it.status == EntryStatus.WAITING })

        assertEquals("Alice", controller.userNames["user-1"])
        assertEquals("Bob", controller.userNames["user-2"])
    }

    @Test
    fun fetchData_canBeInvokedWithRunBlocking() = runBlocking {
        setupData()
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId)

        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertEquals(2, controller.waitingEntries.size)
    }

    @Test
    fun fetchData_whenLotteryNotFound_leavesLotteryNull() = runTest {
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, "nonexistent-lottery")

        controller.fetchData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertNull(controller.lottery)
        assertTrue(controller.waitingEntries.isEmpty())
    }

    @Test
    fun fetchData_whenRepositoryFails_setsError() = runTest {
        val failingRepo = object : LotteryRepository by FakeLotteryRepository() {
            override suspend fun getLottery(id: String): Lottery? = throw RuntimeException("Network error")
        }
        val controller = LotteryDetailsController(failingRepo, profileRepository, lotteryId)

        controller.fetchData()

        assertFalse(controller.loading)
        assertEquals(
            "Unable to load lottery details. Please check your network connection and try again.",
            controller.error
        )
    }

    @Test
    fun loadData_triggersFetchAndPreservesState() = runTest {
        setupData()
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId)

        controller.loadData()

        assertFalse(controller.loading)
        assertNull(controller.error)
        assertNotNull(controller.lottery)
        assertEquals(2, controller.waitingEntries.size)
    }

    @Test
    fun fetchData_loadsAllEntriesIncludingNonWaiting() = runTest {
        setupData()
        profileRepository.saveProfile(com.example.vipers.domain.model.UserProfile(id = "user-3", name = "Charlie"))
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId)

        controller.fetchData()

        assertEquals(2, controller.waitingEntries.size)
        assertEquals(3, controller.allEntries.size)
        assertEquals("Charlie", controller.userNames["user-3"])
    }

    @Test
    fun drawLottery_executesDrawAndRefreshesData() = runTest {
        setupData()
        val drawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = com.example.vipers.data.fake.FakeNotificationRepository(),
            campsiteRepository = com.example.vipers.data.fake.FakeCampsiteRepository(),
        )
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId, drawer)

        // Draw at a timestamp after registrationEnd (2000L)
        val result = controller.drawLottery()
        assertTrue(result is LotteryDrawResult.Success)

        assertEquals(0, controller.waitingEntries.size)
        assertEquals(3, controller.allEntries.size)
        val wonCount = controller.allEntries.count { it.status == EntryStatus.WON }
        assertEquals(3, wonCount) // 2 originally waiting won + 1 pre-existing won
    }
}
