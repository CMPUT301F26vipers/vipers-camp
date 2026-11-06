/*
 * MyCampsitesControllerTest.kt
 * Unit tests for MyCampsitesController (US 02.03.01): it loads only the current owner's campsites,
 * and shows an empty list when they have none. Uses the fake campsite repository.
 * Known issues: none.
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

class MyCampsitesControllerTest {

    @Before
    fun setup() {
        // Overrides the Android UI thread with a generic thread for testing
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @Test
    fun init_withNoCampsites_returnsEmptyList() {
        // 1. ARRANGE
        val myUserId = "user12"
        val someoneElseId = "user999"

        val dummyData = listOf(
            Campsite(id = "3", ownerId = someoneElseId, name = "Not My Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)

        // 2. ACT: Create ViewModel. It automatically fetches in the init block now.
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        // 3. ASSERT: No longer uses .value because we switched to mutableStateOf
        val actualList = viewModel.campsites
        TestCase.assertEquals(0, actualList.size)
    }

    @Test
    fun init_withCampsites_returnsPopulatedList() {
        // 1. ARRANGE
        val myUserId = "user12"

        val dummyData = listOf(
            Campsite(id = "1", ownerId = myUserId, name = "My First Camp"),
            Campsite(id = "2", ownerId = myUserId, name = "My Second Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)

        // 2. ACT: Create ViewModel
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        // 3. ASSERT
        val actualList = viewModel.campsites
        TestCase.assertEquals(2, actualList.size)
        TestCase.assertEquals("My First Camp", actualList[0].name)
    }

    @Test
    fun init_withCampsitesButNothingUnderUser_returnsEmptyList() {
        // 1. ARRANGE
        val myUserId = "user12"
        val otherID = "other12"

        val dummyData = listOf(
            Campsite(id = "1", ownerId = otherID, name = "My First Camp"),
            Campsite(id = "2", ownerId = otherID, name = "My Second Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)

        // 2. ACT: Create ViewModel
        val viewModel = MyCampsitesController(fakeRepo, myUserId)

        // 3. ASSERT
        val actualList = viewModel.campsites
        TestCase.assertEquals(0, actualList.size)
    }

    @After
    fun tearDown() {
        // Resets the thread after tests finish
        Dispatchers.resetMain()
    }
}