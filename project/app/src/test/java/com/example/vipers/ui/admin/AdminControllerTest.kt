/*
 * AdminControllerTest.kt
 * Unit tests for AdminController (US 03.04.01, US 03.05.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AdminControllerTest {

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

        val viewModel = AdminController(fakeRepo, FakeProfileRepository())

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

        val viewModel = AdminController(fakeRepo, FakeProfileRepository())

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

        val viewModel = AdminController(fakeRepo, FakeProfileRepository())

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

        val viewModel = AdminController(failingRepo, FakeProfileRepository())

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

        val viewModel = AdminController(repository, FakeProfileRepository())

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

        val viewModel = AdminController(campsiteRepo, profileRepo)

        assertEquals("Alice Smith", viewModel.getOwnerName("owner1"))
        assertEquals("Deleted user", viewModel.getOwnerName("owner2"))
    }

    @Test
    fun fetchProfiles_withProfilesInRepository_loadsAllProfiles() = runTest {
        val profiles = listOf(
            UserProfile(id = "user-1", name = "Alice"),
            UserProfile(id = "user-2", name = "Bob"),
        )
        val profileRepo = FakeProfileRepository(initial = profiles)
        val campsiteRepo = FakeCampsiteRepository()
        val viewModel = AdminController(campsiteRepo, profileRepo)

        viewModel.fetchProfiles()

        assertEquals(2, viewModel.profiles.size)
        assertEquals(listOf("user-1", "user-2"), viewModel.profiles.map { it.id })
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun fetchProfiles_determinesCampsiteOwnershipCorrectly() = runTest {
        val profiles = listOf(
            UserProfile(id = "owner-1", name = "Alice"),
            UserProfile(id = "former-owner", name = "Bob"),
            UserProfile(id = "entrant-only", name = "Charlie"),
        )
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner-1", name = "Camp 1", removed = false),
            Campsite(id = "c2", ownerId = "former-owner", name = "Camp 2", removed = true),
        )
        val profileRepo = FakeProfileRepository(initial = profiles)
        val campsiteRepo = FakeCampsiteRepository(initial = campsites)
        val viewModel = AdminController(campsiteRepo, profileRepo)

        viewModel.fetchProfiles()

        assertTrue(viewModel.isCampsiteOwner("owner-1"))
        assertTrue(viewModel.isOwner("owner-1"))
        assertTrue("owner-1" in viewModel.ownerProfileIds)

        assertFalse(viewModel.isCampsiteOwner("former-owner"))
        assertFalse(viewModel.isOwner("former-owner"))
        assertFalse("former-owner" in viewModel.ownerProfileIds)

        assertFalse(viewModel.isCampsiteOwner("entrant-only"))
        assertFalse(viewModel.isOwner("entrant-only"))
        assertFalse("entrant-only" in viewModel.ownerProfileIds)
    }

    @Test
    fun fetchProfiles_whenProfileRepositoryFails_setsLoadFailedAndError() = runTest {
        val failingProfileRepo = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun getAllProfiles(): List<UserProfile> {
                throw IllegalStateException("Database error")
            }
        }
        val viewModel = AdminController(FakeCampsiteRepository(), failingProfileRepo)

        viewModel.fetchProfiles()

        assertTrue(viewModel.loadFailed)
        assertEquals("Unable to load profiles.", viewModel.error)
        assertFalse(viewModel.isLoading)
        assertTrue(viewModel.profiles.isEmpty())
        assertTrue(viewModel.ownerProfileIds.isEmpty())
    }

    @Test
    fun fetchProfiles_whenCampsiteRepositoryFails_setsLoadFailedAndError() = runTest {
        val failingCampsiteRepo = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsitesByOwner(ownerId: String): List<Campsite> {
                throw IllegalStateException("Network error")
            }
        }
        val profileRepo = FakeProfileRepository(listOf(UserProfile(id = "u1", name = "Alice")))
        val viewModel = AdminController(failingCampsiteRepo, profileRepo)

        viewModel.fetchProfiles()

        assertTrue(viewModel.loadFailed)
        assertEquals("Unable to load profiles.", viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadProfiles_launchesFetchProfilesAndCanRetryAfterFailure() = runTest {
        val profileRepo = FakeProfileRepository(listOf(UserProfile(id = "u1", name = "Alice")))
        var shouldFail = true
        val failingRepo = object : ProfileRepository by profileRepo {
            override suspend fun getAllProfiles(): List<UserProfile> {
                if (shouldFail) throw IllegalStateException("Database error")
                return profileRepo.getAllProfiles()
            }
        }
        val viewModel = AdminController(FakeCampsiteRepository(), failingRepo)

        viewModel.loadProfiles()

        assertTrue(viewModel.loadFailed)
        assertEquals("Unable to load profiles.", viewModel.error)

        shouldFail = false
        viewModel.loadProfiles()

        assertFalse(viewModel.loadFailed)
        assertNull(viewModel.error)
        assertEquals(1, viewModel.profiles.size)
        assertEquals("Alice", viewModel.profiles[0].name)
    }

    @Test
    fun getProfile_returnsProfileWhenFoundAndNullWhenMissing() = runTest {
        val profiles = listOf(UserProfile(id = "p1", name = "Alice"))
        val viewModel = AdminController(FakeCampsiteRepository(), FakeProfileRepository(profiles))
        viewModel.fetchProfiles()

        val found = viewModel.getProfile("p1")
        assertEquals("Alice", found?.name)

        val missing = viewModel.getProfile("p2")
        assertNull(missing)
    }

    @Test
    fun getCampsitesForOwner_returnsOnlyActiveCampsitesForOwner() = runTest {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "o1", name = "Camp 1", removed = false),
            Campsite(id = "c2", ownerId = "o1", name = "Camp 2", removed = true),
            Campsite(id = "c3", ownerId = "o2", name = "Camp 3", removed = false),
        )
        val viewModel = AdminController(FakeCampsiteRepository(campsites), FakeProfileRepository())

        val ownedByO1 = viewModel.getCampsitesForOwner("o1")
        assertEquals(1, ownedByO1.size)
        assertEquals("Camp 1", ownedByO1[0].name)

        val ownedByO2 = viewModel.getCampsitesForOwner("o2")
        assertEquals(1, ownedByO2.size)
        assertEquals("Camp 3", ownedByO2[0].name)

        val ownedByO3 = viewModel.getCampsitesForOwner("o3")
        assertTrue(ownedByO3.isEmpty())
    }
}
