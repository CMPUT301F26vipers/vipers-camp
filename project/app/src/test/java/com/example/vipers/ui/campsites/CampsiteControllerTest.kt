/*
 * CampsiteControllerTest.kt
 * Unit tests for CampsiteController (US 02.01.01, 02.01.02a, 02.01.03, T-11), using the fake
 * campsite repository.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.CampsiteRepository
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

    // a saved campsite belonging to the controller's owner
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse",
        maxOccupancy = 8,
        tags = listOf("Cookhouse", "Tennis court"),
    )

    // hinton with every field the form doesn't have filled in, so tests can see they're kept
    private val hintonInFull = hinton.copy(
        pricePerNightCents = 6500,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
        imageId = "photo-1",
        lat = 53.405,
        lng = -117.585,
        ratingSum = 46,
        ratingCount = 10,
    )

    /** A controller for "my-phone" with [campsite] loaded from [campsites] for editing. */
    private suspend fun editForm(
        campsites: CampsiteRepository,
        campsite: Campsite = hintonInFull,
    ): CampsiteController {
        val form = CampsiteController(campsites, ownerId = "my-phone")
        form.loadForEdit(campsite.id)
        return form
    }

    private fun assertFormIsEmpty(form: CampsiteController) {
        assertEquals(FormMode.CREATE, form.mode)
        assertNull(form.loadedCampsite)
        assertEquals("", form.name)
        assertEquals("", form.address)
        assertNull(form.region)
        assertEquals("", form.maxGuests)
        assertEquals("", form.description)
        assertTrue(form.tags.isEmpty())
        assertEquals("", form.rules)
        assertEquals("", form.cancellationPolicy)
    }

    @Test
    fun mode_newController_isCreate() {
        assertEquals(FormMode.CREATE, controller.mode)
    }

    @Test
    fun loadForEdit_owner_prefillsEveryField() = runBlocking {
        val form = CampsiteController(FakeCampsiteRepository(listOf(hinton)), ownerId = "my-phone")
        form.loadForEdit("hinton")

        assertEquals(FormMode.EDIT, form.mode)
        assertEquals(hinton, form.loadedCampsite)
        assertEquals("Hinton Ridge Campground", form.name)
        assertEquals("Hinton, AB", form.address)
        assertEquals(Region.HINTON, form.region)
        assertEquals("8", form.maxGuests)
        assertEquals("Outdoor cookhouse", form.description)
        assertEquals(listOf("Cookhouse", "Tennis court"), form.tags)
        assertFalse(form.notAllowed)
        assertFalse(form.loadFailed)
    }

    @Test
    fun loadForEdit_nonOwner_setsNotAllowedAndLeavesTheFormEmpty() = runBlocking {
        val form = CampsiteController(FakeCampsiteRepository(listOf(hinton)), ownerId = "someone-else")
        form.loadForEdit("hinton")

        assertTrue(form.notAllowed)
        assertFormIsEmpty(form)
    }

    @Test
    fun loadForEdit_missingId_setsNotAllowedAndLeavesTheFormEmpty() = runBlocking {
        val form = CampsiteController(FakeCampsiteRepository(), ownerId = "my-phone")
        form.loadForEdit("nope")

        assertTrue(form.notAllowed)
        assertFormIsEmpty(form)
    }

    @Test
    fun loadForEdit_whenTheRepositoryFails_setsLoadFailedAndLeavesTheFormEmpty() = runBlocking {
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = throw Exception("offline")
        }
        val form = CampsiteController(failing, ownerId = "my-phone")
        form.loadForEdit("hinton")

        assertTrue(form.loadFailed)
        assertFalse(form.notAllowed)
        assertFormIsEmpty(form)
    }

    @Test
    fun publish_editWithEmptyName_givesTheSameErrorsAsCreateAndSavesNothing() = runBlocking {
        fillValidForm()
        controller.onNameChange("")
        controller.publish()

        val campsites = FakeCampsiteRepository(listOf(hintonInFull))
        val form = editForm(campsites)
        form.onNameChange("")
        form.publish()

        assertEquals(mapOf("name" to "Enter a name"), form.errors)
        assertEquals(controller.errors, form.errors)
        assertEquals(hintonInFull, campsites.getCampsite("hinton"))
        assertFalse(form.published)
    }

    @Test
    fun publish_editWithEveryRequiredFieldEmpty_givesTheSameErrorsAsCreate() = runBlocking {
        fillValidForm()
        controller.onNameChange("")
        controller.onAddressChange("")
        controller.onMaxGuestsChange("")
        controller.publish()

        val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        form.onNameChange("")
        form.onAddressChange("")
        form.onMaxGuestsChange("")
        form.publish()

        assertEquals(setOf("name", "address", "maxOccupancy"), form.errors.keys)
        assertEquals(controller.errors, form.errors)
    }

    @Test
    fun publish_validEdit_savesTheFormAndKeepsEverythingElse() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hintonInFull))
        val form = editForm(campsites)
        form.onNameChange("  Hinton Ridge Sites ")
        form.onAddressChange("Jasper, AB")
        form.onRegionChange(Region.JASPER)
        form.onMaxGuestsChange("6")
        form.onDescriptionChange("Tennis court")
        form.removeTag("Cookhouse")
        form.publish()

        val expected = hintonInFull.copy(
            name = "Hinton Ridge Sites", // spaces trimmed
            address = "Jasper, AB",
            region = Region.JASPER,
            maxOccupancy = 6,
            description = "Tennis court",
            tags = listOf("Tennis court"),
        )
        // the whole campsite, so id, ownerId, ratings, rules and cancellationPolicy are all checked
        assertEquals(expected, campsites.getCampsite("hinton"))
        assertEquals(1, campsites.getAllCampsites().size) // updated, not added
        assertTrue(form.published)
        assertTrue(form.errors.isEmpty())
    }

    @Test
    fun publish_validEdit_leavesReservationsUnchanged() = runBlocking {
        val booked = Reservation(
            id = "r1",
            reservationNumber = "R-20417",
            userId = "sam",
            ownerId = "my-phone",
            campsiteId = "hinton",
            campsiteName = "Hinton Ridge Campground",
            checkIn = "2027-05-08",
            checkOut = "2027-05-10",
            groupSize = 4,
            totalPriceCents = 13000,
        )
        val reservations = FakeReservationRepository(listOf(booked))
        val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        form.onNameChange("Hinton Ridge Sites")
        form.onMaxGuestsChange("2")
        form.publish()

        assertTrue(form.published)
        assertEquals(booked, reservations.getReservation("r1"))
        assertEquals(13000L, reservations.getReservation("r1")?.totalPriceCents)
    }

    @Test
    fun saveEdits_ownerChangedSinceLoading_setsNotAllowedAndSavesNothing() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hintonInFull))
        var updated = false
        // the first read is loadForEdit's; by the re-check it belongs to someone else
        var reads = 0
        val changing = object : CampsiteRepository by campsites {
            override suspend fun getCampsite(id: String): Campsite? =
                if (reads++ == 0) hintonInFull else hintonInFull.copy(ownerId = "someone-else")

            override suspend fun updateCampsite(campsite: Campsite) {
                updated = true
            }
        }
        val form = editForm(changing)
        form.saveEdits(form.toCampsite())

        assertTrue(form.notAllowed)
        assertFalse(updated)
        assertFalse(form.published)
    }

    @Test
    fun saveEdits_nothingLoaded_setsNotAllowedAndSavesNothing() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hintonInFull))
        val form = CampsiteController(campsites, ownerId = "my-phone")
        form.saveEdits(Campsite(id = "hinton", name = "Taken over"))

        assertTrue(form.notAllowed)
        assertEquals(hintonInFull, campsites.getCampsite("hinton"))
        assertFalse(form.published)
    }

    @Test
    fun saveEdits_whenTheRepositoryFails_setsSaveFailed() = runBlocking {
        val failing = object : CampsiteRepository by FakeCampsiteRepository(listOf(hintonInFull)) {
            override suspend fun updateCampsite(campsite: Campsite) = throw Exception("rejected")
        }
        val form = editForm(failing)
        form.saveEdits(form.toCampsite())

        assertTrue(form.saveFailed)
        assertFalse(form.published)
        assertFalse(form.isSaving)
    }

    @Test
    fun hasUnsavedChanges_rightAfterLoadForEdit_isFalse() = runBlocking {
        assertFalse(editForm(FakeCampsiteRepository(listOf(hintonInFull))).hasUnsavedChanges)
    }

    @Test
    fun hasUnsavedChanges_afterChangingAnyOneField_isTrue() = runBlocking {
        val changes = listOf<(CampsiteController) -> Unit>(
            { it.onNameChange("Hinton Ridge Sites") },
            { it.onAddressChange("Jasper, AB") },
            { it.onRegionChange(Region.JASPER) },
            { it.onMaxGuestsChange("6") },
            { it.onDescriptionChange("Tennis court") },
            { it.removeTag("Cookhouse") },
        )
        for (change in changes) {
            val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
            change(form)
            assertTrue(form.hasUnsavedChanges)
        }
    }

    @Test
    fun hasUnsavedChanges_afterChangingAFieldBack_isFalse() = runBlocking {
        val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        form.onNameChange("Hinton Ridge Sites")
        form.onNameChange(hintonInFull.name)

        assertFalse(form.hasUnsavedChanges)
    }

    @Test
    fun hasUnsavedChanges_afterSavingAnEdit_isFalse() = runBlocking {
        val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        form.onNameChange("Hinton Ridge Sites")
        form.publish()

        assertFalse(form.hasUnsavedChanges)
    }

    @Test
    fun publish_withRulesAndCancellationPolicy_savesBothTrimmed() = runBlocking {
        fillValidForm()
        controller.onRulesChange("  Quiet hours after 10 PM ")
        controller.onCancellationPolicyChange(" Free up to 7 days before  ")
        controller.publish()

        val saved = repo.getCampsitesByOwner("my-phone").single()
        assertEquals("Quiet hours after 10 PM", saved.rules)
        assertEquals("Free up to 7 days before", saved.cancellationPolicy)
        assertTrue(controller.published)
        assertTrue(controller.errors.isEmpty())
    }

    @Test
    fun publish_withoutRulesOrCancellationPolicy_succeedsWithBothEmpty() = runBlocking {
        fillValidForm()
        controller.publish()

        val saved = repo.getCampsitesByOwner("my-phone").single()
        assertEquals("", saved.rules)
        assertEquals("", saved.cancellationPolicy)
        assertTrue(controller.published)
        assertTrue(controller.errors.isEmpty())
    }

    @Test
    fun loadForEdit_owner_prefillsRulesAndCancellationPolicy() = runBlocking {
        val form = editForm(FakeCampsiteRepository(listOf(hintonInFull)))

        assertEquals("Quiet hours after 10 PM", form.rules)
        assertEquals("Free up to 7 days before", form.cancellationPolicy)
    }

    @Test
    fun publish_editToRules_savesTheNewRulesAndKeepsEverythingElse() = runBlocking {
        val campsites = FakeCampsiteRepository(listOf(hintonInFull))
        val form = editForm(campsites)
        form.onRulesChange(" No pets ")
        form.publish()

        assertEquals(hintonInFull.copy(rules = "No pets"), campsites.getCampsite("hinton"))
        assertTrue(form.published)
    }

    @Test
    fun hasUnsavedChanges_newFormAfterTypingRulesOrCancellationPolicy_isTrue() {
        val rulesOnly = CampsiteController(FakeCampsiteRepository(), ownerId = "my-phone")
        rulesOnly.onRulesChange("Quiet hours after 10 PM")
        assertTrue(rulesOnly.hasUnsavedChanges)

        val policyOnly = CampsiteController(FakeCampsiteRepository(), ownerId = "my-phone")
        policyOnly.onCancellationPolicyChange("Free up to 7 days before")
        assertTrue(policyOnly.hasUnsavedChanges)
    }

    @Test
    fun hasUnsavedChanges_editAfterTypingRulesOrCancellationPolicy_isTrue() = runBlocking {
        val rulesEdit = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        rulesEdit.onRulesChange("No pets")
        assertTrue(rulesEdit.hasUnsavedChanges)

        val policyEdit = editForm(FakeCampsiteRepository(listOf(hintonInFull)))
        policyEdit.onCancellationPolicyChange("No refunds")
        assertTrue(policyEdit.hasUnsavedChanges)
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
