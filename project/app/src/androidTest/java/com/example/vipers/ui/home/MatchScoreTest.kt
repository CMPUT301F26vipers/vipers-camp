/*
 * MatchScoreTest.kt
 * Intent tests for preferred tags, date-proximity scoring, match percentages and Home sorting
 * (W-01, E02/E03). Uses fake campsite and lottery repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MatchScoreTest {
    @get:Rule
    val screen = createComposeRule()

    private val ridge = Campsite(
        id = "ridge", name = "Hinton Ridge Campground", address = "Hinton, AB",
        region = Region.HINTON, maxOccupancy = 6, pricePerNightCents = 6500,
        tags = listOf("Cookhouse", "Tennis court"), ratingSum = 9, ratingCount = 2,
    )
    private val lake = ridge.copy(
        id = "lake", name = "Aspen Lake Campground", tags = listOf("Lake access", "Showers"),
    )

    private fun openHome(
        campsites: List<Campsite>,
        calendars: List<AvailabilityCalendar>,
    ) {
        val sites = FakeCampsiteRepository(campsites)
        runBlocking {
            calendars.forEach { sites.saveAvailability(it) }
        }
        val controller = CampsiteSearchController(sites, FakeLotteryRepository())
        screen.setContent { VipersTheme { HomeScreen(controller) } }
    }

    private fun filters() = screen.onNodeWithTag("button_filters").performClick()

    private fun select(tag: String) = screen.onNodeWithText(tag).performScrollTo().performClick()

    private fun apply() = screen.onNodeWithTag("button_apply_filters").performClick()

    private fun enterDates(text: String) {
        screen.onNodeWithTag("field_filter_dates").performTextInput(text)
        Espresso.closeSoftKeyboard()
    }

    private fun assertBefore(first: Campsite, second: Campsite) {
        val top = screen.onNodeWithText(first.name).fetchSemanticsNode().boundsInRoot.top
        val below = screen.onNodeWithText(second.name).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top < below)
    }

    @Test
    fun preferredTagRanksBothSitesAndClearRestoresNameOrder() {
        val today = LocalDate.now().toString()
        openHome(
            listOf(lake, ridge),
            listOf(
                AvailabilityCalendar(ridge.id, listOf(today)),
                AvailabilityCalendar(lake.id, listOf(today)),
            ),
        )
        assertBefore(lake, ridge)
        screen.onNodeWithText("% match", substring = true).assertDoesNotExist()
        filters()
        select("Cookhouse")
        apply()
        assertBefore(ridge, lake)
        screen.onNodeWithText("98% match").assertIsDisplayed()
        screen.onNodeWithText("68% match").assertIsDisplayed()
        screen.onNodeWithText("Sort: Best match").performClick()
        screen.onNodeWithText("Name").performClick()
        assertBefore(lake, ridge)
        screen.onNodeWithText("Sort: Name").performClick()
        screen.onNodeWithText("Best match").performClick()
        assertBefore(ridge, lake)
        screen.onNodeWithTag("button_clear_filters_bar").performClick()
        assertBefore(lake, ridge)
        screen.onNodeWithText("% match", substring = true).assertDoesNotExist()
        screen.onNodeWithText("Sort: Best match").assertDoesNotExist()
    }

    @Test
    fun requestedDatesAndTwoTagsShowDifferentFreeNightShares() {
        val nights = listOf("2027-05-08", "2027-05-09")
        openHome(
            listOf(lake, ridge),
            listOf(
                AvailabilityCalendar(ridge.id, nights),
                AvailabilityCalendar(lake.id, nights, listOf(nights.last())),
            ),
        )
        filters()
        enterDates("2027-05-08 to 2027-05-09")
        select("Cookhouse")
        select("Showers")
        apply()
        screen.onNodeWithText("83% match").assertIsDisplayed()
        screen.onNodeWithText("58% match").assertIsDisplayed()
        filters()
        screen.onNodeWithText("Cookhouse").performScrollTo().assertIsSelected()
        screen.onNodeWithText("Showers").performScrollTo().assertIsSelected()
    }

    @Test
    fun preferredTagsDoNotBringBackASiteOutsideTheRequestedDates() {
        val nights = listOf("2027-05-08", "2027-05-09")
        openHome(
            listOf(lake, ridge),
            listOf(
                AvailabilityCalendar(ridge.id, nights),
                AvailabilityCalendar(lake.id, nights, listOf(nights.last())),
            ),
        )
        filters()
        enterDates("2027-05-09")
        select("Showers")
        apply()
        screen.onAllNodesWithTag("campsite_result").assertCountEquals(1)
        screen.onNodeWithText(ridge.name).assertIsDisplayed()
        screen.onNodeWithText(lake.name).assertDoesNotExist()
    }

    @Test
    fun dateProximityRanksEarlierAndAdjacentAvailabilityHigher() {
        val cedar = ridge.copy(id = "cedar", name = "Cedar Creek Campground")
        val birch = ridge.copy(id = "birch", name = "Birch Point Campground")
        val aspen = ridge.copy(id = "aspen", name = "Aspen Late Campground")
        openHome(
            listOf(aspen, birch, cedar),
            listOf(
                AvailabilityCalendar(cedar.id, listOf("2027-05-08", "2027-05-10")),
                AvailabilityCalendar(birch.id, listOf("2027-05-08")),
                AvailabilityCalendar(aspen.id, listOf("2027-05-09")),
            ),
        )
        filters()
        enterDates("2027-05-08 to 2027-05-09")
        apply()
        assertBefore(cedar, birch)
        assertBefore(birch, aspen)
        screen.onNodeWithText("78% match").assertIsDisplayed()
        screen.onNodeWithText("73% match").assertIsDisplayed()
        screen.onNodeWithText("68% match").assertIsDisplayed()
    }

    @Test
    fun noDatesBiasRanksSoonerUpcomingAvailabilityHigher() {
        val today = LocalDate.now()
        val later = lake.copy(id = "later", name = "Aspen Later Campground")
        openHome(
            listOf(later, ridge),
            listOf(
                AvailabilityCalendar(ridge.id, listOf(today.toString())),
                AvailabilityCalendar(later.id, listOf(today.plusDays(30).toString())),
            ),
        )
        filters()
        screen.onNodeWithTag("field_filter_region").performClick()
        screen.onNodeWithText("Hinton").performClick()
        apply()
        assertBefore(ridge, later)
        screen.onNodeWithText("98% match").assertIsDisplayed()
        screen.onNodeWithText("93% match").assertIsDisplayed()
    }
}
