/*
 * ProfileControllerTest.kt
 * Unit tests for ProfileController (T-01, US 01.09.01), using the fake profile repository.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
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

class ProfileControllerTest {

    private val repo = FakeProfileRepository()
    private val controller = ProfileController(repo, deviceId = "my-phone")

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
}
