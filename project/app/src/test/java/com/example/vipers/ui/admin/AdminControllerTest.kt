/*
 * AdminControllerTest.kt
 * Unit tests for AdminController (US 03.04.01, US 03.06.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    fun fetchImages_withImagesInRepository_loadsImagesNewestFirst() = runBlocking {
        val olderImage = ImageAsset(
            id = "img-1",
            uploaderId = "user-1",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "camp-1",
            label = "Banff Spot",
            createdAt = 1000L,
        )
        val newerImage = ImageAsset(
            id = "img-2",
            uploaderId = "user-2",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "user-2",
            label = "Profile Photo",
            createdAt = 2000L,
        )
        val imageRepo = FakeImageRepository(listOf(olderImage, newerImage))
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = imageRepo,
        )

        viewModel.fetchImages()

        assertEquals(2, viewModel.images.size)
        assertEquals("img-2", viewModel.images[0].id)
        assertEquals("img-1", viewModel.images[1].id)
        assertFalse(viewModel.isImagesLoading)
        assertFalse(viewModel.imagesLoadFailed)
        assertNull(viewModel.imagesError)
    }

    @Test
    fun fetchImages_includesBothCampsiteAndProfileImages() = runBlocking {
        val campsiteImage = ImageAsset(
            id = "camp-img",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "camp-1",
            label = "Jasper Peak",
            createdAt = 500L,
        )
        val profileImage = ImageAsset(
            id = "profile-img",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "user-1",
            label = "Sam Patel",
            createdAt = 600L,
        )
        val imageRepo = FakeImageRepository(listOf(campsiteImage, profileImage))
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = imageRepo,
        )

        viewModel.fetchImages()

        assertEquals(2, viewModel.images.size)
        assertTrue(viewModel.images.any { it.ownerType == ImageAsset.OWNER_CAMPSITE && it.label == "Jasper Peak" })
        assertTrue(viewModel.images.any { it.ownerType == ImageAsset.OWNER_PROFILE && it.label == "Sam Patel" })
    }

    @Test
    fun fetchImages_whenRepositoryFails_setsLoadFailedAndError() = runBlocking {
        val failingRepo = object : ImageRepository by FakeImageRepository() {
            override suspend fun getAllImages(): List<ImageAsset> {
                throw IllegalStateException("Database error")
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = failingRepo,
        )

        viewModel.fetchImages()

        assertTrue(viewModel.imagesLoadFailed)
        assertEquals("Unable to load images.", viewModel.imagesError)
        assertFalse(viewModel.isImagesLoading)
        assertTrue(viewModel.images.isEmpty())
    }

    @Test
    fun loadImages_launchesFetchImagesAndCanRetryAfterFailure() = runBlocking {
        val image = ImageAsset(id = "img-1", createdAt = 100L)
        val fakeRepo = FakeImageRepository(listOf(image))
        var shouldFail = true
        val failingRepo = object : ImageRepository by fakeRepo {
            override suspend fun getAllImages(): List<ImageAsset> {
                if (shouldFail) throw IllegalStateException("Database error")
                return fakeRepo.getAllImages()
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = failingRepo,
        )

        viewModel.loadImages()

        assertTrue(viewModel.imagesLoadFailed)
        assertEquals("Unable to load images.", viewModel.imagesError)

        shouldFail = false
        viewModel.loadImages()

        assertFalse(viewModel.imagesLoadFailed)
        assertNull(viewModel.imagesError)
        assertEquals(1, viewModel.images.size)
        assertEquals("img-1", viewModel.images[0].id)
    }

    @Test
    fun fetchImages_keepsLoadingAndErrorStateSeparateFromCampsites() = runBlocking {
        val failingImageRepo = object : ImageRepository by FakeImageRepository() {
            override suspend fun getAllImages(): List<ImageAsset> {
                throw IllegalStateException("Image load error")
            }
        }
        val campsite = Campsite(id = "c1", name = "Camp 1")
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val viewModel = AdminController(
            campsiteRepository = campsiteRepo,
            profileRepository = FakeProfileRepository(),
            imageRepository = failingImageRepo,
        )

        // Campsites loaded successfully during init
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
        assertEquals(1, viewModel.campsites.size)

        // Images fail to load
        viewModel.fetchImages()

        // Image error state is set
        assertTrue(viewModel.imagesLoadFailed)
        assertEquals("Unable to load images.", viewModel.imagesError)
        assertFalse(viewModel.isImagesLoading)

        // Campsite state remains unaffected
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
        assertEquals(1, viewModel.campsites.size)
    }
}
