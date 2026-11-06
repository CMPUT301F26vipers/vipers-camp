/*
 * DeclineWinTest.kt
 * Intent tests for US 01.04.03: a winner declines their win from the Reserve screen (mockups E11
 * and E12). Cancel in the dialog changes nothing, Confirm marks the entry DECLINED and replaces the
 * button, and a reserved win shows no Decline button. Uses the fake repositories, so it doesn't
 * touch Firestore.
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
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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

    /**
     * Shows the Reserve screen for Sam's win, whose entry has [status], and waits for it to load.
     * The controller is built with fake repositories and passed straight to the screen.
     */
    private fun openReserve(status: EntryStatus) {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        runBlocking {
            campsites.saveAvailability(AvailabilityCalendar(campsiteId = "hinton", openNights = nights))
        }
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
            ),
        )
        val controller = ReserveController(
            campsites, lotteries, userId = "test-device", lotteryId = "lot-summer", campsiteId = "hinton",
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
}
