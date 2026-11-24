/*
 * ShowDeviceNotificationsTest.kt
 * Intent tests for T-14: with notification permission already granted, opening the app goes
 * straight to Home without asking again or crashing, and the inbox still works. Uses the fake
 * repositories, so it doesn't touch Firestore.
 * Known issues: refusing the system permission dialog can't be automated reliably, so that case is
 * covered by the unit tests of NotificationService.showNewOnDevice with canPost() false.
 */
package com.example.vipers.ui.notifications

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShowDeviceNotificationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private var permissionGrantedCalls = 0

    private fun openApp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(
                listOf(UserProfile(id = "test-device", name = "Sam Patel")),
            ),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(
                listOf(
                    Notification(
                        id = "win", recipientId = "test-device", type = NotificationType.WON,
                        lotteryId = "may", title = "You won the lottery!",
                        message = "Hinton Ridge: reserve by May 9",
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            ),
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme {
                AppRoot(
                    app.container.deviceIdentityManager,
                    onLoadDemoData = {},
                    onNotificationPermissionGranted = { permissionGrantedCalls++ },
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("button_scan_qr")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun permissionAlreadyGranted_opensHomeWithoutAskingAgain() {
        openApp()

        composeRule.onNodeWithTag("button_scan_qr").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").assertIsDisplayed()
        assertEquals(0, permissionGrantedCalls)
    }

    @Test
    fun permissionAlreadyGranted_inboxStillShowsTheNotification() {
        openApp()

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()

        composeRule.onNodeWithText("You won the lottery!").assertIsDisplayed()
    }
}
