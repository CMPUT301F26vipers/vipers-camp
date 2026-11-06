/*
 * WaitingListTest.kt
 * Intent tests for US 02.09.01: the owner opens a lottery's Waiting List (mockup O11) from
 * Lottery Details (O09) and sees each entrant's name, group size and join date, the total against
 * the limit, results after the draw, and new entrants appearing live. Uses the fake repositories,
 * so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaitingListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    // Priya's Hinton lottery from scenario 2: registration open now, limit of 10
    private val openLottery = Lottery(
        id = "hinton-may",
        ownerId = "owner-priya",
        campsiteIds = listOf("hinton"),
        nightsDescription = "May 2027 weekends",
        registrationStart = now - 2 * day,
        registrationEnd = now + 3 * day,
        bookingStart = now + 4 * day,
        bookingEnd = now + 10 * day,
        winnersCount = 1,
        waitlistCapacity = 10,
    )

    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "owner-priya", name = "Priya Sharma"),
            UserProfile(id = "sam", name = "Sam Patel"),
            UserProfile(id = "aisha", name = "Aisha Khan"),
            UserProfile(id = "liam", name = "Liam Chen"),
        ),
    )

    private val sam = entry("sam", joinedAt = now - 2 * day, groupSize = 2)
    private val aisha = entry("aisha", joinedAt = now - day, groupSize = 4)

    private fun entry(userId: String, joinedAt: Long, groupSize: Int, status: EntryStatus = EntryStatus.WAITING) =
        LotteryEntry(
            id = LotteryEntry.idFor("hinton-may", userId),
            lotteryId = "hinton-may",
            userId = userId,
            groupSize = groupSize,
            joinedAt = joinedAt,
            status = status,
        )

    /** Shows the Waiting List screen for the Hinton lottery, backed by [repo]. */
    private fun openWaitingList(repo: FakeLotteryRepository) {
        val controller = WaitingListController(repo, profiles, "hinton-may")
        composeRule.setContent {
            VipersTheme { WaitingListScreen("hinton-may", onBack = {}, controller = controller) }
        }
    }

    // the lists here are short, so everything fits on screen without scrolling
    private fun assertShown(text: String) =
        composeRule.onNodeWithText(text).assertIsDisplayed()

    @Test
    fun showsEachEntrantsNameGroupSizeAndJoinDate() {
        openWaitingList(FakeLotteryRepository(listOf(openLottery), listOf(sam, aisha)))

        assertShown("Sam Patel")
        assertShown(entrantDetails(sam))
        assertShown("Aisha Khan")
        assertShown(entrantDetails(aisha))
    }

    @Test
    fun showsTheTotalAgainstTheLimit() {
        openWaitingList(FakeLotteryRepository(listOf(openLottery), listOf(sam, aisha)))

        composeRule.onNodeWithTag("waiting_list_count").assertIsDisplayed()
        assertShown("2 of 10 joined")
    }

    @Test
    fun showsTheTotalOnlyWhenThereIsNoLimit() {
        openWaitingList(FakeLotteryRepository(listOf(openLottery.copy(waitlistCapacity = null)), listOf(sam)))

        assertShown("1 joined")
    }

    @Test
    fun afterTheDrawShowsEachEntrantsResult() {
        val drawn = openLottery.copy(drawnAt = now - day / 2)
        openWaitingList(
            FakeLotteryRepository(
                listOf(drawn),
                listOf(
                    sam.copy(status = EntryStatus.WON),
                    aisha.copy(status = EntryStatus.LOST),
                    entry("liam", now - day / 4, 3, EntryStatus.DECLINED),
                ),
            ),
        )

        assertShown("Won")
        assertShown("Lost")
        assertShown("Declined")
        composeRule.onNodeWithText("3 of 10 joined · drawn", substring = true).assertIsDisplayed()
    }

    @Test
    fun newEntrantAppearsWithoutReopeningTheScreen() {
        val repo = FakeLotteryRepository(listOf(openLottery), listOf(sam))
        openWaitingList(repo)
        assertShown("1 of 10 joined")

        // Liam joins from another phone while the owner is watching
        runBlocking { repo.saveEntry(entry("liam", now, groupSize = 3)) }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Liam Chen").fetchSemanticsNodes().isNotEmpty()
        }
        assertShown("Liam Chen")
        assertShown("2 of 10 joined")
    }

    @Test
    fun entrantWhoLeavesDisappearsFromTheList() {
        val repo = FakeLotteryRepository(listOf(openLottery), listOf(sam, aisha))
        openWaitingList(repo)
        assertShown("Aisha Khan")

        runBlocking { repo.saveEntry(aisha.copy(status = EntryStatus.REMOVED)) }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Aisha Khan").fetchSemanticsNodes().isEmpty()
        }
        assertShown("1 of 10 joined")
    }

    @Test
    fun emptyWaitingListSaysNoOneHasJoined() {
        openWaitingList(FakeLotteryRepository(listOf(openLottery)))

        assertShown("0 of 10 joined")
        assertShown("No one has joined this waiting list yet.")
    }

    @Test
    fun viewWaitingListOnLotteryDetailsOpensTheWaitingList() {
        var opened = false
        val controller = LotteryDetailsController(
            lotteryRepository = FakeLotteryRepository(listOf(openLottery), listOf(sam)),
            profileRepository = profiles,
            lotteryId = "hinton-may",
            currentUserId = "owner-priya",
        )
        composeRule.setContent {
            VipersTheme {
                LotteryDetailsScreen(
                    lotteryId = "hinton-may",
                    onBack = {},
                    onViewWaitingList = { opened = true },
                    controller = controller,
                )
            }
        }

        composeRule.onNodeWithText("View Waiting List").performScrollTo().performClick()

        assertTrue(opened)
    }
}
