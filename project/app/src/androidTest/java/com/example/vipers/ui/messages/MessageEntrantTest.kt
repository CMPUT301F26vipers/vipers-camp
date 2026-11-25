/*
 * MessageEntrantTest.kt
 * Intent tests for US 02.12.01 (mockups O15, E17): the owner opens the conversation from a
 * reservation with Message Entrant, sends a message (never an empty one), sees the messages in
 * order with sender and time, the entrant gets a "New message" notification that opens the same
 * conversation, another owner can't open it, and the messages are kept for the admin log.
 * Uses the fake repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.messages

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
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
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.navigation.AppNavigation
import com.example.vipers.ui.navigation.Routes
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MessageEntrantTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "owner-priya", name = "Priya Sharma"),
            UserProfile(id = "sam", name = "Sam Patel", email = "sam@example.com"),
        ),
    )

    // Sam's stay at Priya's Hinton campsite; the screens use today's date, so it's far ahead
    private val samsStay = Reservation(
        id = "res-hinton",
        userId = "sam",
        ownerId = "owner-priya",
        campsiteId = "hinton",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2099-05-08",
        checkOut = "2099-05-10",
        groupSize = 3,
        totalPriceCents = 13000,
        reservationNumber = "R-20417",
    )

    // March 2 2027 at 9:14 AM on this phone's clock, as in mockup E17
    private val march2At914 = LocalDateTime.parse("2027-03-02T09:14")
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private val messages = FakeMessageRepository()
    private val notifications = FakeNotificationRepository()

    /** Saves a message about Sam's stay from [senderId], sent [minutesLater] after 9:14 AM. */
    private fun saved(senderId: String, text: String, minutesLater: Int = 0): Message {
        val message = Message(
            reservationId = "res-hinton",
            senderId = senderId,
            text = text,
            createdAt = march2At914 + minutesLater * 60_000L,
        )
        return message.copy(id = runBlocking { messages.addMessage(message) })
    }

    /** Starts the whole app as the user [deviceId], with the fake repositories. */
    private fun startApp(deviceId: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as CampApp
        app.container = AppContainer(
            deviceId = deviceId,
            profileRepository = profiles,
            campsiteRepository = FakeCampsiteRepository(),
            lotteryRepository = FakeLotteryRepository(),
            reservationRepository = FakeReservationRepository(listOf(samsStay)),
            messageRepository = messages,
            notificationRepository = notifications,
            imageRepository = FakeImageRepository(),
        )
        composeRule.setContent {
            VipersTheme { AppNavigation(isAdmin = false, onLoadDemoData = {}) }
        }
    }

    /** Shows the Messages screen for "res-hinton" to [userId] on its own. */
    private fun openMessages(userId: String) {
        val controller = MessageController(
            messageRepository = messages,
            reservationRepository = FakeReservationRepository(listOf(samsStay)),
            profileRepository = profiles,
            notificationService = NotificationService(
                notifications,
                FakeCampsiteRepository(),
                profiles,
                FakeLotteryRepository(),
            ),
            userId = userId,
            reservationId = "res-hinton",
        )
        composeRule.setContent {
            VipersTheme { MessagesScreen("res-hinton", onBack = {}, controller = controller) }
        }
    }

    /** Campsites → Reservations → Sam's reservation (O15) → Message Entrant. */
    private fun ownerOpensTheConversation() {
        composeRule.onNodeWithTag("tab_${Routes.CAMPSITES}").performClick()
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onNode(hasText("Reservations") and isButton).performClick()
        composeRule.onNodeWithTag("reservation_row").performClick()
        composeRule.onNodeWithText("Message Entrant").performClick()
    }

    /** How far down the screen the message with this [text] is. */
    private fun topOf(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    /**
     * Types [text] in the message box, taps send and closes the keyboard. On a small phone
     * without a hardware keyboard (like the CI emulator), the test screen slides up under the
     * open keyboard and hides the top of the conversation.
     */
    private fun send(text: String) {
        composeRule.onNodeWithTag("message_field").performTextInput(text)
        composeRule.onNodeWithTag("button_send").performClick()
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
    }

    @Test
    fun messageEntrant_onTheReservation_opensTheConversationWithTheEntrant() {
        startApp(deviceId = "owner-priya")

        ownerOpensTheConversation()

        // the top bar shows who the conversation is with
        composeRule.onNodeWithText("Sam Patel").assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()
        composeRule.onNodeWithTag("message_field").assertIsDisplayed()
    }

    @Test
    fun owner_sendsAMessage_itShowsAsTheirsAndTheBoxEmpties() {
        startApp(deviceId = "owner-priya")
        ownerOpensTheConversation()

        send("Check-in is at 2 PM")

        composeRule.onNodeWithText("Check-in is at 2 PM").assertIsDisplayed()
        composeRule.onNodeWithText("You ·", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet").assertDoesNotExist()
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
    }

    @Test
    fun entrantStartsThreadFromReservation_thenRepliesInTheSameThread() {
        startApp(deviceId = "sam")
        composeRule.onNodeWithTag("tab_${Routes.RESERVATIONS}").performClick()
        composeRule.onNodeWithTag("my_reservation_row").performClick()
        composeRule.onNodeWithText("Message Owner").performScrollTo().performClick()
        composeRule.onNodeWithText("Priya Sharma").assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()

        composeRule.onNodeWithTag("message_field").performTextInput("   ")
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        send("Is drinking water available?")
        composeRule.onNodeWithText("Is drinking water available?").assertIsDisplayed()
        saved("owner-priya", "Yes, by the cookhouse.")
        composeRule.onNodeWithText("Yes, by the cookhouse.").assertIsDisplayed()
        send("Thank you!")

        val notices = runBlocking { notifications.getNotificationsFor("owner-priya") }
        assertEquals(2, notices.size)
        assertTrue(notices.all { it.type == NotificationType.NEW_MESSAGE })
        assertTrue(notices.all { it.reservationId == samsStay.id })
        assertTrue(notices.any { it.message == "Hinton Ridge Campground: Thank you!" })
        val thread = runBlocking { messages.getAllThreads() }.single()
        assertEquals(3, thread.size)
        assertEquals(2, thread.count { it.senderId == "sam" })
    }

    @Test
    fun emptyMessage_cannotBeSent() {
        openMessages("owner-priya")

        // nothing typed
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        // only spaces
        composeRule.onNodeWithTag("message_field").performTextInput("   ")
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("button_send").performClick()

        assertTrue(runBlocking { messages.getAllThreads() }.isEmpty())
    }

    @Test
    fun messages_showInOrderWithSenderAndTime() {
        // saved out of order on purpose
        saved("owner-priya", "Yes, by the cookhouse.", minutesLater = 66)
        saved("owner-priya", "Thanks for booking!")
        saved("sam", "Is there drinking water?", minutesLater = 48)
        openMessages("sam")

        // the entrant sees the owner's name in the top bar and on the owner's messages
        composeRule.onNodeWithText("Priya Sharma").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Sharma · Mar 2 · 9:14 AM").assertIsDisplayed()
        composeRule.onNodeWithText("You · Mar 2 · 10:02 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Sharma · Mar 2 · 10:20 AM").assertIsDisplayed()
        // oldest at the top
        val first = topOf("Thanks for booking!")
        val second = topOf("Is there drinking water?")
        val third = topOf("Yes, by the cookhouse.")
        assertTrue(first < second && second < third)
    }

    @Test
    fun sentMessage_addsANewMessageNoticeToTheEntrantsInbox() {
        startApp(deviceId = "owner-priya")
        ownerOpensTheConversation()

        send("Check-in is at 2 PM")

        val notice = runBlocking { notifications.getNotificationsFor("sam") }.single()
        assertEquals(NotificationType.NEW_MESSAGE, notice.type)
        assertEquals("res-hinton", notice.reservationId)
        assertEquals("Hinton Ridge Campground: Check-in is at 2 PM", notice.message)
        // the owner doesn't get a notice about their own message
        assertTrue(runBlocking { notifications.getNotificationsFor("owner-priya") }.isEmpty())
    }

    @Test
    fun entrant_tapsTheNewMessageNotice_andSeesTheConversation() {
        // Priya sent a message, which added a notice to Sam's inbox
        val fromOwner = saved("owner-priya", "Check-in is at 2 PM")
        runBlocking {
            NotificationService(notifications, FakeCampsiteRepository(), profiles, FakeLotteryRepository())
                .sendNewMessage(samsStay, fromOwner)
        }
        startApp(deviceId = "sam")

        composeRule.onNodeWithTag("tab_${Routes.NOTIFICATIONS}").performClick()
        val preview = "Hinton Ridge Campground: Check-in is at 2 PM"
        composeRule.onNodeWithText(preview, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("New message").performClick()

        composeRule.onNodeWithText("Priya Sharma").assertIsDisplayed()
        composeRule.onNodeWithText("Check-in is at 2 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Sharma ·", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("message_field").assertIsDisplayed()
    }

    @Test
    fun entrantWithNotificationsOff_getsNoNotice_butStillSeesTheMessage() {
        val samOff = UserProfile(id = "sam", name = "Sam Patel", notificationsEnabled = false)
        runBlocking { profiles.saveProfile(samOff) }
        openMessages("owner-priya")

        send("Check-in is at 2 PM")

        assertTrue(runBlocking { notifications.getNotificationsFor("sam") }.isEmpty())
        assertEquals(1, runBlocking { messages.getAllThreads() }.single().size)
    }

    @Test
    fun anotherOwner_cannotOpenTheConversation() {
        saved("owner-priya", "Thanks for booking!")
        openMessages("owner-daniel")

        composeRule.onNodeWithText("Conversation not found").assertIsDisplayed()
        composeRule.onNodeWithText("Thanks for booking!").assertDoesNotExist()
        composeRule.onNodeWithTag("message_field").assertDoesNotExist()
    }

    @Test
    fun sentMessages_areKeptForTheAdminLog() {
        openMessages("owner-priya")

        send("Check-in is at 2 PM")
        composeRule.onNodeWithTag("button_send").assertIsNotEnabled()
        send("Firewood is free")

        val thread = runBlocking { messages.getAllThreads() }.single()
        assertEquals(listOf("Check-in is at 2 PM", "Firewood is free"), thread.map { it.text })
        assertTrue(thread.all { it.senderId == "owner-priya" && it.reservationId == "res-hinton" })
    }

    @Test
    fun sendButton_worksOnceTextIsTyped() {
        openMessages("owner-priya")

        composeRule.onNodeWithTag("message_field").performTextInput("Hi")

        composeRule.onNodeWithTag("button_send").assertIsEnabled()
    }
}
