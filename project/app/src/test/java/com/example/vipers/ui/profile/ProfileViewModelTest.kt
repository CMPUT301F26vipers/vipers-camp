
/*
 * ProfileViewModelTest.kt
 * Tests loading, editing and saving profile information.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
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
            isAdmin = true,
            notificationsEnabled = false,
            createdAt = 1000L
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        viewModel.onNameChange("Sam Patel")
        viewModel.saveProfile()

        val saved = fakeRepo.getProfile("user1")

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
