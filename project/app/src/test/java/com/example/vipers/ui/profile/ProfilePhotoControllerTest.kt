/*
 * ProfilePhotoControllerTest.kt
 * Unit tests for the profile photo in ProfileController (US 01.08.02): adding, replacing and
 * removing it, deleting the old image, and what happens when a step fails. Uses the fake profile
 * and image repositories and a fake photo reader.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.PhotoReader
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoControllerTest {

    private val now = 1_809_000_000_000 // April 2027
    private val sam = UserProfile(id = "my-phone", name = "Sam Patel", email = "sam@example.com")
    private val oldPhoto = ImageAsset.fromJpeg(
        FakePhotoReader.SAMPLE_JPEG, "my-phone", ImageAsset.OWNER_PROFILE, "my-phone",
        "Sam Patel", now - 1000,
    ).copy(id = "old-photo")

    private val profiles = FakeProfileRepository(listOf(sam))
    private val images = FakeImageRepository()
    private val reader = FakePhotoReader()

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun controller(
        profileRepository: ProfileRepository = profiles,
        imageRepository: ImageRepository = images,
        photoReader: PhotoReader = reader,
    ) = ProfileController(profileRepository, "my-phone", imageRepository, photoReader)

    // Sam with a saved photo, in both repositories
    private suspend fun samWithAPhoto(): ProfileController {
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        return controller()
    }

    @Test
    fun loadingTheProfile_showsItsPhoto() = runBlocking {
        val controller = samWithAPhoto()

        assertEquals("old-photo", controller.imageId)
    }

    @Test
    fun loadingAProfileWithNoPhoto_hasNoImageId() {
        val controller = controller()

        assertNull(controller.imageId)
        assertTrue(controller.canChangePhoto)
    }

    @Test
    fun changePhoto_savesItAsAProfileImageTheAdminCanFind() = runBlocking {
        val controller = controller()

        controller.changePhoto("content://photos/1", now)

        val saved = images.getAllImages().single()
        assertEquals(ImageAsset.OWNER_PROFILE, saved.ownerType)
        assertEquals("my-phone", saved.ownerId)
        assertEquals("my-phone", saved.uploaderId)
        assertEquals("Sam Patel", saved.label)
        assertEquals(now, saved.createdAt)
        assertArrayEquals(FakePhotoReader.SAMPLE_JPEG, saved.jpegBytes())
        assertEquals(saved.id, profiles.getProfile("my-phone")?.imageId)
        assertEquals(saved.id, controller.imageId)
        assertEquals(listOf("content://photos/1"), reader.reads)
        assertNull(controller.photoError)
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_replacingAPhoto_deletesTheOldOne() = runBlocking {
        val controller = samWithAPhoto()

        controller.changePhoto("content://photos/2", now)

        val newId = controller.imageId
        assertNotNull(newId)
        assertNotEquals("old-photo", newId)
        assertEquals(newId, profiles.getProfile("my-phone")?.imageId)
        assertNotNull(images.getImage(newId!!))
        assertEquals(listOf("old-photo"), images.deletedIds)
        assertNull(images.getImage("old-photo"))
    }

    @Test
    fun changePhoto_keepsChangesSavedFromAnotherScreen() = runBlocking {
        val controller = controller()
        // e.g. Settings turned notifications off after Profile loaded
        profiles.saveProfile(sam.copy(name = "Sam P.", notificationsEnabled = false))

        controller.changePhoto("content://photos/1", now)

        val saved = profiles.getProfile("my-phone")!!
        assertEquals("Sam P.", saved.name)
        assertFalse(saved.notificationsEnabled)
        assertEquals("Sam P.", images.getAllImages().single().label)
    }

    @Test
    fun changePhoto_doesNotSaveUnsavedFormEdits() = runBlocking {
        val controller = controller()
        controller.onNameChange("Not saved yet")

        controller.changePhoto("content://photos/1", now)

        assertEquals("Sam Patel", profiles.getProfile("my-phone")?.name)
        assertEquals("Not saved yet", controller.name)
    }

    @Test
    fun changePhoto_withAPhotoThatCantBeRead_keepsTheOldPhoto() = runBlocking {
        val controller = samWithAPhoto()
        reader.fails = true

        controller.changePhoto("content://photos/broken", now)

        assertEquals("Couldn't use that photo. Try another one.", controller.photoError)
        assertEquals("old-photo", controller.imageId)
        assertEquals("old-photo", profiles.getProfile("my-phone")?.imageId)
        assertEquals(listOf(oldPhoto.id), images.getAllImages().map { it.id })
        assertTrue(images.deletedIds.isEmpty())
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_thatIsTooBig_isNotSaved() = runBlocking {
        val tooBig = ByteArray(ImageAsset.MAX_JPEG_BYTES + 100_000)
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        val controller = controller(photoReader = FakePhotoReader(tooBig))

        controller.changePhoto("content://photos/huge", now)

        assertEquals("This photo is too big. Try another one.", controller.photoError)
        assertEquals(listOf("old-photo"), images.getAllImages().map { it.id })
        assertEquals("old-photo", controller.imageId)
        assertEquals("old-photo", profiles.getProfile("my-phone")?.imageId)
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_whenTheUploadFails_keepsTheOldPhoto() = runBlocking {
        val controller = samWithAPhoto()
        images.failAdds = true

        controller.changePhoto("content://photos/2", now)

        assertEquals("Couldn't upload the photo. Try again.", controller.photoError)
        assertEquals("old-photo", controller.imageId)
        assertEquals("old-photo", profiles.getProfile("my-phone")?.imageId)
        assertTrue(images.deletedIds.isEmpty())
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_whenTheProfileCantBeSaved_deletesTheNewImageAgain() = runBlocking {
        images.addImage(oldPhoto)
        val failing = object : ProfileRepository by
            FakeProfileRepository(listOf(sam.copy(imageId = "old-photo"))) {
            override suspend fun saveProfile(profile: UserProfile) = throw IOException("offline")
        }
        val controller = controller(profileRepository = failing)

        controller.changePhoto("content://photos/2", now)

        assertEquals("Couldn't save the photo. Try again.", controller.photoError)
        assertEquals("old-photo", controller.imageId)
        assertEquals("old-photo", failing.getProfile("my-phone")?.imageId)
        // only the old photo is left, so a retry doesn't leave an image nothing points to
        assertEquals(listOf("old-photo"), images.getAllImages().map { it.id })
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_whenDeletingTheOldImageFails_stillChangesThePhoto() = runBlocking {
        val cantDelete = object : ImageRepository by images {
            override suspend fun deleteImage(id: String) = throw IOException("offline")
        }
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        val controller = controller(imageRepository = cantDelete)

        controller.changePhoto("content://photos/2", now)

        assertNull(controller.photoError)
        val newId = controller.imageId
        assertNotNull(newId)
        assertNotEquals("old-photo", newId)
        assertEquals(newId, profiles.getProfile("my-phone")?.imageId)
        assertNotNull(images.getImage(newId!!))
        // the old one is left for an admin to remove
        assertNotNull(images.getImage("old-photo"))
    }

    // a profile repository whose profile can be made to disappear, e.g. deleted on another phone
    private class VanishingProfiles(private val profiles: ProfileRepository) :
        ProfileRepository by profiles {
        var gone = false

        override suspend fun getProfile(id: String) = if (gone) null else profiles.getProfile(id)
    }

    @Test
    fun changePhoto_whenTheProfileIsGone_saysItCantSave() = runBlocking {
        val vanishing = VanishingProfiles(profiles)
        val controller = controller(profileRepository = vanishing)
        vanishing.gone = true

        controller.changePhoto("content://photos/1", now)

        assertEquals("Couldn't save the photo. Try again.", controller.photoError)
        assertTrue(images.getAllImages().isEmpty())
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun changePhoto_whenStopped_rethrowsTheCancellation() = runBlocking {
        val stopped = object : PhotoReader {
            override suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray =
                throw CancellationException("screen closed")
        }
        val controller = controller(photoReader = stopped)

        try {
            controller.changePhoto("content://photos/1", now)
            fail("expected the cancellation to be rethrown")
        } catch (e: CancellationException) {
            // expected
        }
        assertNull(controller.photoError)
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun aNewPhotoChange_clearsTheOldError() = runBlocking {
        val controller = controller()
        reader.fails = true
        controller.changePhoto("content://photos/broken", now)
        assertNotNull(controller.photoError)
        reader.fails = false

        controller.changePhoto("content://photos/1", now)

        assertNull(controller.photoError)
        assertNotNull(controller.imageId)
    }

    @Test
    fun clearPhotoError_removesTheMessage() = runBlocking {
        val controller = controller()
        reader.fails = true
        controller.changePhoto("content://photos/broken", now)
        assertNotNull(controller.photoError)

        controller.clearPhotoError()

        assertNull(controller.photoError)
    }

    @Test
    fun deletePhoto_removesItFromTheProfileAndDeletesTheImage() = runBlocking {
        val controller = samWithAPhoto()

        controller.deletePhoto()

        assertNull(controller.imageId)
        assertNull(profiles.getProfile("my-phone")?.imageId)
        assertEquals(listOf("old-photo"), images.deletedIds)
        assertNull(controller.photoError)
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun deletePhoto_keepsTheRestOfTheProfile() = runBlocking {
        val controller = samWithAPhoto()

        controller.deletePhoto()

        assertEquals(sam, profiles.getProfile("my-phone"))
    }

    @Test
    fun deletePhoto_whenTheProfileCantBeSaved_keepsThePhoto() = runBlocking {
        images.addImage(oldPhoto)
        val failing = object : ProfileRepository by
            FakeProfileRepository(listOf(sam.copy(imageId = "old-photo"))) {
            override suspend fun saveProfile(profile: UserProfile) = throw IOException("offline")
        }
        val controller = controller(profileRepository = failing)

        controller.deletePhoto()

        assertEquals("Couldn't remove the photo. Try again.", controller.photoError)
        assertEquals("old-photo", controller.imageId)
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun deletePhoto_whenDeletingTheImageFails_stillRemovesIt() = runBlocking {
        val cantDelete = object : ImageRepository by images {
            override suspend fun deleteImage(id: String) = throw IOException("offline")
        }
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        val controller = controller(imageRepository = cantDelete)

        controller.deletePhoto()

        assertNull(controller.photoError)
        assertNull(controller.imageId)
        assertNull(profiles.getProfile("my-phone")?.imageId)
    }

    @Test
    fun deletePhoto_whenTheProfileIsGone_saysItCantRemove() = runBlocking {
        samWithAPhoto()
        val vanishing = VanishingProfiles(profiles)
        val controller = controller(profileRepository = vanishing)
        vanishing.gone = true

        controller.deletePhoto()

        assertEquals("Couldn't remove the photo. Try again.", controller.photoError)
        assertEquals("old-photo", controller.imageId)
        assertTrue(images.deletedIds.isEmpty())
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun deletePhoto_withNoPhoto_savesNothingAndDeletesNothing() = runBlocking {
        var saves = 0
        val counting = object : ProfileRepository by profiles {
            override suspend fun saveProfile(profile: UserProfile) {
                saves++
                profiles.saveProfile(profile)
            }
        }
        val controller = controller(profileRepository = counting)

        controller.deletePhoto()

        assertEquals(0, saves)
        assertTrue(images.deletedIds.isEmpty())
        assertNull(controller.imageId)
        assertNull(controller.photoError)
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun deletePhoto_whenStopped_rethrowsTheCancellation() = runBlocking {
        samWithAPhoto()
        var stopped = false
        val stopping = object : ProfileRepository by profiles {
            override suspend fun getProfile(id: String): UserProfile? {
                if (stopped) throw CancellationException("screen closed")
                return profiles.getProfile(id)
            }
        }
        val controller = controller(profileRepository = stopping)
        stopped = true

        try {
            controller.deletePhoto()
            fail("expected the cancellation to be rethrown")
        } catch (e: CancellationException) {
            // expected
        }
        assertNull(controller.photoError)
        assertFalse(controller.isSavingPhoto)
        assertEquals("old-photo", profiles.getProfile("my-phone")?.imageId)
    }

    @Test
    fun removePhoto_removesThePhotoStraightAway() = runBlocking {
        val controller = samWithAPhoto()

        controller.removePhoto()

        assertNull(controller.imageId)
        assertFalse(controller.isSavingPhoto)
        assertEquals(listOf("old-photo"), images.deletedIds)
    }

    @Test
    fun changePhoto_stoppedWhileSavingTheProfile_stillDeletesTheNewImage() = runBlocking {
        images.addImage(oldPhoto)
        val stopping = object : ProfileRepository by
            FakeProfileRepository(listOf(sam.copy(imageId = "old-photo"))) {
            override suspend fun saveProfile(profile: UserProfile) =
                throw CancellationException("screen closed")
        }
        val controller = controller(profileRepository = stopping)

        try {
            controller.changePhoto("content://photos/2", now)
            fail("expected the cancellation to be rethrown")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(listOf("old-photo"), images.getAllImages().map { it.id })
        assertFalse(controller.isSavingPhoto)
    }

    @Test
    fun whileAPhotoIsSaving_photoButtonsAndSaveDoNothing() = runBlocking {
        val photoReady = CompletableDeferred<ByteArray>()
        val reads = mutableListOf<String>()
        val slowReader = object : PhotoReader {
            override suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray {
                reads.add(uri)
                return photoReady.await()
            }
        }
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        val controller = controller(photoReader = slowReader)

        controller.choosePhoto("content://photos/1")
        assertTrue(controller.isSavingPhoto)
        assertFalse(controller.canChangePhoto)

        controller.choosePhoto("content://photos/2")
        controller.removePhoto()
        controller.onNameChange("Sam Lee")
        controller.saveProfile()
        assertEquals(listOf("content://photos/1"), reads)
        assertEquals("Sam Patel", profiles.getProfile("my-phone")?.name)

        photoReady.complete(FakePhotoReader.SAMPLE_JPEG)
        assertFalse(controller.isSavingPhoto)
        assertEquals(1, images.getAllImages().size)
        // deleted once, by the replace, not by the ignored Remove
        assertEquals(listOf("old-photo"), images.deletedIds)
        assertEquals(controller.imageId, profiles.getProfile("my-phone")?.imageId)
        assertTrue(controller.canChangePhoto)
    }

    @Test
    fun whileTheFormIsSaving_photoButtonsDoNothing() = runBlocking {
        val saveDone = CompletableDeferred<Unit>()
        val slowSave = object : ProfileRepository by profiles {
            override suspend fun saveProfile(profile: UserProfile) {
                saveDone.await()
                profiles.saveProfile(profile)
            }
        }
        images.addImage(oldPhoto)
        profiles.saveProfile(sam.copy(imageId = "old-photo"))
        val controller = controller(profileRepository = slowSave)
        controller.onNameChange("Sam Lee")
        controller.saveProfile()
        assertTrue(controller.isSaving)
        assertFalse(controller.canChangePhoto)

        controller.choosePhoto("content://photos/1")
        controller.removePhoto()
        assertTrue(reader.reads.isEmpty())

        saveDone.complete(Unit)
        assertFalse(controller.isSaving)
        assertTrue(controller.canChangePhoto)
        assertEquals("Sam Lee", profiles.getProfile("my-phone")?.name)
        assertEquals("old-photo", profiles.getProfile("my-phone")?.imageId)
        assertEquals("old-photo", controller.imageId)
        assertTrue(images.deletedIds.isEmpty())
    }
}
