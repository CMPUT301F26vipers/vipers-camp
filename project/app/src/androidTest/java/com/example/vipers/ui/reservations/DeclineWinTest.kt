/*
 * DeclineWinTest.kt
 * Intent tests for US 01.04.03 and US 01.04.02: a winner declines their win from the Reserve
 * screen (mockups E11 and E12), including before selecting a campsite in a multi-site lottery.
 * Cancel in the dialog changes nothing, Confirm marks the entry DECLINED and replaces the button,
 * and a reserved win shows no Decline button. Uses the fake repositories, so it doesn't touch
 * Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.lottery.LotteryDrawer
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class DeclineWinTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 5000,
    )

    // the screen checks the booking period against the real clock, so times are set around now
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    // booking opened yesterday and ends in 5 days, so the win can be declined
    private val nights = (0L until 7L).map { LocalDate.now().plusDays(10 + it).toString() }
    private val lottery = Lottery(
        id = "lot-summer",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        nightsOffered = nights,
        registrationStart = now - 10 * day,
        registrationEnd = now - 2 * day,
        bookingStart = now - day,
        bookingEnd = now + 5 * day,
        drawnAt = now - 2 * day,
    )

    private lateinit var lotteries: FakeLotteryRepository
    private lateinit var notifications: FakeNotificationRepository

    private fun prepareFakes(
        status: EntryStatus,
        extraEntries: List<LotteryEntry> = emptyList(),
    ): FakeCampsiteRepository {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking {
            campsites.saveAvailability(AvailabilityCalendar(campsiteId = "hinton", openNights = nights))
        }
        notifications = FakeNotificationRepository()
        lotteries = FakeLotteryRepository(
            listOf(lottery),
            listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "test-device"),
                    lotteryId = "lot-summer",
                    userId = "test-device",
                    groupSize = 4,
                    status = status,
                ),
            ) + extraEntries,
            notificationRepository = notifications,
        )
        return campsites
    }

    /**
     * Shows the Reserve screen for Sam's win, whose entry has [status], and waits for it to load.
     * The controller is built with fake repositories and passed straight to the screen.
     */
    private fun openReserve(
        status: EntryStatus,
        extraEntries: List<LotteryEntry> = emptyList(),
    ) {
        val campsites = prepareFakes(status, extraEntries)
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = "hinton",
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            lotteryDrawer = LotteryDrawer(
                lotteries,
                notifications,
                campsites,
                FakeProfileRepository(),
                Random(42),
            ),
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", "hinton", onBack = {}, controller = controller)
            }
        }
        waitForTag("reserve_banner")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun samsStatus(): EntryStatus? =
        runBlocking { lotteries.getEntry("lot-summer", "test-device")?.status }

    @Test
    fun cancel_leavesTheEntryWon() {
        openReserve(EntryStatus.WON)

        composeRule.onNodeWithTag("reserve_decline").performScrollTo().performClick()
        composeRule.onNodeWithText("Decline this win?").assertIsDisplayed()
        composeRule.onNodeWithText("You won't be able to reserve from it.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(EntryStatus.WON, samsStatus())
        assertEquals(0, composeRule.onAllNodesWithTag("decline_confirm").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("reserve_decline").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun confirm_setsTheEntryDeclinedAndHidesTheButton() {
        openReserve(EntryStatus.WON)

        composeRule.onNodeWithTag("reserve_decline").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        waitForTag("reserve_declined")

        assertEquals(EntryStatus.DECLINED, samsStatus())
        composeRule.onNodeWithText("You declined this win.").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("reserve_decline").fetchSemanticsNodes().size)
        // the win is used, so nights can't be reserved from it any more
        composeRule.onNodeWithText("You have already used this win.").assertIsDisplayed()
    }

    @Test
    fun reservedEntry_showsNoDeclineButton() {
        openReserve(EntryStatus.RESERVED)

        assertEquals(0, composeRule.onAllNodesWithTag("reserve_decline").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("reserve_declined").fetchSemanticsNodes().size)
        assertEquals(EntryStatus.RESERVED, samsStatus())
    }

    @Test
    fun confirm_withLostEntrant_promotesReplacementAndSendsWonNotice() {
        val campsites = prepareFakes(
            EntryStatus.WON,
            extraEntries = listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "user-jo"),
                    lotteryId = "lot-summer",
                    userId = "user-jo",
                    groupSize = 2,
                    status = EntryStatus.LOST,
                ),
            ),
        )
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampApp
        val previousContainer = app.container
        app.container = AppContainer(
            deviceId = "test-device",
            profileRepository = FakeProfileRepository(),
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            messageRepository = FakeMessageRepository(),
            notificationRepository = notifications,
            imageRepository = FakeImageRepository(),
        )
        try {
            composeRule.setContent {
                VipersTheme {
                    ReserveScreen("lot-summer", "hinton", onBack = {})
                }
            }
            waitForTag("reserve_banner")

            composeRule.onNodeWithTag("reserve_decline").performScrollTo().performClick()
            composeRule.onNodeWithText("Confirm").performClick()
            waitForTag("reserve_declined")

            assertEquals(EntryStatus.DECLINED, samsStatus())
            runBlocking {
                assertEquals(EntryStatus.WON, lotteries.getEntry("lot-summer", "user-jo")?.status)
                val notices = app.container.notificationRepository.getNotificationsFor("user-jo")
                assertEquals(1, notices.size)
                assertEquals("replacement_lot-summer_user-jo", notices[0].id)
                assertEquals(NotificationType.WON, notices[0].type)
            }
        } finally {
            app.container = previousContainer
        }
    }

    @Test
    fun multiSite_declineBeforeSelectingCampsite_declinesAndPromotesReplacement() {
        val jasper = Campsite(
            id = "jasper",
            ownerId = "owner-priya",
            name = "Jasper Pines",
            address = "Jasper, AB",
            region = Region.JASPER,
            maxOccupancy = 6,
            pricePerNightCents = 6500,
        )
        val campsites = FakeCampsiteRepository(listOf(hinton, jasper))
        runBlocking {
            campsites.saveAvailability(AvailabilityCalendar(campsiteId = "hinton", openNights = nights))
            campsites.saveAvailability(AvailabilityCalendar(campsiteId = "jasper", openNights = nights))
        }
        notifications = FakeNotificationRepository()
        val multiLottery = lottery.copy(campsiteIds = listOf("hinton", "jasper"))
        lotteries = FakeLotteryRepository(
            listOf(multiLottery),
            listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "test-device"),
                    lotteryId = "lot-summer",
                    userId = "test-device",
                    groupSize = 4,
                    status = EntryStatus.WON,
                ),
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "user-jo"),
                    lotteryId = "lot-summer",
                    userId = "user-jo",
                    groupSize = 2,
                    status = EntryStatus.LOST,
                ),
            ),
            notificationRepository = notifications,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = null,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            lotteryDrawer = LotteryDrawer(
                lotteries,
                notifications,
                campsites,
                FakeProfileRepository(),
                Random(42),
            ),
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", null, onBack = {}, controller = controller)
            }
        }
        waitForTag("reserve_choose_campsite_prompt")

        composeRule.onNodeWithTag("reserve_decline").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        waitForTag("reserve_declined")

        assertEquals(EntryStatus.DECLINED, samsStatus())
        runBlocking {
            assertEquals(EntryStatus.WON, lotteries.getEntry("lot-summer", "user-jo")?.status)
            val notices = notifications.getNotificationsFor("user-jo")
            assertEquals(1, notices.size)
            assertEquals("replacement_lot-summer_user-jo", notices[0].id)
            assertEquals(NotificationType.WON, notices[0].type)
        }
    }

    @Test
    fun multiSite_zeroActiveCampsites_showsNoCampsitesMessageAndAllowsDecline() {
        val removedHinton = hinton.copy(removed = true)
        val campsites = FakeCampsiteRepository(listOf(removedHinton))
        notifications = FakeNotificationRepository()
        val multiLottery = lottery.copy(campsiteIds = listOf("hinton", "missing-site"))
        lotteries = FakeLotteryRepository(
            listOf(multiLottery),
            listOf(
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "test-device"),
                    lotteryId = "lot-summer",
                    userId = "test-device",
                    groupSize = 4,
                    status = EntryStatus.WON,
                ),
                LotteryEntry(
                    id = LotteryEntry.idFor("lot-summer", "user-jo"),
                    lotteryId = "lot-summer",
                    userId = "user-jo",
                    groupSize = 2,
                    status = EntryStatus.LOST,
                ),
            ),
            notificationRepository = notifications,
        )
        val controller = ReserveController(
            campsiteRepository = campsites,
            lotteryRepository = lotteries,
            userId = "test-device",
            lotteryId = "lot-summer",
            campsiteId = null,
            reservationRepository = FakeReservationRepository(campsites, lotteries),
            lotteryDrawer = LotteryDrawer(
                lotteries,
                notifications,
                campsites,
                FakeProfileRepository(),
                Random(42),
            ),
        )
        composeRule.setContent {
            VipersTheme {
                ReserveScreen("lot-summer", null, onBack = {}, controller = controller)
            }
        }
        waitForTag("reserve_no_campsites")

        composeRule.onNodeWithTag("reserve_no_campsites")
            .assertIsDisplayed()
        composeRule.onNodeWithText("No campsites are available for this lottery.")
            .assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("reserve_choose_campsite_prompt").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("button_reserve").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("reserve_decline").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirm").performClick()
        waitForTag("reserve_declined")

        assertEquals(EntryStatus.DECLINED, samsStatus())
        runBlocking {
            assertEquals(EntryStatus.WON, lotteries.getEntry("lot-summer", "user-jo")?.status)
        }
    }
}
