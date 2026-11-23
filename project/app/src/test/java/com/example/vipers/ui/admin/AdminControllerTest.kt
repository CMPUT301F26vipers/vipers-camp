/*
 * AdminControllerTest.kt
 * Unit tests for AdminController (US 03.04.01, US 03.05.01, US 03.06.01, US 03.12.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    @Test
    fun fetchMessageLogs_withEmptyRepository_returnsEmptyListWithoutError() = runBlocking {
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        assertTrue(viewModel.messageLogs.isEmpty())
        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        assertFalse(viewModel.isMessageLogsLoading)
    }

    @Test
    fun fetchMessageLogs_withMultipleConversations_preservesOrderFromRepository() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", text = "Old message", createdAt = 1000L),
            Message(id = "m2", reservationId = "res-2", text = "Middle message", createdAt = 2000L),
            Message(id = "m3", reservationId = "res-3", text = "Newest message", createdAt = 3000L),
        )
        val reservations = listOf(
            Reservation(id = "res-1", campsiteName = "Camp 1"),
            Reservation(id = "res-2", campsiteName = "Camp 2"),
            Reservation(id = "res-3", campsiteName = "Camp 3"),
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(reservations),
        )

        viewModel.fetchMessageLogs()

        assertEquals(3, viewModel.messageLogs.size)
        // MessageRepository.getAllThreads() returns threads sorted newest conversation first (res-3, res-2, res-1)
        assertEquals(listOf("res-3", "res-2", "res-1"), viewModel.messageLogs.map { it.reservationId })
        assertEquals(listOf(3000L, 2000L, 1000L), viewModel.messageLogs.map { it.lastMessageTime })
    }

    @Test
    fun fetchMessageLogs_resolvesOwnerEntrantAndCampsiteCorrectly() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", senderId = "user1", text = "Hi", createdAt = 100L)
        )
        val reservations = listOf(
            Reservation(
                id = "res-1",
                ownerId = "owner1",
                userId = "user1",
                campsiteName = "Pine Ridge Camp"
            )
        )
        val profiles = listOf(
            UserProfile(id = "owner1", name = "Owen Owner"),
            UserProfile(id = "user1", name = "Emma Entrant")
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(profiles),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(reservations),
        )

        viewModel.fetchMessageLogs()

        val log = viewModel.messageLogs.single()
        assertEquals("res-1", log.reservationId)
        assertEquals("Pine Ridge Camp", log.campsiteName)
        assertEquals("Owen Owner", log.ownerName)
        assertEquals("Emma Entrant", log.entrantName)
        assertEquals(100L, log.lastMessageTime)
    }

    @Test
    fun fetchMessageLogs_preservesCompleteMessageHistoryForEachConversation() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", senderId = "user1", text = "Is firewood available?", createdAt = 100L),
            Message(id = "m2", reservationId = "res-1", senderId = "owner1", text = "Yes, stacked by the cabin.", createdAt = 200L),
            Message(id = "m3", reservationId = "res-1", senderId = "user1", text = "Great, thanks!", createdAt = 300L)
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        val log = viewModel.messageLogs.single()
        assertEquals(3, log.messages.size)
        assertEquals(listOf("m1", "m2", "m3"), log.messages.map { it.id })
        assertEquals(listOf("Is firewood available?", "Yes, stacked by the cabin.", "Great, thanks!"), log.messages.map { it.text })
        assertEquals(300L, log.lastMessageTime)
    }

    @Test
    fun fetchMessageLogs_whenReservationMissing_retainsConversationWithFallbackInfo() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "missing-res", text = "Hello", createdAt = 500L)
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        assertEquals(1, viewModel.messageLogs.size)
        val log = viewModel.messageLogs.single()
        assertEquals("missing-res", log.reservationId)
        assertEquals("Unknown campsite", log.campsiteName)
        assertEquals("Deleted user", log.ownerName)
        assertEquals("Deleted user", log.entrantName)
        assertEquals(1, log.messages.size)
    }

    @Test
    fun fetchMessageLogs_whenProfileMissingOrDeleted_showsDeletedUser() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", text = "Hello", createdAt = 500L)
        )
        val reservations = listOf(
            Reservation(
                id = "res-1",
                ownerId = "deleted-owner",
                userId = "deleted-entrant",
                campsiteName = "Quiet Lake"
            )
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(reservations),
        )

        viewModel.fetchMessageLogs()

        val log = viewModel.messageLogs.single()
        assertEquals("Quiet Lake", log.campsiteName)
        assertEquals("Deleted user", log.ownerName)
        assertEquals("Deleted user", log.entrantName)
    }

    @Test
    fun fetchMessageLogs_whenProfileLookupFails_gracefullyFallsBackToDeletedUser() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", text = "Hello", createdAt = 500L)
        )
        val reservations = listOf(
            Reservation(id = "res-1", ownerId = "owner1", userId = "user1", campsiteName = "Mountain Peak")
        )
        val failingProfileRepo = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun getProfile(id: String): UserProfile? {
                throw IllegalStateException("Profile network failure")
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = failingProfileRepo,
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(reservations),
        )

        viewModel.fetchMessageLogs()

        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        val log = viewModel.messageLogs.single()
        assertEquals("Mountain Peak", log.campsiteName)
        assertEquals("Deleted user", log.ownerName)
        assertEquals("Deleted user", log.entrantName)
    }

    @Test
    fun fetchMessageLogs_unsortedMessages_areDisplayedOldestFirst() = runBlocking {
        val rawMessages = listOf(
            Message(id = "m3", reservationId = "res-1", text = "Latest", createdAt = 300L),
            Message(id = "m1", reservationId = "res-1", text = "Oldest", createdAt = 100L),
            Message(id = "m2", reservationId = "res-1", text = "Middle", createdAt = 200L),
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(rawMessages),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        val log = viewModel.messageLogs.single()
        assertEquals(listOf("m1", "m2", "m3"), log.messages.map { it.id })
        assertEquals(300L, log.lastMessageTime)
    }

    @Test
    fun fetchMessageLogs_unorderedConversations_areSortedNewestFirstWithDeterministicTieBreaker() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-old", text = "1", createdAt = 1000L),
            Message(id = "m2", reservationId = "res-new", text = "2", createdAt = 5000L),
            Message(id = "m3", reservationId = "res-tie-2", text = "3", createdAt = 3000L),
            Message(id = "m4", reservationId = "res-tie-1", text = "4", createdAt = 3000L),
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        val ids = viewModel.messageLogs.map { it.reservationId }
        assertEquals(listOf("res-new", "res-tie-1", "res-tie-2", "res-old"), ids)
    }

    @Test
    fun fetchMessageLogs_olderConcurrentRequestCannotOverwriteNewerCompletedRequest() = runTest {
        val slowGate = CompletableDeferred<Unit>()
        val slowMessage = Message(id = "m1", reservationId = "res-slow", text = "Slow", createdAt = 100L)
        val fastMessage = Message(id = "m2", reservationId = "res-fast", text = "Fast", createdAt = 200L)

        var callCount = 0
        val controlledRepo = object : MessageRepository by FakeMessageRepository() {
            override suspend fun getAllThreads(): List<List<Message>> {
                callCount++
                if (callCount == 1) {
                    slowGate.await()
                    return listOf(listOf(slowMessage))
                }
                return listOf(listOf(fastMessage))
            }
        }

        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = controlledRepo,
            reservationRepository = FakeReservationRepository(),
        )

        val job1 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }
        val job2 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }
        job2.join()

        assertEquals(1, viewModel.messageLogs.size)
        assertEquals("res-fast", viewModel.messageLogs.single().reservationId)
        assertFalse(viewModel.isMessageLogsLoading)

        slowGate.complete(Unit)
        job1.join()

        assertEquals(1, viewModel.messageLogs.size)
        assertEquals("res-fast", viewModel.messageLogs.single().reservationId)
        assertFalse(viewModel.isMessageLogsLoading)
        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
    }

    @Test
    fun fetchMessageLogs_loadingAndErrorStateBelongsToLatestRequest() = runTest {
        val slowGate = CompletableDeferred<Unit>()
        val fastMessage = Message(id = "m1", reservationId = "res-fast", text = "Fast", createdAt = 200L)

        var callCount = 0
        val controlledRepo = object : MessageRepository by FakeMessageRepository() {
            override suspend fun getAllThreads(): List<List<Message>> {
                callCount++
                if (callCount == 1) {
                    slowGate.await()
                    throw IllegalStateException("Slow request failed")
                }
                return listOf(listOf(fastMessage))
            }
        }

        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = controlledRepo,
            reservationRepository = FakeReservationRepository(),
        )

        val job1 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }
        val job2 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }
        job2.join()

        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        assertEquals(1, viewModel.messageLogs.size)

        slowGate.complete(Unit)
        job1.join()

        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        assertFalse(viewModel.isMessageLogsLoading)
        assertEquals(1, viewModel.messageLogs.size)
        assertEquals("res-fast", viewModel.messageLogs.single().reservationId)
    }

    @Test
    fun fetchMessageLogs_olderRequestCannotClearLoadingStateOfNewerRequest() = runTest {
        val slowGate1 = CompletableDeferred<Unit>()
        val slowGate2 = CompletableDeferred<Unit>()
        val message1 = Message(id = "m1", reservationId = "res-1", text = "M1", createdAt = 100L)
        val message2 = Message(id = "m2", reservationId = "res-2", text = "M2", createdAt = 200L)

        var callCount = 0
        val controlledRepo = object : MessageRepository by FakeMessageRepository() {
            override suspend fun getAllThreads(): List<List<Message>> {
                callCount++
                if (callCount == 1) {
                    slowGate1.await()
                    return listOf(listOf(message1))
                } else {
                    slowGate2.await()
                    return listOf(listOf(message2))
                }
            }
        }

        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = controlledRepo,
            reservationRepository = FakeReservationRepository(),
        )

        val job1 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }
        val job2 = launch(Dispatchers.Unconfined) { viewModel.fetchMessageLogs() }

        assertTrue(viewModel.isMessageLogsLoading)

        slowGate1.complete(Unit)
        job1.join()

        assertTrue(viewModel.isMessageLogsLoading)

        slowGate2.complete(Unit)
        job2.join()

        assertFalse(viewModel.isMessageLogsLoading)
        assertEquals(1, viewModel.messageLogs.size)
        assertEquals("res-2", viewModel.messageLogs.single().reservationId)
    }

    @Test
    fun fetchMessageLogs_temporaryReservationLookupFailure_reportedAndSucceedsOnRetry() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", text = "Hello", createdAt = 500L)
        )
        val reservation = Reservation(id = "res-1", campsiteName = "Pine Ridge", ownerId = "o1", userId = "u1")
        var shouldFail = true
        val fakeReservationRepo = FakeReservationRepository(listOf(reservation))
        val failingReservationRepo = object : ReservationRepository by fakeReservationRepo {
            override suspend fun getReservation(id: String): Reservation? {
                if (shouldFail) {
                    throw IllegalStateException("Reservation database failure")
                }
                return fakeReservationRepo.getReservation(id)
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = failingReservationRepo,
        )

        viewModel.fetchMessageLogs()

        assertTrue(viewModel.messageLogsLoadFailed)
        assertEquals("Unable to load message logs.", viewModel.messageLogsError)
        assertFalse(viewModel.isMessageLogsLoading)
        assertTrue(viewModel.messageLogs.isEmpty())

        // Retry after failure
        shouldFail = false
        viewModel.fetchMessageLogs()

        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        assertEquals(1, viewModel.messageLogs.size)
        val log = viewModel.messageLogs.single()
        assertEquals("res-1", log.reservationId)
        assertEquals("Pine Ridge", log.campsiteName)
    }

    @Test
    fun fetchMessageLogs_whenMessageRepositoryFails_setsLoadFailedAndError() = runBlocking {
        val failingMsgRepo = object : MessageRepository by FakeMessageRepository() {
            override suspend fun getAllThreads(): List<List<Message>> {
                throw IllegalStateException("Message repository error")
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = failingMsgRepo,
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        assertTrue(viewModel.messageLogsLoadFailed)
        assertEquals("Unable to load message logs.", viewModel.messageLogsError)
        assertFalse(viewModel.isMessageLogsLoading)
        assertTrue(viewModel.messageLogs.isEmpty())
    }

    @Test
    fun loadMessageLogs_launchesFetchMessageLogsAndCanRetryAfterFailure() = runBlocking {
        val message = Message(id = "m1", reservationId = "res-1", text = "Hi", createdAt = 100L)
        val fakeRepo = FakeMessageRepository(listOf(message))
        var shouldFail = true
        val failingRepo = object : MessageRepository by fakeRepo {
            override suspend fun getAllThreads(): List<List<Message>> {
                if (shouldFail) throw IllegalStateException("Database error")
                return fakeRepo.getAllThreads()
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = failingRepo,
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.loadMessageLogs()

        assertTrue(viewModel.messageLogsLoadFailed)
        assertEquals("Unable to load message logs.", viewModel.messageLogsError)

        shouldFail = false
        viewModel.loadMessageLogs()

        assertFalse(viewModel.messageLogsLoadFailed)
        assertNull(viewModel.messageLogsError)
        assertEquals(1, viewModel.messageLogs.size)
        assertEquals("res-1", viewModel.messageLogs.single().reservationId)
    }

    @Test
    fun fetchMessageLogs_keepsLoadingAndErrorStateSeparateFromCampsites() = runBlocking {
        val failingMsgRepo = object : MessageRepository by FakeMessageRepository() {
            override suspend fun getAllThreads(): List<List<Message>> {
                throw IllegalStateException("Message failure")
            }
        }
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = failingMsgRepo,
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        assertTrue(viewModel.messageLogsLoadFailed)
        assertEquals("Unable to load message logs.", viewModel.messageLogsError)
        assertFalse(viewModel.loadFailed)
        assertNull(viewModel.error)
    }

    @Test
    fun getConversation_returnsMatchingConversationOrNull() = runBlocking {
        val messages = listOf(
            Message(id = "m1", reservationId = "res-1", text = "Hello", createdAt = 100L)
        )
        val viewModel = AdminController(
            campsiteRepository = FakeCampsiteRepository(),
            profileRepository = FakeProfileRepository(),
            messageRepository = FakeMessageRepository(messages),
            reservationRepository = FakeReservationRepository(),
        )

        viewModel.fetchMessageLogs()

        val found = viewModel.getConversation("res-1")
        val missing = viewModel.getConversation("missing-res")

        assertEquals("res-1", found?.reservationId)
        assertNull(missing)
    }
}
