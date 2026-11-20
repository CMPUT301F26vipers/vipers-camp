/*
 * AddCampsitePhotoTest.kt
 * Intent tests for US 02.01.02b: adding an optional promo photo on the campsite form (mockups O02,
 * O05) and seeing it as a thumbnail on the Home results (E02) and My Campsites (O01), and whole on
 * My Campsite (O04) and Campsite Details (E05), one test per acceptance criterion. Uses fake
 * repositories and a fake photo reader, so it doesn't touch Firestore or need the photo picker.
 * Known issues: the system photo picker can't be driven from a test, so tests hand the controller
 * a picked photo directly, as the picker would.
 */
package com.example.vipers.ui.campsites

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakePlaceFinder
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddCampsitePhotoTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val photo = ImageAsset.fromJpeg(
        FakePhotoReader.SAMPLE_JPEG, "test-device", ImageAsset.OWNER_CAMPSITE, "hinton",
        "Hinton Ridge Campground", 1_809_000_000_000,
    ).copy(id = "photo-1")

    // the Hinton campsite from scenario 2 in the project description, with a saved photo
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "test-device",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 6500,
        imageId = "photo-1",
    )

    private val campsites = FakeCampsiteRepository(listOf(hinton))
    private val images = FakeImageRepository(listOf(photo))
    private val reader = FakePhotoReader()
    private var publishedCalled = false

    @Before
    fun useFakeRepositories() {
        // the photo boxes read images through the app's container, so give it the fakes
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma")),
            ),
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = images,
        )
    }

    /** Opens the Add Campsite form, or Edit Campsite for [campsiteId], with the fakes. */
    private fun openForm(campsiteId: String? = null): CampsiteController {
        val controller = CampsiteController(
            campsites,
            ownerId = "test-device",
            placeFinder = FakePlaceFinder(),
            imageRepository = images,
            photoReader = reader,
        )
        composeRule.setContent {
            VipersTheme {
                CampsiteFormScreen(
                    onBack = {},
                    onPublished = { publishedCalled = true },
                    campsiteId = campsiteId,
                    controller = controller,
                )
            }
        }
        return controller
    }

    private fun openApp() = composeRule.setContent {
        VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
    }

    // the campsite the form saved
    private fun lakesideSites() =
        runBlocking { campsites.getAllCampsites() }.single { it.name == "Lakeside Sites" }

    // what the photo picker does once the owner chooses a photo
    private fun pickPhoto(controller: CampsiteController) =
        composeRule.runOnIdle { runBlocking { controller.fetchPhoto("content://media/photo/42") } }

    private fun type(tag: String, text: String) =
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun fillValidForm() {
        type("field_name", "Lakeside Sites")
        type("field_address", "Hinton, AB")
        composeRule.onNodeWithTag("field_region").performScrollTo().performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "6")
        type("field_price", "65")
    }

    private fun publish() = composeRule.onNodeWithTag("button_publish").performClick()

    // photos are decoded off the main thread, so wait until one is on screen
    private fun waitForPhoto(description: String) =
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun form_hasAnOptionalAddPhotoButton() {
        openForm()

        composeRule.onNodeWithText("+ Add photo").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithContentDescription("Campsite photo")
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pickedPhoto_isPreviewedAndCanBeChangedOrRemoved() {
        val controller = openForm()

        pickPhoto(controller)

        composeRule.onNodeWithTag("photo_preview").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Campsite photo").assertIsDisplayed()
        composeRule.onNodeWithTag("button_change_photo").assertIsDisplayed()

        // Remove goes back to "+ Add photo"
        composeRule.onNodeWithTag("button_remove_photo").performClick()
        composeRule.onNodeWithText("+ Add photo").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(controller.hasPhoto) }
    }

    @Test
    fun pickedPhoto_changedForAnother_showsTheNewOne() {
        val controller = openForm()
        pickPhoto(controller)

        pickPhoto(controller) // Change opens the picker again; the owner picks another photo

        composeRule.onNodeWithTag("photo_preview").assertIsDisplayed()
        assertEquals(2, reader.reads.size)
    }

    @Test
    fun publish_withPhoto_savesTheCampsiteWithItsPhoto() {
        val controller = openForm()
        fillValidForm()
        pickPhoto(controller)

        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        val saved = lakesideSites()
        val image = runBlocking { images.getImage(saved.imageId!!) }
        assertNotNull(image)
        assertEquals(saved.id, image!!.ownerId)
        // it's in the list the admin image browser will show (US 03.06.01)
        assertTrue(runBlocking { images.getAllImages() }.any { it.id == saved.imageId })
    }

    @Test
    fun publish_withoutPhoto_savesTheCampsite() {
        openForm()
        fillValidForm()

        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        val saved = lakesideSites()
        assertNull(saved.imageId)
    }

    @Test
    fun publish_photoUploadFails_showsAnErrorAndKeepsWhatWasTyped() {
        val controller = openForm()
        fillValidForm()
        pickPhoto(controller)
        images.failAdds = true

        publish()

        composeRule.onNodeWithTag("photo_error").performScrollTo()
            .assertTextContains("Couldn't upload the photo", substring = true)
        composeRule.onNodeWithTag("field_name").performScrollTo()
            .assertTextContains("Lakeside Sites")
        composeRule.onNodeWithTag("photo_preview").performScrollTo().assertIsDisplayed()
        assertFalse(publishedCalled)
        assertEquals(1, runBlocking { campsites.getAllCampsites() }.size)
    }

    @Test
    fun unreadablePhoto_showsAnError() {
        val controller = openForm()
        reader.fails = true

        pickPhoto(controller)

        composeRule.onNodeWithText("Couldn't use that photo. Try another one.").assertIsDisplayed()
        composeRule.onNodeWithText("+ Add photo").assertIsDisplayed()
    }

    @Test
    fun editForm_showsTheSavedPhotoWithChangeAndRemove() {
        openForm(campsiteId = "hinton")

        waitForPhoto("Campsite photo")
        composeRule.onNodeWithContentDescription("Campsite photo").assertIsDisplayed()
        composeRule.onNodeWithTag("button_change_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("button_remove_photo").assertIsDisplayed()
    }

    // The sample photo is square, so shown whole (not cropped to a strip) it's as tall as it is
    // wide, up to FullPhoto's 360dp limit.
    private fun assertShowsTheWholeSquarePhoto() {
        val photo = composeRule.onNodeWithContentDescription("Campsite photo").getBoundsInRoot()
        val width = (photo.right - photo.left).value
        val height = (photo.bottom - photo.top).value
        assertEquals(minOf(width, 360f), height, 2f)
    }

    @Test
    fun savedPhoto_showsOnHomeCampsiteDetailsAndMyCampsite() {
        openApp()

        // Home results (E02)
        waitForPhoto("Photo of Hinton Ridge Campground")
        composeRule.onNodeWithContentDescription(
            "Photo of Hinton Ridge Campground", useUnmergedTree = true,
        ).assertIsDisplayed()

        // Campsite Details (E05)
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        waitForPhoto("Campsite photo")
        composeRule.onNodeWithTag("details_photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Campsite photo").assertIsDisplayed()
        assertShowsTheWholeSquarePhoto()

        // My Campsites list (O01)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        waitForPhoto("Photo of Hinton Ridge Campground")
        composeRule.onNodeWithContentDescription(
            "Photo of Hinton Ridge Campground", useUnmergedTree = true,
        ).assertIsDisplayed()

        // My Campsite (O04)
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
        waitForPhoto("Campsite photo")
        composeRule.onNodeWithTag("owner_photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Campsite photo").assertIsDisplayed()
        assertShowsTheWholeSquarePhoto()
    }

    @Test
    fun myCampsite_withPhotoAndMapLocation_showsTheWholePhotoAndTheMapBox() {
        runBlocking { campsites.updateCampsite(hinton.copy(lat = 53.405, lng = -117.585)) }
        openApp()

        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        waitForPhoto("Campsite photo")

        assertShowsTheWholeSquarePhoto()
        composeRule.onNodeWithTag("owner_map_location").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun campsiteWithoutPhoto_showsThePlaceholder() {
        runBlocking { campsites.updateCampsite(hinton.copy(imageId = null)) }
        openApp()

        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()

        composeRule.onNodeWithTag("details_photo").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithContentDescription("Campsite photo")
            .fetchSemanticsNodes().isEmpty())
    }
}
