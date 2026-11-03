/*
 * CampsiteSummaryTest.kt
 * Intent tests for US 01.01.04: each Home result shows a summary card with the campsite's name,
 * location, maximum occupancy, rating and whether it has an open lottery, one test per acceptance
 * criterion. Uses the fake campsite and lottery repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CampsiteSummaryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longName = "Whistlers Mountain View Family Campground and Group Tenting Area"
    private val longAddress =
        "Km 3.5 Whistlers Campground Road, Jasper National Park, Jasper, AB T0E 1E0"

    private val campsites = listOf(
        // 46 stars over 10 ratings is 4.6
        Campsite(
            id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB",
            region = Region.HINTON, maxOccupancy = 8, ratingSum = 46, ratingCount = 10,
        ),
        Campsite(
            id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB",
            region = Region.HINTON, maxOccupancy = 6,
        ),
        Campsite(
            id = "3", name = longName, address = longAddress,
            region = Region.JASPER, maxOccupancy = 4, ratingSum = 9, ratingCount = 2,
        ),
    )

    // Home checks the lotteries against the real clock, so the periods are set around today
    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    private val lotteries = listOf(
        // Hinton Ridge: registration opened 2 days ago and closes in 3 days
        Lottery(
            id = "open", campsiteIds = listOf("1"),
            registrationStart = now - 2 * day, registrationEnd = now + 3 * day,
            bookingStart = now + 5 * day, bookingEnd = now + 30 * day,
        ),
        // the Jasper campsite: registration only starts in a week
        Lottery(
            id = "upcoming", campsiteIds = listOf("3"),
            registrationStart = now + 7 * day, registrationEnd = now + 12 * day,
            bookingStart = now + 14 * day, bookingEnd = now + 40 * day,
        ),
    )

    @Before
    fun openHome() {
        val controller = CampsiteSearchController(
            FakeCampsiteRepository(campsites),
            FakeLotteryRepository(lotteries),
        )
        composeRule.setContent { VipersTheme { HomeScreen(controller) } }
    }

    /** The result card for the campsite called [name]. */
    private fun card(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasTestTag("campsite_result") and hasAnyDescendant(hasText(name)))

    private fun SemanticsNodeInteraction.assertShows(text: String) =
        assert(hasAnyDescendant(hasText(text)))

    @Test
    fun eachResultShowsNameLocationOccupancyAndRating() {
        card("Hinton Ridge Campground").assertIsDisplayed()
            .assertShows("Hinton, AB")
            .assertShows("8 guests · ★ 4.6")
    }

    @Test
    fun campsiteWithNoRatingsShowsNoRatingsYet() {
        card("Hinton Lakeside Sites").assertShows("6 guests · No ratings yet")
    }

    @Test
    fun eachResultShowsWhetherItHasAnActiveLottery() {
        card("Hinton Ridge Campground").assertShows("Lottery open")
        card("Hinton Lakeside Sites").assertShows("No lottery")
        // its lottery hasn't opened for registration yet, so entrants can't join it
        card(longName).assertShows("No lottery")
    }

    @Test
    fun longNamesAndLocationsDoNotBreakTheCard() {
        // the long name and location are cut short, so the rest of the card still shows
        card(longName).assertIsDisplayed()
            .assertShows(longAddress)
            .assertShows("4 guests · ★ 4.5")
            .assertShows("No lottery")
    }

    @Test
    fun summaryStaysWithItsCampsiteWhileSearching() {
        composeRule.onNodeWithTag("search_field").performTextInput("Hinton R")
        card("Hinton Ridge Campground")
            .assertShows("8 guests · ★ 4.6")
            .assertShows("Lottery open")
        composeRule.onNodeWithText("Hinton Lakeside Sites").assertDoesNotExist()
    }
}
