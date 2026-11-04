/*
 * CampsiteControllerTest.kt
 * Unit tests for CampsiteController (US 02.01.01, 02.01.02a), using the fake campsite repository.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CampsiteControllerTest {

    private val repo = FakeCampsiteRepository()
    private val controller = CampsiteController(repo, ownerId = "my-phone")

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillValidForm() {
        controller.onNameChange("  Hinton Ridge Campground ")
        controller.onAddressChange("Hinton, AB")
        controller.onRegionChange(Region.HINTON)
        controller.onMaxGuestsChange("6")
        controller.onDescriptionChange("Outdoor cookhouse")
        controller.onTagInputChange("Cookhouse")
        controller.addTag()
    }

    @Test
    fun publish_withEmptyForm_showsAnErrorForEachRequiredFieldAndSavesNothing() = runBlocking {
        controller.publish()

        assertEquals(setOf("name", "address", "region", "maxOccupancy"), controller.errors.keys)
        assertEquals("Enter max guests", controller.errors["maxOccupancy"])
        assertTrue(repo.getAllCampsites().isEmpty())
        assertFalse(controller.published)
    }

    @Test
    fun publish_withValidForm_savesTheCampsiteUnderTheOwner() = runBlocking {
        fillValidForm()
        controller.publish()

        val saved = repo.getCampsitesByOwner("my-phone")
        assertEquals(1, saved.size)
        assertEquals("Hinton Ridge Campground", saved[0].name) // spaces trimmed
        assertEquals(Region.HINTON, saved[0].region)
        assertEquals(6, saved[0].maxOccupancy)
        assertEquals(listOf("Cookhouse"), saved[0].tags)
        assertTrue(controller.published)
        assertTrue(controller.errors.isEmpty())
    }

    @Test
    fun publish_withMaxGuestsNotAWholeNumber_showsAnError() = runBlocking {
        fillValidForm()
        controller.onMaxGuestsChange("2.5")
        controller.publish()

        assertEquals("Enter a whole number", controller.errors["maxOccupancy"])
        assertTrue(repo.getAllCampsites().isEmpty())
    }

    @Test
    fun publish_withZeroMaxGuests_showsAnError() = runBlocking {
        fillValidForm()
        controller.onMaxGuestsChange("0")
        controller.publish()

        assertEquals("Must be at least 1", controller.errors["maxOccupancy"])
        assertTrue(repo.getAllCampsites().isEmpty())
    }

    @Test
    fun typingInAField_clearsItsError() {
        controller.publish()
        controller.onNameChange("Bow River Sites")

        assertFalse("name" in controller.errors)
        assertTrue("address" in controller.errors)
    }

    @Test
    fun addTag_skipsBlankAndDuplicateTags() {
        controller.onTagInputChange("Cookhouse")
        controller.addTag()
        controller.onTagInputChange("cookhouse")
        controller.addTag()
        controller.onTagInputChange("   ")
        controller.addTag()

        assertEquals(listOf("Cookhouse"), controller.tags)
        assertEquals("", controller.tagInput)
    }

    @Test
    fun removeTag_takesTheTagOff() {
        controller.onTagInputChange("Cookhouse")
        controller.addTag()
        controller.onTagInputChange("Tennis court")
        controller.addTag()
        controller.removeTag("Cookhouse")

        assertEquals(listOf("Tennis court"), controller.tags)
    }

    @Test
    fun hasUnsavedChanges_onlyOnceSomethingIsFilledAndUntilPublished() {
        assertFalse(controller.hasUnsavedChanges)
        fillValidForm()
        assertTrue(controller.hasUnsavedChanges)
        controller.publish()
        assertFalse(controller.hasUnsavedChanges)
    }

    @Test
    fun save_whenTheRepositoryFails_setsSaveFailed() = runBlocking {
        // a repository that always fails, e.g. Firestore rejecting the write
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun addCampsite(campsite: Campsite): String = throw Exception("offline")
        }
        val offlineController = CampsiteController(failing, ownerId = "my-phone")
        offlineController.save(Campsite(name = "Bow River Sites"))

        assertTrue(offlineController.saveFailed)
        assertFalse(offlineController.published)
        assertFalse(offlineController.isSaving)
    }
}
