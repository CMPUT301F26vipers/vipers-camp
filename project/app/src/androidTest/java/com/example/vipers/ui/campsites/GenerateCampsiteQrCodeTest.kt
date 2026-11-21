/*
 * GenerateCampsiteQrCodeTest.kt
 * Intent tests for US 02.02.01: QR Code on the owner's campsite screen (mockup O04) opens the
 * Campsite QR Code dialog (mockup O06). The code scans to a link with the campsite's ID, each
 * campsite gets its own, it stays the same after edits, it can be opened again any time from the
 * app, and Save writes a PNG that scans. Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: tapping Save opens Android's own "save as" screen, which a Compose test can't
 * drive, so saving is tested by writing the file straight from the controller.
 */
package com.example.vipers.ui.campsites

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vipers.AppContainer
import com.example.vipers.CampApp
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeMessageRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class GenerateCampsiteQrCodeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // the Hinton campsite from scenario 2 in the project description, owned by this device
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "test-device",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse and an on-site tennis court.",
        maxOccupancy = 8,
        pricePerNightCents = 6500,
    )

    // a second campsite the same owner lists
    private val jasper = Campsite(
        id = "jasper",
        ownerId = "test-device",
        name = "Jasper Lakeside Sites",
        address = "Jasper, AB",
        region = Region.JASPER,
        maxOccupancy = 6,
        pricePerNightCents = 5000,
    )

    private val campsites = FakeCampsiteRepository(listOf(hinton, jasper))

    /** Shows the whole app on this device, which owns [hinton] and [jasper]. */
    private fun openApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Priya Sharma")),
            ),
            campsiteRepository = campsites,
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }
    }

    /** A controller for [hinton] on its own, for the tests that save the code. */
    private fun ownerController() =
        OwnerCampsiteController(campsites, FakeLotteryRepository(), "hinton", "test-device")

    /** From the app's first screen, opens My Campsite for the campsite called [name]. */
    private fun openMyCampsite(name: String) {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        composeRule.onNodeWithText(name).performClick()
        composeRule.onNodeWithText("My Campsite").assertIsDisplayed()
    }

    private fun openQrCode() {
        composeRule.onNodeWithTag("button_qr_code").performClick()
        composeRule.onNodeWithText("Campsite QR Code").assertIsDisplayed()
    }

    /** Reads [image] the way a phone camera would and returns the text in its QR code. */
    private fun scan(image: Bitmap): String {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    /** Scans the QR code shown in the open dialog. */
    private fun scanShownCode(): String {
        val shown = composeRule.onNodeWithTag("qr_code_image").captureToImage().asAndroidBitmap()
        // a software copy, since getPixels doesn't work on images kept on the GPU
        return scan(shown.copy(Bitmap.Config.ARGB_8888, false))
    }

    @Test
    fun qrCodeButtonOpensTheCampsiteQrCode() {
        openApp()
        openMyCampsite("Hinton Ridge Campground")
        openQrCode()

        composeRule.onNodeWithTag("qr_code_image").assertIsDisplayed()
        composeRule.onNodeWithTag("button_qr_save").assertIsDisplayed()
        composeRule.onNodeWithTag("button_qr_close").assertIsDisplayed()
    }

    @Test
    fun shownCodeScansToALinkWithTheCampsiteId() {
        openApp()
        openMyCampsite("Hinton Ridge Campground")
        openQrCode()

        val link = scanShownCode()

        assertEquals("vipers-camp://campsite/hinton", link)
        assertEquals("hinton", Campsite.idFromQrLink(link))
    }

    @Test
    fun eachCampsiteHasItsOwnCode() {
        openApp()
        openMyCampsite("Hinton Ridge Campground")
        openQrCode()
        val hintonLink = scanShownCode()
        composeRule.onNodeWithTag("button_qr_close").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Jasper Lakeside Sites").performClick()
        openQrCode()
        val jasperLink = scanShownCode()

        assertNotEquals(hintonLink, jasperLink)
        assertEquals("jasper", Campsite.idFromQrLink(jasperLink))
    }

    @Test
    fun closeThenQrCodeAgainShowsTheSameCodeWithoutLeavingTheApp() {
        openApp()
        openMyCampsite("Hinton Ridge Campground")
        openQrCode()
        val first = scanShownCode()

        composeRule.onNodeWithTag("button_qr_close").performClick()
        composeRule.onNodeWithText("Campsite QR Code").assertDoesNotExist()
        openQrCode()

        assertEquals(first, scanShownCode())
    }

    @Test
    fun codeStaysTheSameAfterTheCampsiteIsEdited() {
        openApp()
        openMyCampsite("Hinton Ridge Campground")
        openQrCode()
        val before = scanShownCode()
        composeRule.onNodeWithTag("button_qr_close").performClick()

        composeRule.onNodeWithTag("button_edit").performClick()
        composeRule.onNodeWithTag("field_name").performTextReplacement("Hinton Ridge Sites")
        composeRule.onNodeWithTag("button_publish").performClick()
        composeRule.onNodeWithText("Hinton Ridge Sites").assertIsDisplayed()
        openQrCode()

        assertEquals(before, scanShownCode())
    }

    @Test
    fun savedPngScansToTheCampsiteLink() = runBlocking {
        val controller = ownerController()
        controller.fetchData()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, qrFileName(hinton.name))

        controller.writeQrCode({ file.outputStream() })

        assertEquals("QR code saved", controller.qrMessage)
        val saved = BitmapFactory.decodeFile(file.path)
        assertEquals(QR_SAVED_SIZE, saved.width)
        assertEquals("vipers-camp://campsite/hinton", scan(saved))
        file.delete()
        Unit
    }

    @Test
    fun dialogSaysWhenTheCodeWasSaved() {
        val controller = ownerController()
        composeRule.setContent {
            VipersTheme {
                OwnerCampsiteScreen(
                    campsiteId = "hinton",
                    onBack = {},
                    onEdit = {},
                    onOpenAvailability = {},
                    onOpenReservations = {},
                    onCreateLottery = {},
                    onLotteryClick = {},
                    controller = controller,
                )
            }
        }
        openQrCode()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qr.png")

        runBlocking { controller.writeQrCode({ file.outputStream() }) }

        composeRule.onNodeWithTag("qr_code_message").assertTextEquals("QR code saved")
        file.delete()
    }
}
