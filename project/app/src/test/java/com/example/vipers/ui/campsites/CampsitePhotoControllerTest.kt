/*
 * CampsitePhotoControllerTest.kt
 * Unit tests for the promo photo on the campsite form in CampsiteController (US 02.01.02b):
 * picking, removing, and saving the photo with a new or edited campsite, and not publishing while
 * a picked photo is still being got ready. Uses the fake campsite
 * and image repositories and a fake photo reader.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakePlaceFinder
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.PhotoReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsitePhotoControllerTest {

    private val now = 1_809_000_000_000 // May 2027
    private val photo = FakePhotoReader.SAMPLE_JPEG
    private val savedPhoto = ImageAsset.fromJpeg(
        photo, "my-phone", ImageAsset.OWNER_CAMPSITE, "hinton", "Hinton Ridge Campground", now,
    ).copy(id = "photo-1")
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 6500,
        imageId = "photo-1",
    )

    private val campsites = FakeCampsiteRepository(listOf(hinton))
    private val images = FakeImageRepository(listOf(savedPhoto))
    private val reader = FakePhotoReader()
    private val form = CampsiteController(
        campsites,
        ownerId = "my-phone",
        placeFinder = FakePlaceFinder(),
        imageRepository = images,
        photoReader = reader,
    )

    private fun fillValidForm() {
        form.onNameChange("Lakeside Sites")
        form.onAddressChange("Hinton, AB")
        form.onRegionChange(Region.HINTON)
        form.onMaxGuestsChange("6")
        form.onPriceChange("65")
    }

    @Test
    fun newForm_hasNoPhoto() {
        assertFalse(form.hasPhoto)
        assertNull(form.newPhoto)
        assertNull(form.imageId)
    }

    @Test
    fun fetchPhoto_readsThePickedPhotoAsASmallJpeg() = runBlocking {
        form.fetchPhoto("content://photos/42")

        assertArrayEquals(photo, form.newPhoto)
        assertTrue(form.hasPhoto)
        assertEquals(listOf("content://photos/42"), reader.reads)
        assertNull(form.photoError)
        assertFalse(form.isReadingPhoto)
    }

    @Test
    fun fetchPhoto_unreadable_showsAnErrorAndKeepsThePhotoItHad() = runBlocking {
        form.fetchPhoto("content://photos/42")
        reader.fails = true

        form.fetchPhoto("content://photos/broken")

        assertEquals("Couldn't use that photo. Try another one.", form.photoError)
        assertArrayEquals(photo, form.newPhoto)
        assertFalse(form.isReadingPhoto)
    }

    @Test
    fun removePhoto_clearsThePickedPhotoAndItsError() = runBlocking {
        reader.fails = true
        form.fetchPhoto("content://photos/broken")
        reader.fails = false
        form.fetchPhoto("content://photos/42")

        form.removePhoto()

        assertFalse(form.hasPhoto)
        assertNull(form.photoError)
    }

    @Test
    fun hasUnsavedChanges_pickedPhotoOnAnEmptyForm_isTrue() = runBlocking {
        form.fetchPhoto("content://photos/42")

        assertTrue(form.hasUnsavedChanges)
    }

    @Test
    fun save_withPhoto_savesTheImageForTheNewCampsiteFirst() = runBlocking {
        fillValidForm()
        form.fetchPhoto("content://photos/42")

        form.save(form.toCampsite(), now)

        assertTrue(form.published)
        val campsite = campsites.getAllCampsites().single { it.name == "Lakeside Sites" }
        val image = images.getImage(campsite.imageId!!)!!
        assertArrayEquals(photo, image.jpegBytes())
        assertEquals(campsite.id, image.ownerId)
        assertEquals(ImageAsset.OWNER_CAMPSITE, image.ownerType)
        assertEquals("my-phone", image.uploaderId)
        assertEquals("Lakeside Sites", image.label)
        assertEquals(now, image.createdAt)
    }

    @Test
    fun save_withoutPhoto_savesNoImage() = runBlocking {
        fillValidForm()

        form.save(form.toCampsite(), now)

        assertTrue(form.published)
        assertNull(campsites.getAllCampsites().single { it.name == "Lakeside Sites" }.imageId)
        assertEquals(1, images.getAllImages().size) // only the photo that was there before
    }

    @Test
    fun save_photoUploadFails_showsAnErrorAndSavesNothing() = runBlocking {
        fillValidForm()
        form.fetchPhoto("content://photos/42")
        images.failAdds = true

        form.save(form.toCampsite(), now)

        assertEquals(
            "Couldn't upload the photo. Try again, or remove it to save without one.",
            form.photoError,
        )
        assertFalse(form.published)
        assertFalse(form.isSaving)
        assertEquals(listOf("hinton"), campsites.getAllCampsites().map { it.id })
        // what the owner typed is kept, so they can try again
        assertEquals("Lakeside Sites", form.name)
        assertNotNull(form.newPhoto)
    }

    @Test
    fun save_photoTooBig_showsAnErrorAndSavesNothing() = runBlocking {
        val bigReader = FakePhotoReader(jpeg = ByteArray(ImageAsset.MAX_DATA_LENGTH))
        val bigForm = CampsiteController(
            campsites,
            ownerId = "my-phone",
            placeFinder = FakePlaceFinder(),
            imageRepository = images,
            photoReader = bigReader,
        )
        bigForm.onNameChange("Lakeside Sites")
        bigForm.fetchPhoto("content://photos/huge")

        bigForm.save(bigForm.toCampsite(), now)

        assertEquals("This photo is too big. Try another one.", bigForm.photoError)
        assertFalse(bigForm.published)
        assertEquals(1, campsites.getAllCampsites().size)
    }

    @Test
    fun loadForEdit_showsTheSavedPhoto() = runBlocking {
        form.loadForEdit("hinton")

        assertEquals("photo-1", form.imageId)
        assertNull(form.newPhoto)
        assertTrue(form.hasPhoto)
        assertFalse(form.hasUnsavedChanges)
    }

    @Test
    fun saveEdits_photoNotChanged_keepsItAndDeletesNothing() = runBlocking {
        form.loadForEdit("hinton")
        form.onNameChange("Hinton Ridge Sites")

        form.saveEdits(form.toCampsite(), now)

        assertEquals("photo-1", campsites.getCampsite("hinton")?.imageId)
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun saveEdits_newPhoto_replacesTheOldOneAndDeletesIt() = runBlocking {
        form.loadForEdit("hinton")
        form.fetchPhoto("content://photos/43")
        assertTrue(form.hasUnsavedChanges)

        form.saveEdits(form.toCampsite(), now)

        val newId = campsites.getCampsite("hinton")?.imageId
        assertNotNull(newId)
        assertTrue(newId != "photo-1")
        assertEquals("hinton", images.getImage(newId!!)?.ownerId)
        assertEquals(listOf("photo-1"), images.deletedIds)
        assertTrue(form.published)
    }

    @Test
    fun saveEdits_photoRemoved_savesWithoutOneAndDeletesIt() = runBlocking {
        form.loadForEdit("hinton")
        form.removePhoto()
        assertTrue(form.hasUnsavedChanges)

        form.saveEdits(form.toCampsite(), now)

        assertNull(campsites.getCampsite("hinton")?.imageId)
        assertEquals(listOf("photo-1"), images.deletedIds)
    }

    @Test
    fun saveEdits_newPhotoUploadFails_keepsTheOldPhoto() = runBlocking {
        form.loadForEdit("hinton")
        form.fetchPhoto("content://photos/43")
        images.failAdds = true

        form.saveEdits(form.toCampsite(), now)

        assertFalse(form.published)
        assertEquals("photo-1", campsites.getCampsite("hinton")?.imageId)
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun canPublish_whileThePhotoIsBeingRead_isFalseAndPublishSavesNothing() = runBlocking {
        val photoReady = CompletableDeferred<ByteArray>()
        val slowReader = object : PhotoReader {
            override suspend fun readJpeg(uri: String, maxBytes: Int) = photoReady.await()
        }
        val slowForm = CampsiteController(
            campsites,
            ownerId = "my-phone",
            placeFinder = FakePlaceFinder(),
            imageRepository = images,
            photoReader = slowReader,
        )
        slowForm.onNameChange("Lakeside Sites")
        slowForm.onAddressChange("Hinton, AB")
        slowForm.onRegionChange(Region.HINTON)
        slowForm.onMaxGuestsChange("6")
        slowForm.onPriceChange("65")
        val reading = launch { slowForm.fetchPhoto("content://photos/42") }
        yield() // fetchPhoto is now waiting for the photo

        assertTrue(slowForm.isReadingPhoto)
        assertFalse(slowForm.canPublish)
        slowForm.publish() // tapped too early: does nothing

        assertFalse(slowForm.isSaving)
        assertTrue(slowForm.errors.isEmpty())
        assertEquals(1, campsites.getAllCampsites().size)
        photoReady.complete(photo)
        reading.join()
        assertTrue(slowForm.canPublish)
    }

    @Test
    fun save_campsiteCantBeSaved_deletesTheJustSavedPhotoAgain() = runBlocking {
        val failing = object : CampsiteRepository by campsites {
            override suspend fun addCampsite(campsite: Campsite): String =
                throw Exception("offline")
        }
        val failingForm = CampsiteController(
            failing,
            ownerId = "my-phone",
            placeFinder = FakePlaceFinder(),
            imageRepository = images,
            photoReader = reader,
        )
        failingForm.onNameChange("Lakeside Sites")
        failingForm.fetchPhoto("content://photos/42")

        failingForm.save(failingForm.toCampsite(), now)

        assertTrue(failingForm.saveFailed)
        assertEquals(1, images.deletedIds.size)
        // only the photo that was there before is left, so a retry doesn't leave an extra one
        assertEquals(listOf("photo-1"), images.getAllImages().map { it.id })
    }

    @Test
    fun saveEdits_updateFails_deletesTheNewPhotoAndKeepsTheOldOne() = runBlocking {
        val failing = object : CampsiteRepository by campsites {
            override suspend fun updateCampsite(campsite: Campsite) = throw Exception("offline")
        }
        val failingForm = CampsiteController(
            failing,
            ownerId = "my-phone",
            placeFinder = FakePlaceFinder(),
            imageRepository = images,
            photoReader = reader,
        )
        failingForm.loadForEdit("hinton")
        failingForm.fetchPhoto("content://photos/43")

        failingForm.saveEdits(failingForm.toCampsite(), now)

        assertTrue(failingForm.saveFailed)
        assertEquals("photo-1", campsites.getCampsite("hinton")?.imageId)
        assertEquals(listOf("photo-1"), images.getAllImages().map { it.id })
        assertFalse(images.deletedIds.contains("photo-1"))
    }
}
