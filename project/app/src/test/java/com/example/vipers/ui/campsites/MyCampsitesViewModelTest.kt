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

class MyCampsitesViewModelTest {

    @Before
    fun setup() {
        // Overrides the Android UI thread with a generic thread for testing
        Dispatchers.setMain(Dispatchers.Unconfined)
    }


    @Test
    fun loadCampsites_withNoCampsites_returnsEmptyList(){
        val myUserId = "user12"
        val someoneElseId = "user999"

        val dummyData = listOf(
            Campsite(id = "3", ownerId = someoneElseId, name = "Not My Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Fetch the campsites
        viewModel.loadCampsites(myUserId)

        // 3. ASSERT: The list should be perfectly empty
        val actualList = viewModel.campsites.value
        TestCase.assertEquals(0, actualList.size)
    }

    @Test
    fun loadCampsites_withCampsites_returnsPopulatedList() {
        // 1. ARRANGE: Create a repo WITH campsites for our user
        val myUserId = "user12"

        val dummyData = listOf(
            Campsite(id = "1", ownerId = myUserId, name = "My First Camp"),
            Campsite(id = "2", ownerId = myUserId, name = "My Second Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Fetch the campsites
        viewModel.loadCampsites(myUserId)

        // 3. ASSERT: The list should contain our 2 items
        val actualList = viewModel.campsites.value
        TestCase.assertEquals(2, actualList.size)
        TestCase.assertEquals("My First Camp", actualList[0].name)
    }

    @Test
    fun loadCampsites_withCampsitesButNothingUnderUser_returnsEmptyList() {
        // 1. ARRANGE: Create a repo WITH campsites for our user
        val myUserId = "user12"
        val otherID = "other12"

        val dummyData = listOf(
            Campsite(id = "1", ownerId = otherID, name = "My First Camp"),
            Campsite(id = "2", ownerId = otherID, name = "My Second Camp")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Fetch the campsites
        viewModel.loadCampsites(myUserId)

        // 3. ASSERT: The list should contain our 2 items
        val actualList = viewModel.campsites.value
        TestCase.assertEquals(0, actualList.size)
    }

    @After
    fun tearDown() {
        // Resets the thread after tests finish
        Dispatchers.resetMain()
    }
}