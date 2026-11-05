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

        lotteryRepository.saveEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-1", status = EntryStatus.WAITING, groupSize = 2)
        )
        lotteryRepository.saveEntry(
            LotteryEntry(lotteryId = lotteryId, userId = "user-2", status = EntryStatus.WAITING, groupSize = 1)
        )
        // Add a non-waiting entry that should not appear in waitingEntries
        lotteryRepository.saveEntry(
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
    fun performJoinLottery_duringRegistrationPeriod_savesEntryAndUpdatesState() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "entrant-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-1", now = 1500L)
        assertNull(joinError)
        assertNull(controller.error)
        assertNotNull(controller.userEntry)
        assertEquals("entrant-1", controller.userEntry!!.userId)
        assertEquals(EntryStatus.WAITING, controller.userEntry!!.status)
        assertEquals(3, controller.waitingEntries.size)
    }

    @Test
    fun performJoinLottery_beforeRegistrationStarts_rejectsWithErrorMessage() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "entrant-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-1", now = 500L)
        assertEquals("Registration has not started yet", joinError)
        assertEquals("Registration has not started yet", controller.error)
        assertNull(controller.userEntry)
        assertEquals(2, controller.waitingEntries.size)
    }

    @Test
    fun performJoinLottery_afterRegistrationEnds_rejectsWithErrorMessage() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "entrant-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-1", now = 2001L)
        assertEquals("Registration has ended", joinError)
        assertEquals("Registration has ended", controller.error)
        assertNull(controller.userEntry)
        assertEquals(2, controller.waitingEntries.size)
    }

    @Test
    fun performJoinLottery_withUnconfiguredPeriod_rejectsWithErrorMessage() = runTest {
        val unconfiguredLottery = Lottery(id = "lottery-unconfigured")
        val generatedId = lotteryRepository.addLottery(unconfiguredLottery)
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = generatedId,
            currentUserId = "entrant-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-1", now = 1500L)
        assertEquals("Registration period is not configured", joinError)
        assertEquals("Registration period is not configured", controller.error)
        assertNull(controller.userEntry)
    }

    @Test
    fun performJoinLottery_withReversedPeriod_rejectsWithErrorMessage() = runTest {
        val reversedLottery = Lottery(
            id = "lottery-reversed",
            registrationStart = 3000L,
            registrationEnd = 1000L
        )
        val generatedId = lotteryRepository.addLottery(reversedLottery)
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = generatedId,
            currentUserId = "entrant-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-1", now = 2000L)
        assertEquals("Invalid registration period", joinError)
        assertEquals("Invalid registration period", controller.error)
        assertNull(controller.userEntry)
    }

    @Test
    fun performJoinLottery_atExactRegistrationStart_isAllowed() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "entrant-start"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-start", now = 1000L)
        assertNull(joinError)
        assertNull(controller.error)
        assertNotNull(controller.userEntry)
    }

    @Test
    fun performJoinLottery_atExactRegistrationEnd_isAllowed() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "entrant-end"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "entrant-end", now = 2000L)
        assertNull(joinError)
        assertNull(controller.error)
        assertNotNull(controller.userEntry)
    }

    @Test
    fun performJoinLottery_withBlankUserId_rejectsWithErrorMessage() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = ""
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "", now = 1500L)
        assertEquals("Unable to join: user not identified.", joinError)
        assertEquals("Unable to join: user not identified.", controller.error)
        assertNull(controller.userEntry)
    }

    @Test
    fun performJoinLottery_whenAlreadyJoined_rejectsDuplicateWithErrorMessage() = runTest {
        setupData()
        val controller = LotteryDetailsController(
            lotteryRepository = lotteryRepository,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "user-1"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "user-1", now = 1500L)
        assertEquals("You have already joined this lottery.", joinError)
        assertEquals("You have already joined this lottery.", controller.error)
    }

    @Test
    fun performJoinLottery_whenRepositoryThrowsDuplicateLotteryEntryException_setsDuplicateError() = runTest {
        setupData()
        val duplicateThrowingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun getEntry(lotteryId: String, userId: String): LotteryEntry? = null
            override suspend fun saveEntry(entry: LotteryEntry) {
                throw com.example.vipers.domain.model.DuplicateLotteryEntryException("Duplicate entry")
            }
        }
        val controller = LotteryDetailsController(
            lotteryRepository = duplicateThrowingRepo,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "new-entrant"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "new-entrant", now = 1500L)
        assertEquals("You have already joined this lottery.", joinError)
        assertEquals("You have already joined this lottery.", controller.error)
    }

    @Test
    fun performJoinLottery_whenRepositoryThrowsOtherException_setsGenericError() = runTest {
        setupData()
        val failingRepo = object : LotteryRepository by lotteryRepository {
            override suspend fun saveEntry(entry: LotteryEntry) {
                throw RuntimeException("Database offline")
            }
        }
        val controller = LotteryDetailsController(
            lotteryRepository = failingRepo,
            profileRepository = profileRepository,
            lotteryId = lotteryId,
            currentUserId = "new-entrant"
        )
        controller.fetchData()

        val joinError = controller.performJoinLottery(userId = "new-entrant", now = 1500L)
        assertEquals("Unable to join lottery. Please try again.", joinError)
        assertEquals("Unable to join lottery. Please try again.", controller.error)
    }
}
