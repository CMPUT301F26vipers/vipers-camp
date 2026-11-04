/*
 * ProfileViewModelTest.kt
 * Tests loading, editing and saving profile information.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @Test
    fun init_loadsExistingProfile() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com",
            phone = "7805551234"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))

        val viewModel = ProfileViewModel(fakeRepo, "user1")

        TestCase.assertEquals("Sam", viewModel.name)
        TestCase.assertEquals("sam@example.com", viewModel.email)
        TestCase.assertEquals("7805551234", viewModel.phone)
        TestCase.assertFalse(viewModel.loadFailed)
    }

    @Test
    fun saveProfile_updatesInformation() = runBlocking {
        val profile = UserProfile(
            id = "user1",
            name = "Sam"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        viewModel.onNameChange("Sam Patel")
        viewModel.onEmailChange("sam@example.com")
        viewModel.onPhoneChange("7805551234")
        viewModel.saveProfile()

        val saved = fakeRepo.getProfile("user1")

        TestCase.assertEquals("Sam Patel", saved?.name)
        TestCase.assertEquals("sam@example.com", saved?.email)
        TestCase.assertEquals("7805551234", saved?.phone)
        TestCase.assertTrue(viewModel.saved)
    }

    @Test
    fun saveProfile_rejectsBlankName() = runBlocking {
        val profile = UserProfile(
            id = "user1",
            name = "Sam"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        viewModel.onNameChange("")
        viewModel.saveProfile()

        TestCase.assertEquals(
            "Name is required",
            viewModel.errors["name"]
        )
        TestCase.assertEquals(
            "Sam",
            fakeRepo.getProfile("user1")?.name
        )
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
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        viewModel.onNameChange("Sam Patel")
        viewModel.saveProfile()

        val saved = fakeRepo.getProfile("user1")

        TestCase.assertEquals("user1", saved?.id)
        TestCase.assertEquals("profile-photo", saved?.imageId)
        TestCase.assertTrue(saved?.isAdmin == true)
        TestCase.assertFalse(saved?.notificationsEnabled ?: true)
        TestCase.assertEquals(1000L, saved?.createdAt)
    }

    @Test
    fun init_withMissingProfile_showsError() {
        val fakeRepo = FakeProfileRepository()

        val viewModel = ProfileViewModel(fakeRepo, "missing")

        TestCase.assertTrue(viewModel.loadFailed)
    }

    @Test
    fun saveProfile_trimsFieldsAndReloadsUpdatedDisplayName() = runBlocking {
        val repository = FakeProfileRepository(listOf(UserProfile(id = "user1", name = "Sam")))
        val viewModel = ProfileViewModel(repository, "user1")
        viewModel.onNameChange("  Sam Patel  ")
        viewModel.onEmailChange("  sam@example.com  ")
        viewModel.onPhoneChange("  +1 (780) 555-1234  ")
        viewModel.saveProfile()

        val reloaded = ProfileViewModel(repository, "user1")
        TestCase.assertEquals("Sam Patel", reloaded.name)
        TestCase.assertEquals("sam@example.com", reloaded.email)
        TestCase.assertEquals("+1 (780) 555-1234", reloaded.phone)
        TestCase.assertEquals("Sam Patel", repository.getDisplayName("user1"))
    }

    @Test
    fun saveProfile_allowsClearingOptionalContactFields() = runBlocking {
        val repository = FakeProfileRepository(listOf(
            UserProfile(id = "user1", name = "Sam", email = "sam@example.com", phone = "7805551234")
        ))
        val viewModel = ProfileViewModel(repository, "user1")
        viewModel.onEmailChange("   ")
        viewModel.onPhoneChange("")
        viewModel.saveProfile()

        TestCase.assertEquals("", repository.getProfile("user1")?.email)
        TestCase.assertEquals("", repository.getProfile("user1")?.phone)
        TestCase.assertTrue(viewModel.saved)
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
        val viewModel = ProfileViewModel(repository, "user1")
        TestCase.assertTrue(viewModel.loadFailed)
        TestCase.assertFalse(viewModel.isLoading)

        failRead = false
        viewModel.loadProfile()
        TestCase.assertFalse(viewModel.loadFailed)
        TestCase.assertEquals("Sam", viewModel.name)
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
        val viewModel = ProfileViewModel(repository, "user1")
        viewModel.onNameChange("Sam Patel")
        viewModel.saveProfile()
        TestCase.assertTrue(viewModel.saveFailed)
        TestCase.assertFalse(viewModel.isSaving)
        TestCase.assertFalse(viewModel.saved)
        TestCase.assertEquals(original, fake.getProfile("user1"))

        failWrite = false
        viewModel.saveProfile()
        TestCase.assertFalse(viewModel.saveFailed)
        TestCase.assertTrue(viewModel.saved)
        TestCase.assertEquals("Sam Patel", fake.getProfile("user1")?.name)
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
        val viewModel = ProfileViewModel(repository, "user1")
        viewModel.onNameChange("Sam Patel")
        viewModel.saveProfile()
        TestCase.assertTrue(viewModel.isSaving)
        viewModel.onNameChange("Another name")
        finishSave.complete(Unit)

        TestCase.assertEquals("Sam Patel", fake.getProfile("user1")?.name)
        TestCase.assertEquals("Another name", viewModel.name)
        TestCase.assertFalse(viewModel.saved)
        TestCase.assertFalse(viewModel.isSaving)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
