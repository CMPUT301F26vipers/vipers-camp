/*
 * CampsiteDetailsViewModelTest.kt
 *
 * Contains unit tests for the CampsiteDetailsViewModel to ensure state
 * is correctly updated when fetching a specific campsite.
 *
 * Known issues: None.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the initialization and data fetching logic of the CampsiteDetailsViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteDetailsViewModelTest {

    @Before
    fun setup() {
        // Overrides the Android UI thread with a generic thread for testing
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @Test
    fun init_withExistingCampsiteId_returnsCampsite() {
        // 1. ARRANGE
        val targetId = "camp1"
        val dummyData = listOf(
            Campsite(
                id = targetId,
                ownerId = "user12",
                name = "Maligne Lake Camp",
                address = "JASPER"
            ),
            Campsite(
                id = "camp2",
                ownerId = "user12",
                name = "Other Camp",
                address = "BANFF"
            )
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)

        // 2. ACT: Create ViewModel. It automatically fetches in the init block.
        val viewModel = CampsiteDetailsViewModel(fakeRepo, targetId)

        // 3. ASSERT
        val actualCampsite = viewModel.campsite
        TestCase.assertNotNull(actualCampsite)
        TestCase.assertEquals(targetId, actualCampsite?.id)
        TestCase.assertEquals("Maligne Lake Camp", actualCampsite?.name)
        TestCase.assertEquals("JASPER", actualCampsite?.address)
    }

    @Test
    fun init_withNonExistentCampsiteId_returnsNull() {
        // 1. ARRANGE
        val dummyData = listOf(
            Campsite(id = "camp1", ownerId = "user12", name = "Maligne Lake Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)

        // 2. ACT: Request an ID that doesn't exist in the repo
        val viewModel = CampsiteDetailsViewModel(fakeRepo, "nonExistentId")

        // 3. ASSERT
        val actualCampsite = viewModel.campsite
        TestCase.assertNull(actualCampsite)
    }

    @Test
    fun init_withEmptyRepository_returnsNull() {
        // 1. ARRANGE
        val fakeRepo = FakeCampsiteRepository(initial = emptyList())

        // 2. ACT
        val viewModel = CampsiteDetailsViewModel(fakeRepo, "camp1")

        // 3. ASSERT
        val actualCampsite = viewModel.campsite
        TestCase.assertNull(actualCampsite)
    }

    @Test
    fun init_withLotteries_loadsLotteriesForCampsite() {
        val targetId = "camp1"
        val campsite = Campsite(id = targetId, ownerId = "user12", name = "Maligne Lake Camp")
        val fakeCampsiteRepo = FakeCampsiteRepository(initial = listOf(campsite))

        val now = 100_000L
        val openLottery = Lottery(
            id = "lot1",
            ownerId = "user12",
            campsiteIds = listOf(targetId),
            nightsOffered = listOf("2027-05-01", "2027-05-05"),
            registrationStart = 50_000L,
            registrationEnd = 150_000L,
            bookingStart = 160_000L,
            bookingEnd = 200_000L
        )
        val closedLottery = Lottery(
            id = "lot2",
            ownerId = "user12",
            campsiteIds = listOf(targetId),
            nightsOffered = listOf("2027-09-01"),
            registrationStart = 1_000L,
            registrationEnd = 10_000L,
            bookingStart = 15_000L,
            bookingEnd = 20_000L
        )
        val entries = listOf(
            LotteryEntry(id = LotteryEntry.idFor("lot1", "u1"), lotteryId = "lot1", userId = "u1", status = EntryStatus.WAITING),
            LotteryEntry(id = LotteryEntry.idFor("lot1", "u2"), lotteryId = "lot1", userId = "u2", status = EntryStatus.WAITING),
            LotteryEntry(id = LotteryEntry.idFor("lot2", "u3"), lotteryId = "lot2", userId = "u3", status = EntryStatus.RESERVED)
        )
        val fakeLotteryRepo = FakeLotteryRepository(
            initialLotteries = listOf(openLottery, closedLottery),
            initialEntries = entries
        )

        val viewModel = CampsiteDetailsViewModel(fakeCampsiteRepo, fakeLotteryRepo, targetId)
        viewModel.loadDetails(now = now)

        val lotteries = viewModel.lotteries
        TestCase.assertEquals(2, lotteries.size)

        val first = lotteries[0]
        TestCase.assertEquals("lot1", first.id)
        TestCase.assertEquals("2027-05-01 to 2027-05-05", first.title)
        TestCase.assertEquals("2 joined", first.subtext)
        TestCase.assertEquals("Open", first.status)
        TestCase.assertTrue(first.isOpen)

        val second = lotteries[1]
        TestCase.assertEquals("lot2", second.id)
        TestCase.assertEquals("2027-09-01", second.title)
        TestCase.assertEquals("1 reserved", second.subtext)
        TestCase.assertEquals("Closed", second.status)
        TestCase.assertFalse(second.isOpen)
    }

    @Test
    fun refresh_reloadsUpdatedCampsite() = runBlocking {
        val targetId = "camp1"
        val initialCampsite = Campsite(id = targetId, ownerId = "user12", name = "Old Name")
        var currentCampsite: Campsite? = initialCampsite
        val fakeCampsiteRepo = object : com.example.vipers.domain.repository.CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = currentCampsite
        }
        val fakeLotteryRepo = FakeLotteryRepository()

        val viewModel = CampsiteDetailsViewModel(fakeCampsiteRepo, fakeLotteryRepo, targetId)
        TestCase.assertEquals("Old Name", viewModel.campsite?.name)

        // Update the campsite in repository
        currentCampsite = initialCampsite.copy(name = "Updated Name")

        viewModel.refresh()
        TestCase.assertEquals("Updated Name", viewModel.campsite?.name)
    }

    @After
    fun tearDown() {
        // Resets the thread after tests finish
        Dispatchers.resetMain()
    }
}
