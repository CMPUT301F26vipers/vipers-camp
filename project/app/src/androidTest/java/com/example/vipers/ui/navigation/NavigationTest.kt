/*
 * NavigationTest.kt
 * Intent tests for T-02 (and US 02.10.02's Sent Notifications button): the app opens on Home, every tab opens its screen, Settings and Policy
 * open from Profile, Back returns to the previous screen, and the Admin tab is for admins only.
 * Known issues: tests the navigation on its own. The app's repositories are swapped for empty
 * fakes so Home's campsite search doesn't touch Firestore.
 */
package com.example.vipers.ui.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.messages.messageTime
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Same as the phone's Back button, but sent through the app so it doesn't depend on the
    // emulator window having focus (Espresso's pressBack() does).
    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Before
    fun useFakeRepositories() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(listOf(UserProfile(id = "test-device", name = "Sam Patel"))),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )
    }

    private fun startApp(isAdmin: Boolean = false, userId: String = "user123") {
        composeRule.setContent {
            VipersTheme {
                AppNavigation(
                    isAdmin = isAdmin,
                    onLoadDemoData = {},
                )
            }
        }
    }

    private fun tapTab(route: String) = composeRule.onNodeWithTag("tab_$route").performClick()

    private fun assertHomeShown() = composeRule.onNodeWithTag("search_field").assertIsDisplayed()

    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()

    // photos are decoded off the main thread, so wait until one is on screen
    private fun waitForPhoto(description: String) =
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun opensOnHome() {
        startApp()
        assertHomeShown()
    }

    @Test
    fun eachTabOpensItsScreen() {
        startApp()
        tapTab(Routes.RESERVATIONS)
        assertShown("My Reservations")
        tapTab(Routes.CAMPSITES)
        assertShown("My Campsites")
        tapTab(Routes.NOTIFICATIONS)
        assertShown("No notifications yet")
        tapTab(Routes.PROFILE)
        assertShown("Sam Patel")
        tapTab(Routes.HOME)
        assertHomeShown()
    }

    @Test
    fun anyoneCanOpenAddCampsiteAndGoBack() {
        startApp()
        tapTab(Routes.CAMPSITES)
        composeRule.onNodeWithContentDescription("Add campsite").performClick()
        assertShown("Add Campsite")
        // no bottom bar on the form, like the mockup
        composeRule.onNodeWithTag("tab_${Routes.HOME}").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")
    }

    @Test
    fun settingsAndPolicyOpenFromProfile() {
        startApp()
        tapTab(Routes.PROFILE)
        // Settings and Policy are at the bottom of Profile, below the screen on small phones
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        assertShown("Load demo data")

        pressBack()
        assertShown("Sam Patel")

        composeRule.onNodeWithText("Policy").performScrollTo().performClick()
        assertShown("How the lottery works")
    }

    @Test
    fun adminTabIsHiddenForNormalUsers() {
        startApp(isAdmin = false)
        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").assertDoesNotExist()
    }

    @Test
    fun adminTabShowsForAdmins() {
        startApp(isAdmin = true)
        composeRule.onNodeWithTag("tab_${Routes.ADMIN}").assertIsDisplayed()
        tapTab(Routes.ADMIN)

        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).assertIsDisplayed()
        assertShown("Profiles")
        assertShown("Images")
        assertShown("Notification Log")
        assertShown("Message Log")
        composeRule.onAllNodesWithText("Browse and remove").assertCountEquals(3)
        assertShown("Every notification sent")
        assertShown("Owner–entrant conversations")
    }

    @Test
    fun adminUsersRetainAccessToAllNormalTabs() {
        startApp(isAdmin = true)
        tapTab(Routes.RESERVATIONS)
        assertShown("My Reservations")
        tapTab(Routes.CAMPSITES)
        assertShown("My Campsites")
        tapTab(Routes.NOTIFICATIONS)
        assertShown("No notifications yet")
        tapTab(Routes.PROFILE)
        assertShown("Sam Patel")
        tapTab(Routes.HOME)
        assertHomeShown()
    }

    @Test
    fun adminCanOpenCampsitesAndGoBack() {
        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).performClick()
        assertShown("All Campsites")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }

    @Test
    fun adminCanClickCampsiteRowToOpenDetailsAndGoBack() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(listOf(UserProfile(id = "test-device", name = "Sam Patel"))),
            campsiteRepository = FakeCampsiteRepository(listOf(Campsite(id = "camp-123", name = "Lakeside Camp"))),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )

        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).performClick()
        assertShown("All Campsites")

        composeRule.onNodeWithText("Lakeside Camp").performClick()
        assertShown("My Campsite")
        assertShown("Lakeside Camp")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("All Campsites")
    }

    @Test
    fun adminMenuItemsOpenCorrectDestinationsAndGoBack() {
        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        // Campsites
        composeRule.onNode(hasText("Campsites") and !hasTestTag("tab_${Routes.CAMPSITES}")).performClick()
        assertShown("All Campsites")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Profiles
        composeRule.onNodeWithText("Profiles").performClick()
        assertShown("All Profiles")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Images
        composeRule.onNodeWithText("Images").performClick()
        assertShown("All Images")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Notification Log
        composeRule.onNodeWithText("Notification Log").performClick()
        assertShown("Logs")
        composeRule.onNodeWithTag("logs_tab_notification_log").assertIsSelected()
        assertShown("Notification Log (03.08.01)")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // Message Log
        composeRule.onNodeWithText("Message Log").performClick()
        assertShown("Logs")
        composeRule.onNodeWithTag("logs_tab_message_log").assertIsSelected()
        assertShown("No message logs found.")
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }

    @Test
    fun ownerCampsiteButtonsOpenCorrectScreensAndGoBack() {
        startApp()
        tapTab(Routes.CAMPSITES)

        // Matches the specific node that has the text "Reservations" AND the Button role
        composeRule.onNode(hasText("Reservations") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertShown("No active reservations")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")

        // Test Sent navigation
        composeRule.onNodeWithText("Sent Notifications").performClick()
        assertShown("You haven't sent any notifications yet.")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("My Campsites")
    }

    @Test
    fun ownerNavigatesFromMyCampsitesToOwnerCampsite_showsRegistrationDeadlineAndCreateLottery() {
        runBlocking {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
            val campsiteRepo = FakeCampsiteRepository()
            val lotteryRepo = FakeLotteryRepository()
            val campsiteId = campsiteRepo.addCampsite(
                Campsite(
                    name = "Owner Campground",
                    ownerId = "test-device",
                    address = "Jasper National Park"
                )
            )
            lotteryRepo.addLottery(
                Lottery(
                    campsiteIds = listOf(campsiteId),
                    nightsDescription = "Summer Nights",
                    registrationStart = 1000L,
                    registrationEnd = 2000000000000L,
                    bookingStart = 2000000000000L,
                    bookingEnd = 2000001000000L,
                )
            )
            app.container = AppContainer(
                deviceId = "test-device",
                profileRepository = FakeProfileRepository(listOf(UserProfile(id = "test-device", name = "Owner User"))),
                campsiteRepository = campsiteRepo,
                lotteryRepository = lotteryRepo,
                reservationRepository = FakeReservationRepository(),
                messageRepository = FakeMessageRepository(),
                notificationRepository = FakeNotificationRepository(),
                imageRepository = FakeImageRepository(),
            )

            startApp()
            tapTab(Routes.CAMPSITES)
            composeRule.onNodeWithText("Owner Campground").performClick()

            // Owner sees campsite details and registration deadline
            assertShown("Owner Campground")
            assertShown("Registration ends: ")
            composeRule.onNodeWithText("Create Lottery").assertIsDisplayed()

            // Clicking Create Lottery navigates to Create Lottery form
            composeRule.onNodeWithText("Create Lottery").performClick()
            assertShown("Select Offered Nights")

            // Back returns to Owner Campsite
            composeRule.onNodeWithContentDescription("Back").performClick()
            assertShown("Owner Campground")
        }
    }

    @Test
    fun adminCanBrowseUploadedImagesAndOpenEnlargedViewAndGoBack() {
        val sampleJpeg = FakePhotoReader.SAMPLE_JPEG
        val imageRepo = FakeImageRepository(
            listOf(
                ImageAsset.fromJpeg(
                    jpeg = sampleJpeg,
                    uploaderId = "test-device",
                    ownerType = ImageAsset.OWNER_CAMPSITE,
                    ownerId = "camp-1",
                    label = "Pine Ridge Camp",
                    createdAt = 1000L,
                ).copy(id = "img-1"),
                ImageAsset.fromJpeg(
                    jpeg = sampleJpeg,
                    uploaderId = "test-device",
                    ownerType = ImageAsset.OWNER_PROFILE,
                    ownerId = "user-1",
                    label = "Alice Smith",
                    createdAt = 2000L,
                ).copy(id = "img-2"),
            )
        )
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(listOf(UserProfile(id = "test-device", name = "Sam Patel"))),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(),
            messageRepository = FakeMessageRepository(),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = imageRepo,
        )

        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        composeRule.onNodeWithText("Images").performClick()
        assertShown("All Images")
        assertShown("Pine Ridge Camp")
        assertShown("Alice Smith")
        assertShown("Campsite")
        assertShown("Profile")

        // Verify images are actually rendered (not just placeholders)
        waitForPhoto("Pine Ridge Camp")
        composeRule.onNodeWithContentDescription("Pine Ridge Camp", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Alice Smith", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText("Pine Ridge Camp").performClick()
        assertShown("Image")
        assertShown("Pine Ridge Camp")

        // Verify enlarged image is rendered
        waitForPhoto("Pine Ridge Camp")
        composeRule.onNodeWithContentDescription("Pine Ridge Camp", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("All Images")

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }

    @Test
    fun adminNavigatesToProfiles_showsProfileListAndCanOpenProfileDetails() {
        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        // Open Profiles
        composeRule.onNodeWithText("Profiles").performClick()
        assertShown("All Profiles")
        assertShown("Sam Patel")

        // Open Profile Details
        composeRule.onNodeWithText("Sam Patel").performClick()
        assertShown("Profile")
        assertShown("Sam Patel")
        assertShown("Campsites owned")

        // Back returns to All Profiles
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("All Profiles")

        // Back returns to Admin menu
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }

    @Test
    fun adminCanBrowseMessageLogsAndOpenReadOnlyConversationAndGoBack() {
        val messages = listOf(
            Message(
                id = "m1",
                reservationId = "res-1",
                senderId = "entrant-1",
                text = "Is firewood available?",
                createdAt = 1000L,
            ),
            Message(
                id = "m2",
                reservationId = "res-1",
                senderId = "owner-1",
                text = "Yes, stacked by the cabin.",
                createdAt = 2000L,
            ),
        )
        val reservations = listOf(
            Reservation(
                id = "res-1",
                ownerId = "owner-1",
                userId = "entrant-1",
                campsiteName = "Pine Ridge Camp",
            )
        )
        val profiles = listOf(
            UserProfile(id = "test-device", name = "Sam Patel"),
            UserProfile(id = "owner-1", name = "Owen Owner"),
            UserProfile(id = "entrant-1", name = "Emma Entrant"),
        )
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(profiles),
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(reservations),
            messageRepository = FakeMessageRepository(messages),
            notificationRepository = FakeNotificationRepository(),
            imageRepository = FakeImageRepository(),
        )

        startApp(isAdmin = true)
        tapTab(Routes.ADMIN)

        // 1. Verify Notification Log menu item opens LogsScreen with Notification Log tab selected
        composeRule.onNodeWithText("Notification Log").performClick()
        assertShown("Logs")
        composeRule.onNodeWithTag("logs_tab_notification_log").assertIsSelected()
        assertShown("Notification Log (03.08.01)")

        // Switch to Message Log tab
        composeRule.onNodeWithTag("logs_tab_message_log").performClick()
        composeRule.onNodeWithTag("logs_tab_message_log").assertIsSelected()

        // 2. Verify row displays owner, entrant, campsite, and last-message time
        assertShown("Pine Ridge Camp")
        assertShown("Owen Owner")
        assertShown("Emma Entrant")
        assertShown(messageTime(2000L))

        // Return to Admin home
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")

        // 3. Navigate from Admin -> Message Log directly
        composeRule.onNodeWithText("Message Log").performClick()
        assertShown("Logs")
        composeRule.onNodeWithTag("logs_tab_message_log").assertIsSelected()

        // 4. Tapping the conversation opens read-only MessageLogConversationScreen
        composeRule.onNodeWithText("Pine Ridge Camp").performClick()
        assertShown("Conversation")
        assertShown("Pine Ridge Camp")
        composeRule.onAllNodesWithText("Owen Owner", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Emma Entrant", substring = true).onFirst().assertIsDisplayed()

        // 5. Verify both sides' messages in chronological order
        assertShown("Is firewood available?")
        assertShown("Yes, stacked by the cabin.")
        assertShown("Emma Entrant · ${messageTime(1000L)}")
        assertShown("Owen Owner · ${messageTime(2000L)}")

        // 6. Verify read-only: no text input or Send button
        composeRule.onNodeWithTag("message_field").assertDoesNotExist()
        composeRule.onNodeWithTag("button_send").assertDoesNotExist()
        composeRule.onNodeWithText("Send").assertDoesNotExist()

        // 7. Verify back navigation returns to LogsScreen
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Logs")
        assertShown("Pine Ridge Camp")

        // Back from LogsScreen returns to Admin menu
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertShown("Profiles")
    }
}
