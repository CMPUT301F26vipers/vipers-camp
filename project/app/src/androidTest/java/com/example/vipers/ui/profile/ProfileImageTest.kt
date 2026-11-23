/*
 * ProfileImageTest.kt
 * Intent tests for US 01.08.02: adding, replacing and removing the profile photo on the Profile
 * screen (mockup E18), one test per acceptance criterion, plus a photo that can't be used, a
 * failed remove, saving in progress, and keeping the other fields. Uses fake repositories and a
 * fake photo reader, so it doesn't touch Firestore or need the photo picker.
 * Known issues: the system photo picker can't be driven from a test without another library, so
 * "Change photo opens the picker" is only checked as the button being there, and tests hand the
 * controller a picked photo directly, as the picker would. The admin image browser
 * (US 03.06.01) isn't on main yet, so one test checks the image is in the list that browser shows.
 */
package com.example.vipers.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.PhotoReader
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ProfileImageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sam = UserProfile(
        id = "test-device",
        name = "Sam Patel",
        email = "sam@example.com",
        phone = "780 555 0100",
    )
    private val oldPhoto = ImageAsset.fromJpeg(
        FakePhotoReader.SAMPLE_JPEG, "test-device", ImageAsset.OWNER_PROFILE, "test-device",
        "Sam Patel", 1_809_000_000_000,
    ).copy(id = "old-photo")

    private val profiles = FakeProfileRepository(listOf(sam))
    private val images = FakeImageRepository()
    private val reader = FakePhotoReader()

    @Before
    fun useFakeRepositories() {
        // the photo box reads images through the app's container, so give it the fakes
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = images,
        )
    }

    /** Opens Profile for Sam, with [imageId] as Sam's saved photo, and returns its controller. */
    private fun openProfile(
        imageId: String? = null,
        profileRepository: ProfileRepository = profiles,
        photoReader: PhotoReader = reader,
    ): ProfileController {
        if (imageId != null) {
            runBlocking {
                images.addImage(oldPhoto)
                profiles.saveProfile(sam.copy(imageId = imageId))
            }
        }
        val controller =
            ProfileController(profileRepository, "test-device", images, photoReader)
        composeRule.setContent {
            VipersTheme {
                ProfileScreen(onOpenSettings = {}, onOpenPolicy = {}, controller = controller)
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("profile_photo").fetchSemanticsNodes().isNotEmpty()
        }
        return controller
    }

    // what the photo picker does once the user picks a photo
    private fun pickPhoto(controller: ProfileController, uri: String) {
        composeRule.runOnIdle { controller.choosePhoto(uri) }
        composeRule.waitUntil(5_000) { !controller.isSavingPhoto }
    }

    private fun photoIsShown(): Boolean = composeRule
        .onAllNodesWithContentDescription("Profile photo")
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun waitForPhoto(shown: Boolean) {
        composeRule.waitUntil(5_000) { photoIsShown() == shown }
    }

    // the default avatar: the person icon SavedPhoto draws when there's no photo
    private fun defaultAvatarIsShown(): Boolean = composeRule
        .onAllNodesWithTag("photo_placeholder", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun removeButtonIsShown(): Boolean = composeRule
        .onAllNodesWithTag("button_remove_photo")
        .fetchSemanticsNodes()
        .isNotEmpty()

    @Test
    fun profileScreen_hasChangePhoto() {
        openProfile()

        composeRule.onNodeWithText("Change photo").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("button_change_photo").assertIsDisplayed()
    }

    @Test
    fun withNoPhoto_showsTheDefaultAvatar() {
        openProfile()

        composeRule.onNodeWithTag("profile_photo").assertIsDisplayed()
        assertTrue(defaultAvatarIsShown())
        assertFalse(photoIsShown())
        // nothing to remove yet
        assertFalse(removeButtonIsShown())
    }

    @Test
    fun pickingAPhoto_showsItOnTheProfile() {
        val controller = openProfile()

        pickPhoto(controller, "content://media/photo/1")

        waitForPhoto(shown = true)
        assertFalse(defaultAvatarIsShown())
        composeRule.onNodeWithTag("button_remove_photo").assertIsDisplayed()
        val savedId = runBlocking { profiles.getProfile("test-device")?.imageId }
        assertEquals(controller.imageId, savedId)
        assertEquals(listOf("content://media/photo/1"), reader.reads)
    }

    @Test
    fun replacingThePhoto_showsTheNewOneAndDeletesTheOldOne() {
        val controller = openProfile(imageId = "old-photo")
        waitForPhoto(shown = true)

        pickPhoto(controller, "content://media/photo/2")

        val savedId = runBlocking { profiles.getProfile("test-device")?.imageId }
        assertNotNull(savedId)
        assertNotEquals("old-photo", savedId)
        // the screen shows the new photo, which is the one saved on the profile
        assertEquals(savedId, composeRule.runOnIdle { controller.imageId })
        waitForPhoto(shown = true)
        assertFalse(defaultAvatarIsShown())
        assertEquals(listOf("old-photo"), images.deletedIds)
        assertNull(runBlocking { images.getImage("old-photo") })
    }

    @Test
    fun removingThePhoto_showsTheDefaultAvatarAgain() {
        openProfile(imageId = "old-photo")
        waitForPhoto(shown = true)

        composeRule.onNodeWithTag("button_remove_photo").performClick()

        waitForPhoto(shown = false)
        composeRule.onNodeWithTag("profile_photo").assertIsDisplayed()
        assertTrue(defaultAvatarIsShown())
        assertFalse(removeButtonIsShown())
        assertNull(runBlocking { profiles.getProfile("test-device")?.imageId })
        assertEquals(listOf("old-photo"), images.deletedIds)
    }

    @Test
    fun uploadedPhoto_isInTheAdminImageBrowserList() {
        val controller = openProfile()

        pickPhoto(controller, "content://media/photo/1")

        // getAllImages is what the admin image browser lists (US 03.06.01)
        val listed = runBlocking { images.getAllImages() }.single()
        assertEquals(ImageAsset.OWNER_PROFILE, listed.ownerType)
        assertEquals("test-device", listed.ownerId)
        assertEquals("Sam Patel", listed.label)
        assertEquals(controller.imageId, listed.id)
    }

    @Test
    fun photoThatCantBeUsed_showsAnErrorAndKeepsTheOldPhoto() {
        val controller = openProfile(imageId = "old-photo")
        waitForPhoto(shown = true)
        reader.fails = true

        pickPhoto(controller, "content://media/photo/broken")

        composeRule.onNodeWithTag("photo_error")
            .assertIsDisplayed()
            .assertTextEquals("Couldn't use that photo. Try another one.")
        assertTrue(photoIsShown())
        assertEquals("old-photo", runBlocking { profiles.getProfile("test-device")?.imageId })
    }

    @Test
    fun changingThePhoto_keepsNameEmailAndPhone() {
        val controller = openProfile()

        pickPhoto(controller, "content://media/photo/1")

        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("780 555 0100").assertIsDisplayed()
        val saved = runBlocking { profiles.getProfile("test-device") }
        assertEquals(sam.copy(imageId = controller.imageId), saved)
    }

    @Test
    fun removeThatFails_showsAnErrorAndKeepsThePhoto() {
        runBlocking {
            images.addImage(oldPhoto)
            profiles.saveProfile(sam.copy(imageId = "old-photo"))
        }
        val failing = object : ProfileRepository by profiles {
            override suspend fun saveProfile(profile: UserProfile) = throw IOException("offline")
        }
        openProfile(profileRepository = failing)
        waitForPhoto(shown = true)

        composeRule.onNodeWithTag("button_remove_photo").performClick()

        composeRule.onNodeWithTag("photo_error")
            .assertIsDisplayed()
            .assertTextEquals("Couldn't remove the photo. Try again.")
        assertTrue(photoIsShown())
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun whileAPhotoIsSaving_showsSavingPhotoAndSaveWaits() {
        val photoReady = CompletableDeferred<ByteArray>()
        val slowReader = object : PhotoReader {
            override suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray =
                photoReady.await()
        }
        val controller = openProfile(photoReader = slowReader)

        composeRule.runOnIdle { controller.choosePhoto("content://media/photo/1") }

        composeRule.onNodeWithText("Saving photo…").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("button_change_photo").fetchSemanticsNodes().size,
        )

        photoReady.complete(FakePhotoReader.SAMPLE_JPEG)
        waitForPhoto(shown = true)
        composeRule.onNodeWithText("Save").assertIsEnabled()
        composeRule.onNodeWithTag("button_change_photo").assertIsEnabled()
    }
}
