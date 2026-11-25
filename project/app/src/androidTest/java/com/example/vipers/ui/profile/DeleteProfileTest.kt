/*
 * DeleteProfileTest.kt
 * Intent tests for US 01.08.01: Profile has a "Delete Profile" button, the dialog says it can't
 * be undone and warns about the user's campsites only when they own some, Cancel keeps
 * everything, and Delete deletes the profile and its photo, marks their entries Removed and goes
 * back to the first-launch screen (T-01), where a new profile can be made.
 * Known issues: uses fake repositories, so it doesn't touch Firestore. What happens to
 * reservations and campsites is checked in DeletionServiceTest.
 */
package com.example.vipers.ui.profile

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DeleteProfileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private val images = FakeImageRepository()
    private val profiles = FakeProfileRepository(
        listOf(UserProfile(id = "my-phone", name = "Sam Patel", imageId = "sam-photo")),
        images,
    )
    private val campsites = FakeCampsiteRepository()
    private val lotteries = FakeLotteryRepository(
        initialLotteries = listOf(
            Lottery(id = "lottery-1", ownerId = "alex", waitingUserIds = listOf("my-phone")),
        ),
        initialEntries = listOf(
            LotteryEntry(
                id = LotteryEntry.idFor("lottery-1", "my-phone"),
                lotteryId = "lottery-1",
                userId = "my-phone",
            ),
        ),
    )

    /** Opens the app as "my-phone" (Sam), with fake repositories, and goes to Profile. */
    private fun openProfile() {
        runBlocking {
            images.addImage(
                ImageAsset.fromJpeg(
                    FakePhotoReader.SAMPLE_JPEG, "my-phone", ImageAsset.OWNER_PROFILE,
                    "my-phone", "Sam Patel", 1_809_000_000_000,
                ).copy(id = "sam-photo"),
            )
        }
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "my-phone",
            profileRepository = profiles,
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = images,
        )
        composeRule.setContent {
            VipersTheme { AppRoot(app.container.deviceIdentityManager, onLoadDemoData = {}) }
        }
        waitForTag("tab_${Routes.PROFILE}")
        composeRule.onNodeWithTag("tab_${Routes.PROFILE}").performClick()
        waitForTag("button_delete_profile")
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun tapDeleteProfile() {
        // it's at the bottom of Profile, below the screen on small phones
        composeRule.onNodeWithTag("button_delete_profile").performScrollTo().performClick()
        waitForText("Delete your profile?")
    }

    @Test
    fun profile_hasADeleteProfileButton() {
        openProfile()

        composeRule.onNodeWithTag("button_delete_profile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delete Profile").assertIsDisplayed()
    }

    @Test
    fun deleteProfile_asksFirstAndSaysItCantBeUndone() {
        openProfile()

        tapDeleteProfile()

        composeRule.onNodeWithText("This can't be undone", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("button_confirm_delete").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        // Sam owns no campsites, so there's no warning about them
        composeRule.onNodeWithTag("delete_campsites_warning").assertDoesNotExist()
    }

    @Test
    fun deleteProfile_whenTheyOwnCampsites_warnsTheyWillBeRemoved() {
        runBlocking {
            campsites.addCampsite(Campsite(id = "site-1", ownerId = "my-phone", name = "Lakeside"))
        }
        openProfile()

        tapDeleteProfile()

        composeRule.onNodeWithTag("delete_campsites_warning")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Your campsites will be removed too", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun cancel_keepsTheProfile() {
        openProfile()
        tapDeleteProfile()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete your profile?").assertDoesNotExist()
        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        assertNotNull(runBlocking { profiles.getProfile("my-phone") })
    }

    @Test
    fun confirming_deletesTheProfileAndPhotoAndGoesBackToWelcome() {
        openProfile()
        tapDeleteProfile()

        composeRule.onNodeWithTag("button_confirm_delete").performClick()

        waitForText("Tell us your name to get started.")
        composeRule.onNodeWithTag("welcome_name").assertIsDisplayed()
        assertNull(runBlocking { profiles.getProfile("my-phone") })
        assertNull(runBlocking { images.getImage("sam-photo") })
        assertEquals("Deleted user", runBlocking { profiles.getDisplayName("my-phone") })
        val entry = runBlocking { lotteries.getEntry("lottery-1", "my-phone") }
        assertEquals(EntryStatus.REMOVED, entry?.status)
    }

    @Test
    fun afterDeleting_aNewProfileCanBeMadeOnWelcome() {
        openProfile()
        tapDeleteProfile()
        composeRule.onNodeWithTag("button_confirm_delete").performClick()
        waitForText("Tell us your name to get started.")

        composeRule.onNodeWithTag("welcome_name").performTextInput("Maya Chen")
        composeRule.onNodeWithTag("button_continue").performClick()

        waitForTag("search_field")
        assertEquals("Maya Chen", runBlocking { profiles.getProfile("my-phone")?.name })
    }
}
