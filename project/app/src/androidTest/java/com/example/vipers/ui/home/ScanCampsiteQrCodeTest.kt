/*
 * ScanCampsiteQrCodeTest.kt
 * Intent tests for US 01.12.01: Home's Scan QR button opens the scanned campsite's details, and
 * shows a message for codes that aren't ours or when the scanner can't open. The camera can't be
 * used in a test, so each test hands the app the scan result it wants. Uses the fake
 * repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScanCampsiteQrCodeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private val hinton = Campsite(
        id = "hinton", ownerId = "owner-priya", name = "Hinton Ridge Campground",
        address = "Hinton, AB", description = "Quiet forest sites with an outdoor cookhouse",
    )
    private val removed = Campsite(
        id = "closed", ownerId = "owner-priya", name = "Old Mill Sites", removed = true,
    )

    // what the next Scan QR returns, in place of the camera
    private var nextScan: QrScanResult = QrScanResult.Cancelled

    @Before
    fun openApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Lee")),
            ),
            campsiteRepository = FakeCampsiteRepository(listOf(hinton, removed)),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme {
                AppRoot(
                    app.container.deviceIdentityManager,
                    onLoadDemoData = {},
                    onScanQr = { nextScan },
                )
            }
        }
        composeRule.waitUntil(5_000) {
            shows(hasTestTag("button_scan_qr"))
        }
    }

    private fun scan(result: QrScanResult) {
        nextScan = result
        composeRule.onNodeWithTag("button_scan_qr").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun homeHasAScanQrButton() {
        composeRule.onNodeWithContentDescription("Scan QR").assertIsDisplayed()
    }

    @Test
    fun scanningACampsitePoster_opensThatCampsite() {
        scan(QrScanResult.Scanned(hinton.qrLink()))

        // the description only shows on Campsite Details, not on Home's cards
        composeRule.waitUntil(5_000) {
            shows(hasText(hinton.description))
        }
        composeRule.onNodeWithText(hinton.description).assertIsDisplayed()
        composeRule.onNodeWithTag("search_field").assertDoesNotExist()
    }

    @Test
    fun scanningARemovedCampsite_saysCampsiteNotFound() {
        scan(QrScanResult.Scanned(removed.qrLink()))

        composeRule.waitUntil(5_000) {
            shows(hasText("Campsite not found."))
        }
        composeRule.onNodeWithText("Campsite not found.").assertIsDisplayed()
    }

    @Test
    fun scanningAnotherCode_saysItIsNotValidAndStaysOnHome() {
        scan(QrScanResult.Scanned("https://example.com/menu"))

        composeRule.onNodeWithTag("scan_message").assertTextEquals("Not a valid campsite code")
        composeRule.onNodeWithTag("search_field").assertIsDisplayed()
    }

    @Test
    fun closingTheScanner_staysOnHomeWithNoMessage() {
        scan(QrScanResult.Cancelled)

        composeRule.onNodeWithTag("scan_message").assertDoesNotExist()
        composeRule.onNodeWithTag("search_field").assertIsDisplayed()
    }

    @Test
    fun cameraPermissionDenied_explainsAndDoesNotCrash() {
        scan(QrScanResult.NoCameraPermission)

        composeRule.onNodeWithTag("scan_message").assertTextEquals(
            "Scan QR needs the camera. Allow camera access in your phone's " +
                "Settings, then try again.",
        )
        composeRule.onNodeWithTag("button_scan_qr").assertIsDisplayed()
    }

    @Test
    fun scannerThatCantOpen_explainsAndDoesNotCrash() {
        scan(QrScanResult.Failed)

        composeRule.onNodeWithTag("scan_message")
            .assertTextEquals("Couldn't open the scanner. Try again.")
        composeRule.onNodeWithTag("button_scan_qr").assertIsDisplayed()
    }

    @Test
    fun openingACampsiteAfterABadScan_clearsTheOldMessage() {
        scan(QrScanResult.Scanned("not ours"))

        composeRule.onNodeWithText(hinton.name).performClick()
        composeRule.waitUntil(5_000) { shows(hasText(hinton.description)) }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(5_000) { shows(hasTestTag("search_field")) }

        composeRule.onNodeWithTag("scan_message").assertDoesNotExist()
    }

    @Test
    fun scanningAgain_clearsTheOldMessage() {
        scan(QrScanResult.Scanned("not ours"))

        scan(QrScanResult.Cancelled)

        composeRule.onNodeWithTag("scan_message").assertDoesNotExist()
    }

    /** True once something on screen matches [matcher]. */
    private fun shows(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
}
