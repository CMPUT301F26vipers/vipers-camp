/*
 * ProfileControllerTest.kt
 * Unit tests for ProfileController (T-01, US 01.07.03), using the fake profile repository.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileControllerTest {

    private val repo = FakeProfileRepository()
    private lateinit var controller: ProfileController

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
        controller = ProfileController(repo, deviceId = "my-phone")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Welcome / first-launch creation tests ---

    @Test
    fun canContinue_onlyOnceANameIsEntered() {
        assertFalse(controller.canContinue)
        controller.onNameChange("   ")
        assertFalse(controller.canContinue)
        controller.onNameChange("Maya")
        assertTrue(controller.canContinue)
    }

    @Test
    fun createProfile_withNoName_savesNothing() = runBlocking {
        controller.onEmailChange("maya@example.com")
        controller.createProfile()

        assertNull(repo.getProfile("my-phone"))
        assertFalse(controller.created)
    }

    @Test
    fun createProfile_savesTheProfileUnderThisDevice() = runBlocking {
        controller.onNameChange("  Maya Chen ")
        controller.onEmailChange("maya@example.com")
        controller.onPhoneChange("780-555-0100")
        controller.createProfile()

        val saved = repo.getProfile("my-phone")!!
        assertEquals("Maya Chen", saved.name) // spaces trimmed
        assertEquals("maya@example.com", saved.email)
        assertEquals("780-555-0100", saved.phone)
        assertTrue(controller.created)
    }

    @Test
    fun createProfile_emailAndPhoneAreOptional() = runBlocking {
        controller.onNameChange("Maya Chen")
        controller.createProfile()

        val saved = repo.getProfile("my-phone")!!
        assertEquals("", saved.email)
        assertEquals("", saved.phone)
    }

    @Test
    fun createdName_isWhatOwnersSeeOnWaitingLists() = runBlocking {
        controller.onNameChange("Maya Chen")
        controller.createProfile()

        // waiting lists show names with getDisplayName(userId)
        assertEquals("Maya Chen", repo.getDisplayName("my-phone"))
    }

    @Test
    fun save_whenTheRepositoryFails_setsSaveFailed() = runBlocking {
        val failing = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun saveProfile(profile: UserProfile) = throw Exception("rejected")
        }
        val failingController = ProfileController(failing, deviceId = "my-phone")
        failingController.save(UserProfile(id = "my-phone", name = "Maya"))

        assertTrue(failingController.saveFailed)
        assertFalse(failingController.created)
        assertFalse(failingController.isSaving)
    }

    // --- Profile editing and loading tests ---

    @Test
    fun init_loadsExistingProfile() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com",
            phone = "7805551234"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        assertEquals("Sam", testController.name)
        assertEquals("sam@example.com", testController.email)
        assertEquals("7805551234", testController.phone)
        assertFalse(testController.loadFailed)
    }

    @Test
    fun saveProfile_updatesInformation() = runBlocking {
        val profile = UserProfile(id = "user1", name = "Sam")
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        testController.onNameChange("Sam Patel")
        testController.onEmailChange("sam@example.com")
        testController.onPhoneChange("7805551234")
        testController.saveProfile()

        val saved = fakeRepo.getProfile("user1")
        assertEquals("Sam Patel", saved?.name)
        assertEquals("sam@example.com", saved?.email)
        assertEquals("7805551234", saved?.phone)
        assertTrue(testController.saved)
    }

    @Test
    fun saveProfile_rejectsBlankName() = runBlocking {
        val profile = UserProfile(id = "user1", name = "Sam")
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        testController.onNameChange("")
        testController.saveProfile()

        assertEquals("Name is required", testController.errors["name"])
        assertEquals("Sam", fakeRepo.getProfile("user1")?.name)
    }

    @Test
    fun saveProfile_rejectsInvalidEmail() = runBlocking {
        val profile = UserProfile(id = "user1", name = "Sam")
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        testController.onEmailChange("not-an-email")
        testController.saveProfile()

        assertEquals("Invalid email address", testController.errors["email"])
        assertFalse(testController.saved)
    }

    @Test
    fun saveProfile_rejectsInvalidPhone() = runBlocking {
        val profile = UserProfile(id = "user1", name = "Sam")
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        testController.onPhoneChange("123")
        testController.saveProfile()

        assertEquals("Invalid phone number", testController.errors["phone"])
        assertFalse(testController.saved)
    }

    @Test
    fun saveProfile_preservesOtherFields() = runBlocking {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            imageId = "profile-photo",
            isAdmin = true,
            notificationsEnabled = false,
            createdAt = 1000L
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val testController = ProfileController(fakeRepo, "user1")

        testController.onNameChange("Sam Patel")
        testController.saveProfile()

        val saved = fakeRepo.getProfile("user1")
        assertEquals("user1", saved?.id)
        assertEquals("profile-photo", saved?.imageId)
        assertTrue(saved?.isAdmin == true)
        assertFalse(saved?.notificationsEnabled ?: true)
        assertEquals(1000L, saved?.createdAt)
    }

    @Test
    fun init_withMissingProfile_showsError() {
        val fakeRepo = FakeProfileRepository()
        val testController = ProfileController(fakeRepo, "missing")

        assertTrue(testController.loadFailed)
    }

    @Test
    fun saveProfile_trimsFieldsAndReloadsUpdatedDisplayName() = runBlocking {
        val repository = FakeProfileRepository(listOf(UserProfile(id = "user1", name = "Sam")))
        val testController = ProfileController(repository, "user1")
        testController.onNameChange("  Sam Patel  ")
        testController.onEmailChange("  sam@example.com  ")
        testController.onPhoneChange("  +1 (780) 555-1234  ")
        testController.saveProfile()

        val reloaded = ProfileController(repository, "user1")
        assertEquals("Sam Patel", reloaded.name)
        assertEquals("sam@example.com", reloaded.email)
        assertEquals("+1 (780) 555-1234", reloaded.phone)
        assertEquals("Sam Patel", repository.getDisplayName("user1"))
    }

    @Test
    fun saveProfile_allowsClearingOptionalContactFields() = runBlocking {
        val repository = FakeProfileRepository(listOf(
            UserProfile(id = "user1", name = "Sam", email = "sam@example.com", phone = "7805551234")
        ))
        val testController = ProfileController(repository, "user1")
        testController.onEmailChange("   ")
        testController.onPhoneChange("")
        testController.saveProfile()

        assertEquals("", repository.getProfile("user1")?.email)
        assertEquals("", repository.getProfile("user1")?.phone)
        assertTrue(testController.saved)
    }

    @Test
    fun loadProfile_canRetryAfterRepositoryFailure() {
        val fake = FakeProfileRepository(listOf(UserProfile(id = "user1", name = "Sam")))
        var failRead = true
        val repository = object : ProfileRepository by fake {
            override suspend fun getProfile(id: String): UserProfile? {
                if (failRead) throw IllegalStateException("Read failed")
                return fake.getProfile(id)
            }
        }
        val testController = ProfileController(repository, "user1")
        assertTrue(testController.loadFailed)
        assertFalse(testController.isLoading)

        failRead = false
        testController.loadProfile()
        assertFalse(testController.loadFailed)
        assertEquals("Sam", testController.name)
    }

    @Test
    fun saveProfile_canRetryAfterRepositoryFailure() = runBlocking {
        val original = UserProfile(id = "user1", name = "Sam")
        val fake = FakeProfileRepository(listOf(original))
        var failWrite = true
        val repository = object : ProfileRepository by fake {
            override suspend fun saveProfile(profile: UserProfile) {
                if (failWrite) throw IllegalStateException("Write failed")
                fake.saveProfile(profile)
            }
        }
        val testController = ProfileController(repository, "user1")
        testController.onNameChange("Sam Patel")
        testController.saveProfile()
        assertTrue(testController.saveFailed)
        assertFalse(testController.isSaving)
        assertFalse(testController.saved)
        assertEquals(original, fake.getProfile("user1"))

        failWrite = false
        testController.saveProfile()
        assertFalse(testController.saveFailed)
        assertTrue(testController.saved)
        assertEquals("Sam Patel", fake.getProfile("user1")?.name)
    }

    @Test
    fun saveProfile_doesNotReportNewerEditsAsSaved() = runBlocking {
        val fake = FakeProfileRepository(listOf(UserProfile(id = "user1", name = "Sam")))
        val finishSave = CompletableDeferred<Unit>()
        val repository = object : ProfileRepository by fake {
            override suspend fun saveProfile(profile: UserProfile) {
                finishSave.await()
                fake.saveProfile(profile)
            }
        }
        val testController = ProfileController(repository, "user1")
        testController.onNameChange("Sam Patel")
        testController.saveProfile()
        assertTrue(testController.isSaving)
        testController.onNameChange("Another name")
        finishSave.complete(Unit)

        assertEquals("Sam Patel", fake.getProfile("user1")?.name)
        assertEquals("Another name", testController.name)
        assertFalse(testController.saved)
        assertFalse(testController.isSaving)
    }
}
