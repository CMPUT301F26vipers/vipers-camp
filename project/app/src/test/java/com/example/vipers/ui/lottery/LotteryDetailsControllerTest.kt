/*
 * LotteryDetailsControllerTest.kt
 * Unit tests for LotteryDetailsController data loading, waitlist resolution, error handling, and joining logic.
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.LotteryRepository
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
    private lateinit var campsiteRepository: FakeCampsiteRepository

    private val lotteryId = "lottery-1"
    private val campsiteId = "camp-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        lotteryRepository = FakeLotteryRepository()
        profileRepository = FakeProfileRepository()
        campsiteRepository = FakeCampsiteRepository(listOf(Campsite(id = campsiteId, maxOccupancy = 10)))
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

    private suspend fun setupOpenLottery(
        waitlistCapacity: Int? = 10,
        ownerId: String = "owner-1",
    ): Lottery {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = lotteryId,
            ownerId = ownerId,
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Open Lottery",
            registrationStart = now - 10_000L,
            registrationEnd = now + 10_000L,
            bookingStart = now + 15_000L,
            bookingEnd = now + 25_000L,
            winnersCount = 2,
            waitlistCapacity = waitlistCapacity,
        )
        lotteryRepository.addLottery(lottery)
        return lottery
    }

    private suspend fun setupClosedLottery(): Lottery {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = lotteryId,
            ownerId = "owner-1",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 20_000L,
            registrationEnd = now - 10_000L,
            bookingStart = now - 5_000L,
            bookingEnd = now + 5_000L,
            winnersCount = 2,
        )
        lotteryRepository.addLottery(lottery)
        return lottery
    }

    private suspend fun setupUpcomingLottery(): Lottery {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = lotteryId,
            ownerId = "owner-1",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now + 10_000L,
            registrationEnd = now + 20_000L,
            bookingStart = now + 25_000L,
            bookingEnd = now + 35_000L,
            winnersCount = 2,
        )
        lotteryRepository.addLottery(lottery)
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
    fun joinWaitingList_whenSuccessful_createsWaitingEntryWithCorrectIdsAndGroupSize() = runTest {
        setupOpenLottery()
        val entrantId = "user-entrant"
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 3)

        assertTrue(controller.joinSuccess)
        assertNull(controller.joinError)
        assertFalse(controller.isJoining)
        assertTrue(controller.isJoined)
        assertNotNull(controller.userEntry)

        val entry = controller.userEntry!!
        assertEquals("${lotteryId}_$entrantId", entry.id)
        assertEquals(lotteryId, entry.lotteryId)
        assertEquals(entrantId, entry.userId)
        assertEquals(3, entry.groupSize)
        assertEquals(EntryStatus.WAITING, entry.status)
        assertTrue(entry.joinedAt > 0L)

        val saved = lotteryRepository.getEntry(lotteryId, entrantId)
        assertNotNull(saved)
        assertEquals(entry, saved)
    }

    @Test
    fun joinWaitingList_whenUserIsOwner_isRejected() = runTest {
        setupOpenLottery(ownerId = "owner-1")
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "owner-1",
        )
        controller.fetchData()

        assertTrue(controller.isOwner)
        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertEquals("Owners cannot join their own lottery.", controller.joinError)
        assertFalse(controller.isJoined)
        assertNull(lotteryRepository.getEntry(lotteryId, "owner-1"))
    }

    @Test
    fun joinWaitingList_whenAlreadyOnWaitingList_isRejected() = runTest {
        setupOpenLottery()
        val entrantId = "user-entrant"
        lotteryRepository.seedEntry(
            LotteryEntry(
                id = "${lotteryId}_$entrantId",
                lotteryId = lotteryId,
                userId = entrantId,
                status = EntryStatus.WAITING,
                groupSize = 1,
            )
        )
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        assertTrue(controller.isJoined)
        controller.joinWaitingList(groupSize = 2)

        assertFalse(controller.joinSuccess)
        assertEquals("Already on the waiting list", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenRegistrationClosed_isRejected() = runTest {
        setupClosedLottery()
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertEquals("Registration is closed", controller.joinError)
        assertFalse(controller.isJoined)
    }

    @Test
    fun joinWaitingList_whenRegistrationUpcoming_isRejected() = runTest {
        setupUpcomingLottery()
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertEquals("Registration has not started", controller.joinError)
        assertFalse(controller.isJoined)
    }

    @Test
    fun joinWaitingList_whenWaitingListAtCapacity_isRejected() = runTest {
        setupOpenLottery(waitlistCapacity = 2)
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-1", status = EntryStatus.WAITING)
        )
        lotteryRepository.seedEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-2", status = EntryStatus.WAITING)
        )
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-3",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        assertTrue(controller.isFull)
        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertEquals("Waiting list is full", controller.joinError)
        assertFalse(controller.isJoined)
        assertNull(lotteryRepository.getEntry(lotteryId, "user-3"))
    }

    @Test
    fun joinWaitingList_whenConcurrentJoinFillsWaitlist_atomicJoinReportsWaitlistFull() = runTest {
        // Controller initial state sees 0 waiting entries, but repository simulates a concurrent join
        // that fills the capacity before this entrant's transaction commits.
        val openLottery = setupOpenLottery(waitlistCapacity = 1)
        val fakeRepo = FakeLotteryRepository(listOf(openLottery))
        val fullRepo = object : LotteryRepository by fakeRepo {
            override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult {
                return JoinWaitingListResult.WaitlistFull
            }
        }
        val controller = LotteryDetailsController(
            fullRepo,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoining)
        assertFalse(controller.isJoined)
        assertEquals("Waiting list is full", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenConcurrentJoinAlreadyExists_atomicJoinReportsAlreadyJoined() = runTest {
        val openLottery = setupOpenLottery(waitlistCapacity = 5)
        val fakeRepo = FakeLotteryRepository(listOf(openLottery))
        val conflictRepo = object : LotteryRepository by fakeRepo {
            override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult {
                return JoinWaitingListResult.AlreadyJoined
            }
        }
        val controller = LotteryDetailsController(
            conflictRepo,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoining)
        assertFalse(controller.isJoined)
        assertEquals("Already on the waiting list", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenCapacityIsUnlimited_allowsJoining() = runTest {
        setupOpenLottery(waitlistCapacity = null)
        for (i in 1..25) {
            lotteryRepository.seedEntry(
                LotteryEntry(lotteryId = lotteryId, userId = "user-$i", status = EntryStatus.WAITING)
            )
        }
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-new",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        assertFalse(controller.isFull)
        assertEquals(25, controller.waitingEntries.size)

        controller.joinWaitingList(groupSize = 2)

        assertTrue(controller.joinSuccess)
        assertTrue(controller.isJoined)
        assertEquals(26, controller.waitingEntries.size)
    }

    @Test
    fun joinWaitingList_whenGroupSizeIsInvalid_isRejected() = runTest {
        setupOpenLottery()
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 0)
        assertFalse(controller.joinSuccess)
        assertEquals("Group size must be at least 1", controller.joinError)

        controller.joinWaitingList(groupSize = -2)
        assertFalse(controller.joinSuccess)
        assertEquals("Group size must be at least 1", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenRepositoryFails_reportsErrorWithoutClaimingSuccess() = runTest {
        val now = System.currentTimeMillis()
        val openLottery = Lottery(
            id = lotteryId,
            ownerId = "owner-1",
            campsiteIds = listOf(campsiteId),
            registrationStart = now - 5000L,
            registrationEnd = now + 5000L,
            bookingStart = now + 6000L,
            bookingEnd = now + 7000L,
        )
        val fakeRepo = FakeLotteryRepository(listOf(openLottery))
        val failingRepo = object : LotteryRepository by fakeRepo {
            override suspend fun joinWaitingList(entry: LotteryEntry, now: Long): JoinWaitingListResult {
                throw RuntimeException("Database error")
            }
        }
        val controller = LotteryDetailsController(
            failingRepo,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoining)
        assertFalse(controller.isJoined)
        assertEquals("Unable to join waiting list. Please try again.", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenSuccessful_updatesAllControllerState() = runTest {
        setupOpenLottery()
        profileRepository.saveProfile(UserProfile(id = "user-entrant", name = "Charlie"))
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        assertEquals(0, controller.waitingEntries.size)
        assertFalse(controller.isJoined)
        assertNull(controller.userEntry)

        controller.joinWaitingList(groupSize = 4)

        assertTrue(controller.joinSuccess)
        assertNull(controller.joinError)
        assertFalse(controller.isJoining)
        assertTrue(controller.isJoined)
        assertEquals(1, controller.waitingEntries.size)
        assertEquals("Charlie", controller.userNames["user-entrant"])
    }

    @Test
    fun joinWaitingList_whenCurrentUserIdIsBlank_isRejected() = runTest {
        setupOpenLottery()
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = "",
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertEquals("User ID is required to join.", controller.joinError)
        assertFalse(controller.isJoined)
        assertNull(controller.userEntry)
        assertTrue(lotteryRepository.getEntries(lotteryId).isEmpty())
    }

    @Test
    fun joinWaitingList_whenCheckingExistingEntryFails_reportsJoinError() = runTest {
        val openLottery = setupOpenLottery()
        val fakeRepo = FakeLotteryRepository(listOf(openLottery))
        val failingRepo = object : LotteryRepository by fakeRepo {
            override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? {
                throw RuntimeException("Network error while checking entry")
            }
        }
        val controller = LotteryDetailsController(
            failingRepo,
            profileRepository,
            lotteryId,
            currentUserId = "user-entrant",
            campsiteRepository = campsiteRepository,
        )
        // userEntry is null, so performJoin calls getEntry and encounters the exception
        controller.joinWaitingList(groupSize = 1)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoining)
        assertFalse(controller.isJoined)
        assertEquals("Unable to join waiting list. Please try again.", controller.joinError)
    }

    @Test
    fun joinWaitingList_whenSaveSucceedsButRefreshFails_doesNotFalselyReportFailedJoin() = runTest {
        val openLottery = setupOpenLottery()
        val fakeRepo = FakeLotteryRepository(listOf(openLottery))
        var failGetEntries = false
        val failingRepo = object : LotteryRepository by fakeRepo {
            override suspend fun getEntries(lotteryId: String): List<LotteryEntry> {
                if (failGetEntries) {
                    throw RuntimeException("Network error on refresh")
                }
                return fakeRepo.getEntries(lotteryId)
            }
        }
        val entrantId = "user-entrant"
        val controller = LotteryDetailsController(
            failingRepo,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = campsiteRepository,
        )
        controller.fetchData()

        failGetEntries = true
        controller.joinWaitingList(groupSize = 2)

        assertTrue(controller.joinSuccess)
        assertNull(controller.joinError)
        assertFalse(controller.isJoining)
        assertTrue(controller.isJoined)
        assertNotNull(controller.userEntry)
        assertEquals(entrantId, controller.userEntry!!.userId)
        assertEquals(2, controller.userEntry!!.groupSize)

        // Verify the entry was actually saved in repository
        val saved = fakeRepo.getEntry(lotteryId, entrantId)
        assertNotNull(saved)
        assertEquals(EntryStatus.WAITING, saved!!.status)
    }

    @Test
    fun fetchData_loadsMaxOccupancyFromCampsiteRepository() = runTest {
        val openLottery = setupOpenLottery()
        val campsite = Campsite(id = campsiteId, maxOccupancy = 6)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            campsiteRepository = campsiteRepo,
        )

        controller.fetchData()

        assertEquals(6, controller.maxOccupancy)
    }

    @Test
    fun fetchData_usesMaximumOccupancyWhenMultipleCampsitesExist() = runTest {
        val multiCampsiteLottery = setupOpenLottery().copy(
            campsiteIds = listOf("camp-1", "camp-2")
        )
        val repo = FakeLotteryRepository(listOf(multiCampsiteLottery))
        val campsite1 = Campsite(id = "camp-1", maxOccupancy = 4)
        val campsite2 = Campsite(id = "camp-2", maxOccupancy = 8)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite1, campsite2))
        val controller = LotteryDetailsController(
            repo,
            profileRepository,
            lotteryId,
            campsiteRepository = campsiteRepo,
        )

        controller.fetchData()

        assertEquals(8, controller.maxOccupancy)
    }

    @Test
    fun fetchData_handlesMissingCampsiteSafelyWithoutAssumingArbitraryMaximum() = runTest {
        val openLottery = setupOpenLottery()
        val emptyCampsiteRepo = FakeCampsiteRepository(emptyList())
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            campsiteRepository = emptyCampsiteRepo,
        )

        controller.fetchData()

        assertNull(controller.maxOccupancy)
    }

    @Test
    fun fetchData_partiallyLoadedCampsitesLeavesMaxOccupancyNull() = runTest {
        val multiCampsiteLottery = setupOpenLottery().copy(
            campsiteIds = listOf("camp-1", "camp-2")
        )
        val repo = FakeLotteryRepository(listOf(multiCampsiteLottery))
        // Only camp-1 exists in repository; camp-2 is missing
        val campsite1 = Campsite(id = "camp-1", maxOccupancy = 4)
        val partialRepo = FakeCampsiteRepository(listOf(campsite1))
        val controller = LotteryDetailsController(
            repo,
            profileRepository,
            lotteryId,
            campsiteRepository = partialRepo,
        )

        controller.fetchData()

        // Incomplete campsite lookups should explicitly leave maxOccupancy null
        assertNull(controller.maxOccupancy)
    }

    @Test
    fun joinWaitingList_whenOccupancyUnavailable_isRejected() = runTest {
        setupOpenLottery()
        val emptyCampsiteRepo = FakeCampsiteRepository(emptyList())
        val entrantId = "user-entrant"
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = emptyCampsiteRepo,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 2)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoined)
        assertEquals("Campsite occupancy information is unavailable. Please try again.", controller.joinError)
    }

    @Test
    fun joinWaitingList_rejectsGroupSizeExceedingCampsiteMaxOccupancy() = runTest {
        setupOpenLottery()
        val campsite = Campsite(id = campsiteId, maxOccupancy = 4)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val entrantId = "user-entrant"
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = campsiteRepo,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 5)

        assertFalse(controller.joinSuccess)
        assertFalse(controller.isJoined)
        assertEquals("Group size cannot exceed maximum occupancy of 4", controller.joinError)
    }

    @Test
    fun joinWaitingList_succeedsWhenGroupSizeWithinCampsiteMaxOccupancy() = runTest {
        setupOpenLottery()
        val campsite = Campsite(id = campsiteId, maxOccupancy = 6)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val entrantId = "user-entrant"
        val controller = LotteryDetailsController(
            lotteryRepository,
            profileRepository,
            lotteryId,
            currentUserId = entrantId,
            campsiteRepository = campsiteRepo,
        )
        controller.fetchData()

        controller.joinWaitingList(groupSize = 6)

        assertTrue(controller.joinSuccess)
        assertTrue(controller.isJoined)
        assertNull(controller.joinError)
        assertEquals(6, controller.userEntry?.groupSize)
    }
    @Test
    fun fetchData_loadsAllEntriesIncludingNonWaiting() = runTest {
        setupData()
        val controller = LotteryDetailsController(lotteryRepository, profileRepository, lotteryId)

        controller.fetchData()

        assertEquals(2, controller.waitingEntries.size)
        assertEquals(3, controller.allEntries.size)
    }

    @Test
    fun drawLottery_executesDrawAndRefreshesData() = runTest {
        setupData()
        val drawer = LotteryDrawer(
            lotteryRepository = lotteryRepository,
            notificationRepository = FakeNotificationRepository(),
            campsiteRepository = FakeCampsiteRepository(),
        )
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            lotteryDrawer = drawer,
        )

        val result = controller.drawLottery()
        assertTrue(result is LotteryDrawResult.Success)

        assertEquals(0, controller.waitingEntries.size)
        assertEquals(3, controller.allEntries.size)
        val wonCount = controller.allEntries.count { it.status == EntryStatus.WON }
        assertEquals(3, wonCount)
    }

    @Test
    fun performLeave_whenUserOnWaitlist_succeedsAndUpdatesState() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "user-1",
            campsiteRepository = campsiteRepository,
        )

        assertTrue(controller.isJoined)
        assertTrue(controller.canLeave)
        assertEquals(2, controller.waitingEntries.size)

        val result = controller.performLeave()
        assertTrue(result)
        assertTrue(controller.leaveSuccess)
        assertNull(controller.joinError)
        assertFalse(controller.isJoined)
        assertNull(controller.userEntry)
        assertEquals(1, controller.waitingEntries.size)
        assertFalse(controller.waitingEntries.any { it.userId == "user-1" })

        // Verify repository state
        assertNull(lotteryRepository.getEntry(lotteryId, "user-1"))
        val updatedLottery = lotteryRepository.getLottery(lotteryId)!!
        assertFalse(updatedLottery.waitingUserIds.contains("user-1"))
    }

    @Test
    fun performLeave_whenUserNotOnWaitlist_failsAndSetsError() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "unjoined-user",
            campsiteRepository = campsiteRepository,
        )

        assertFalse(controller.isJoined)
        val result = controller.performLeave()
        assertFalse(result)
        assertFalse(controller.leaveSuccess)
        assertEquals("Not on the waiting list.", controller.joinError)
    }

    @Test
    fun performLeave_whenLotteryAlreadyDrawn_failsAndSetsError() = runTest {
        val now = System.currentTimeMillis()
        val drawnLottery = Lottery(
            id = "drawn-lottery",
            ownerId = "owner-1",
            campsiteIds = listOf(campsiteId),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now - 10_000,
            registrationEnd = now - 5_000,
            bookingStart = now - 4_000,
            bookingEnd = now + 100_000,
            drawnAt = now - 3_000,
        )
        val entry = LotteryEntry(
            id = LotteryEntry.idFor("drawn-lottery", "user-1"),
            lotteryId = "drawn-lottery",
            userId = "user-1",
            status = EntryStatus.WAITING,
        )
        val repo = FakeLotteryRepository(
            initialLotteries = listOf(drawnLottery),
            initialEntries = listOf(entry),
        )
        val controller = LotteryDetailsController(
            lotteryRepository = repo,
            profileRepository = profileRepository,
            lotteryId = "drawn-lottery",
            currentUserId = "user-1",
            campsiteRepository = campsiteRepository,
        )

        assertFalse(controller.canLeave)
        val result = controller.performLeave()
        assertFalse(result)
        assertFalse(controller.leaveSuccess)
        assertEquals("Cannot leave after the lottery has been drawn.", controller.joinError)
    }

    @Test
    fun performLeave_whenRepositoryThrowsException_setsErrorAndPreservesState() = runTest {
        setupData()
        val failingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun leaveWaitingList(lotteryId: String, userId: String) {
                throw Exception("Network failure")
            }
        }
        val controller = LotteryDetailsController(
            lotteryRepository = failingRepo,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "user-1",
            campsiteRepository = campsiteRepository,
        )

        assertTrue(controller.isJoined)
        val result = controller.performLeave()
        assertFalse(result)
        assertFalse(controller.leaveSuccess)
        assertEquals("Network failure", controller.joinError)
        assertTrue(controller.isJoined)
    }

    @Test
    fun performLeave_whenRefreshFailsAfterSuccessfulLeave_stillReflectsLeaveSuccessAndUnjoinedState() = runTest {
        setupData()
        var leaveCalled = false
        val repoWithFailingRefresh = object : LotteryRepository by lotteryRepository {
            override suspend fun leaveWaitingList(lotteryId: String, userId: String) {
                lotteryRepository.leaveWaitingList(lotteryId, userId)
                leaveCalled = true
            }

            override suspend fun getEntries(lotteryId: String): List<LotteryEntry> {
                if (leaveCalled) {
                    throw RuntimeException("Network dropped during refresh")
                }
                return lotteryRepository.getEntries(lotteryId)
            }
        }
        val controller = LotteryDetailsController(
            lotteryRepository = repoWithFailingRefresh,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "user-1",
            campsiteRepository = campsiteRepository,
        )

        assertTrue(controller.isJoined)
        val result = controller.performLeave()
        assertTrue(result)
        assertTrue(controller.leaveSuccess)
        assertFalse(controller.isJoined)
        assertNull(controller.userEntry)
        assertFalse(controller.waitingEntries.any { it.userId == "user-1" })
        assertNull(controller.error)
    }

    @Test
    fun canLeave_evaluatesCorrectly() = runTest {
        setupData()
        val joinedController = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "user-1",
        )
        assertTrue(joinedController.canLeave)

        val ownerController = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "owner-1",
        )
        assertFalse(ownerController.canLeave)

        val nonJoinedController = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "random-user",
        )
        assertFalse(nonJoinedController.canLeave)
    }
}
