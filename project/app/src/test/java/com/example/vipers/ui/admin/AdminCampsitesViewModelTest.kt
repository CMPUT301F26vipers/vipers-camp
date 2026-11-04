/*
 * AdminCampsitesViewModelTest.kt
 * Unit tests for AdminCampsitesViewModel (US 03.04.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdminCampsitesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withEmptyRepository_returnsEmptyListWithoutError() {
        val fakeRepo = FakeCampsiteRepository()

        val viewModel = AdminCampsitesViewModel(fakeRepo, FakeProfileRepository())

        assertTrue(viewModel.campsites.isEmpty())
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun init_withCampsitesFromDifferentOwners_loadsAllCampsites() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View"),
            Campsite(id = "c2", ownerId = "owner2", name = "Lakeside Camp"),
            Campsite(id = "c3", ownerId = "owner3", name = "Forest Lodge")
        )
        val fakeRepo = FakeCampsiteRepository(initial = campsites)

g        val viewModel = AdminCampsitesViewModel(fakeRepo, FakeProfileRepository())

        assertEquals(3, viewModel.campsites.size)
        assertEquals(listOf("c1", "c2", "c3"), viewModel.campsites.map { it.id })
        assertFalse(viewModel.loadFailed)
    }

    @Test
    fun init_excludesRemovedCampsites() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Active Camp 1", removed = false),
            Campsite(id = "c2", ownerId = "owner2", name = "Removed Camp", removed = true),
            Campsite(id = "c3", ownerId = "owner3", name = "Active Camp 2", removed = false)
        )
        val fakeRepo = FakeCampsiteRepository(initial = campsites)

        val viewModel = AdminCampsitesViewModel(fakeRepo, FakeProfileRepository())

        assertEquals(2, viewModel.campsites.size)
        assertEquals(listOf("c1", "c3"), viewModel.campsites.map { it.id })
        assertTrue(viewModel.campsites.none { it.removed })
    }

    @Test
    fun loadCampsites_whenRepositoryFails_setsLoadFailed() {
        val failingRepo = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> {
                throw IllegalStateException("Database error")
            }
        }

        val viewModel = AdminCampsitesViewModel(failingRepo, FakeProfileRepository())

        assertTrue(viewModel.loadFailed)
        assertTrue(viewModel.campsites.isEmpty())
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadCampsites_canRetryAfterRepositoryFailure() {
        val fakeRepo = FakeCampsiteRepository(listOf(Campsite(id = "c1", name = "Mountain View")))
        var shouldFail = true
        val repository = object : CampsiteRepository by fakeRepo {
            override suspend fun getAllCampsites(): List<Campsite> {
                if (shouldFail) throw IllegalStateException("Database error")
                return fakeRepo.getAllCampsites()
            }
        }

        val viewModel = AdminCampsitesViewModel(repository, FakeProfileRepository())

        assertTrue(viewModel.loadFailed)
        assertTrue(viewModel.campsites.isEmpty())

        shouldFail = false
        viewModel.loadCampsites()

        assertFalse(viewModel.loadFailed)
        assertEquals(1, viewModel.campsites.size)
        assertEquals("Mountain View", viewModel.campsites[0].name)
    }

    @Test
    fun init_resolvesOwnerDisplayNamesFromProfileRepository() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View"),
            Campsite(id = "c2", ownerId = "owner2", name = "Lakeside Camp")
        )
        val campsiteRepo = FakeCampsiteRepository(initial = campsites)
        val profileRepo = FakeProfileRepository(
            listOf(
                UserProfile(id = "owner1", name = "Alice Smith")
            )
        )

        val viewModel = AdminCampsitesViewModel(campsiteRepo, profileRepo)

        assertEquals("Alice Smith", viewModel.getOwnerName("owner1"))
        assertEquals("Deleted user", viewModel.getOwnerName("owner2"))
    }
}
