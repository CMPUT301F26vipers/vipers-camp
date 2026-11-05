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
import com.example.vipers.domain.model.Campsite
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests the initialization and data fetching logic of the CampsiteDetailsViewModel.
 */
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
                address = "BANFF")
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

    @After
    fun tearDown() {
        // Resets the thread after tests finish
        Dispatchers.resetMain()
    }
}