/*
 * AdminControllerTest.kt
 * Unit tests for AdminController (US 03.04.01, US 03.05.01, US 03.06.01).
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
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
        assertFalse(viewModel.profilesLoadFailed)
        assertFalse(viewModel.isProfilesLoading)
        assertNull(viewModel.profilesError)
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
        assertTrue("owner-1" in viewModel.ownerProfileIds)

        assertFalse(viewModel.isCampsiteOwner("former-owner"))
        assertFalse("former-owner" in viewModel.ownerProfileIds)

        assertFalse(viewModel.isCampsiteOwner("entrant-only"))
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

        assertTrue(viewModel.profilesLoadFailed)
        assertEquals("Unable to load profiles.", viewModel.profilesError)
        assertFalse(viewModel.isProfilesLoading)
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

        assertTrue(viewModel.profilesLoadFailed)
        assertEquals("Unable to load profiles.", viewModel.profilesError)
        assertFalse(viewModel.isProfilesLoading)
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

        assertTrue(viewModel.profilesLoadFailed)
        assertEquals("Unable to load profiles.", viewModel.profilesError)

        shouldFail = false
        viewModel.loadProfiles()

        assertFalse(viewModel.profilesLoadFailed)
        assertNull(viewModel.profilesError)
        assertEquals(1, viewModel.profiles.size)
        assertEquals("Alice", viewModel.profiles[0].name)
    }

    @Test
    fun fetchProfiles_keepsLoadingAndErrorStateSeparateFromCampsites() = runTest {
        val failingCampsiteRepo = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> {
                throw IllegalStateException("Campsite error")
            }
        }
        val profileRepo = FakeProfileRepository(listOf(UserProfile(id = "p1", name = "Alice")))
        val viewModel = AdminController(failingCampsiteRepo, profileRepo)

        // Campsite loading in init failed
        assertTrue(viewModel.loadFailed)
        assertEquals("Unable to load campsites.", viewModel.error)

        // Loading profiles should succeed and have clean profile state
        viewModel.fetchProfiles()

        assertFalse(viewModel.profilesLoadFailed)
        assertNull(viewModel.profilesError)
        assertFalse(viewModel.isProfilesLoading)
        assertEquals(1, viewModel.profiles.size)

        // Campsite state remains intact
        assertTrue(viewModel.loadFailed)
        assertEquals("Unable to load campsites.", viewModel.error)
    }

    @Test
    fun campsiteLoadingFailure_doesNotAffectProfileLoadingState() = runTest {
        val failingProfileRepo = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun getAllProfiles(): List<UserProfile> {
                throw IllegalStateException("Profile error")
            }
        }
        val campsite = Campsite(id = "c1", name = "Pine Camp")
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val viewModel = AdminController(campsiteRepo, failingProfileRepo)

        // Campsite loaded successfully
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
        assertEquals(1, viewModel.campsites.size)

        // Profile loading fails
        viewModel.fetchProfiles()

        assertTrue(viewModel.profilesLoadFailed)
        assertEquals("Unable to load profiles.", viewModel.profilesError)
        assertFalse(viewModel.isProfilesLoading)

        // Campsite state remains unaffected
        assertFalse(viewModel.loadFailed)
        assertFalse(viewModel.isLoading)
        assertEquals(1, viewModel.campsites.size)
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

    @Test
    fun getImage_returnsMatchingImageAssetOrNull() = runBlocking {
        val image = ImageAsset(id = "img-1", label = "Test Image")
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = FakeImageRepository(listOf(image)),
        )
        viewModel.fetchImages()

        assertEquals(image, viewModel.getImage("img-1"))
        assertNull(viewModel.getImage("non-existent"))
    }

    @Test
    fun fetchImages_resolvesCampsiteImageToCurrentCampsiteName() = runBlocking {
        val campsite = Campsite(id = "camp-1", name = "Sunset Point")
        val image = ImageAsset(
            id = "img-1",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "camp-1",
            label = "Old Label"
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(listOf(campsite)),
            profileRepository = FakeProfileRepository(),
            imageRepository = FakeImageRepository(listOf(image)),
        )

        viewModel.fetchImages()

        assertEquals("Sunset Point", viewModel.getImageOwnerName(image))
        assertEquals("Sunset Point", viewModel.imageOwnerNames[image.id])
    }

    @Test
    fun fetchImages_resolvesProfileImageToCurrentUserName() = runBlocking {
        val profile = UserProfile(id = "user-1", name = "Alice Smith")
        val image = ImageAsset(
            id = "img-1",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "user-1",
            label = "Old Label"
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(listOf(profile)),
            imageRepository = FakeImageRepository(listOf(image)),
        )

        viewModel.fetchImages()

        assertEquals("Alice Smith", viewModel.getImageOwnerName(image))
        assertEquals("Alice Smith", viewModel.imageOwnerNames[image.id])
    }

    @Test
    fun fetchImages_outdatedLabelDoesNotOverrideCurrentName() = runBlocking {
        val campsite = Campsite(id = "camp-1", name = "Current Camp Name")
        val profile = UserProfile(id = "user-1", name = "Current User Name")
        val campImage = ImageAsset(
            id = "img-c",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "camp-1",
            label = "Outdated Camp Label"
        )
        val profileImage = ImageAsset(
            id = "img-p",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "user-1",
            label = "Outdated Profile Label"
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(listOf(campsite)),
            profileRepository = FakeProfileRepository(listOf(profile)),
            imageRepository = FakeImageRepository(listOf(campImage, profileImage)),
        )

        viewModel.fetchImages()

        assertEquals("Current Camp Name", viewModel.getImageOwnerName(campImage))
        assertEquals("Current User Name", viewModel.getImageOwnerName(profileImage))
    }

    @Test
    fun fetchImages_whenOwnerMissing_fallsBackToImageLabel() = runBlocking {
        val campImage = ImageAsset(
            id = "img-c",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "missing-camp",
            label = "Saved Campsite Label"
        )
        val profileImage = ImageAsset(
            id = "img-p",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "missing-user",
            label = "Saved Profile Label"
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = FakeImageRepository(listOf(campImage, profileImage)),
        )

        viewModel.fetchImages()

        assertEquals("Saved Campsite Label", viewModel.getImageOwnerName(campImage))
        assertEquals("Saved Profile Label", viewModel.getImageOwnerName(profileImage))
    }

    @Test
    fun fetchImages_whenOwnerMissingAndLabelBlank_usesGenericFallback() = runBlocking {
        val campImage = ImageAsset(
            id = "img-c",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "missing-camp",
            label = ""
        )
        val profileImage = ImageAsset(
            id = "img-p",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "missing-user",
            label = "   "
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            imageRepository = FakeImageRepository(listOf(campImage, profileImage)),
        )

        viewModel.fetchImages()

        assertEquals("Campsite photo", viewModel.getImageOwnerName(campImage))
        assertEquals("Profile photo", viewModel.getImageOwnerName(profileImage))
    }

    @Test
    fun fetchImages_whenRepositoryLookupFails_handlesGracefullyWithoutFailingImageLoad() = runBlocking {
        val failingCampsiteRepo = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? {
                throw IllegalStateException("Database lookup failure")
            }
        }
        val failingProfileRepo = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun getProfile(id: String): UserProfile? {
                throw IllegalStateException("Network timeout")
            }
        }
        val campImage = ImageAsset(
            id = "img-c",
            ownerType = ImageAsset.OWNER_CAMPSITE,
            ownerId = "camp-1",
            label = "Fallback Camp Label"
        )
        val profileImage = ImageAsset(
            id = "img-p",
            ownerType = ImageAsset.OWNER_PROFILE,
            ownerId = "user-1",
            label = "Fallback Profile Label"
        )
        val viewModel = AdminController(
            campsiteRepository = failingCampsiteRepo,
            profileRepository = failingProfileRepo,
            imageRepository = FakeImageRepository(listOf(campImage, profileImage)),
        )

        viewModel.fetchImages()

        assertFalse(viewModel.imagesLoadFailed)
        assertNull(viewModel.imagesError)
        assertEquals(2, viewModel.images.size)
        assertEquals("Fallback Camp Label", viewModel.getImageOwnerName(campImage))
        assertEquals("Fallback Profile Label", viewModel.getImageOwnerName(profileImage))
    }
}
