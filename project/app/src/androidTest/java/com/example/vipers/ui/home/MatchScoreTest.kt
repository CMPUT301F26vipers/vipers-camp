/*
 * MatchScoreTest.kt
 * Intent tests for preferred tags, match percentages and Home sorting (W-01, E02/E03).
 * Uses fake campsite and lottery repositories.
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
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

    @Before
    fun openHome() {
        val sites = FakeCampsiteRepository(listOf(lake, ridge))
        val nights = listOf("2027-05-08", "2027-05-09")
        runBlocking {
            sites.saveAvailability(AvailabilityCalendar(ridge.id, nights))
            sites.saveAvailability(AvailabilityCalendar(lake.id, nights, listOf(nights.last())))
        }
        val controller = CampsiteSearchController(sites, FakeLotteryRepository())
        screen.setContent { VipersTheme { HomeScreen(controller) } }
    }

    private fun filters() = screen.onNodeWithTag("button_filters").performClick()

    private fun select(tag: String) = screen.onNodeWithText(tag).performScrollTo().performClick()

    private fun apply() = screen.onNodeWithTag("button_apply_filters").performClick()

    private fun assertBefore(first: Campsite, second: Campsite) {
        val top = screen.onNodeWithText(first.name).fetchSemanticsNode().boundsInRoot.top
        val below = screen.onNodeWithText(second.name).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top < below)
    }

    @Test
    fun preferredTagRanksBothSitesAndClearRestoresNameOrder() {
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
        filters()
        screen.onNodeWithTag("field_filter_dates").performTextInput("2027-05-08 to 2027-05-09")
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
        filters()
        screen.onNodeWithTag("field_filter_dates").performTextInput("2027-05-09")
        select("Showers")
        apply()
        screen.onAllNodesWithTag("campsite_result").assertCountEquals(1)
        screen.onNodeWithText(ridge.name).assertIsDisplayed()
        screen.onNodeWithText(lake.name).assertDoesNotExist()
    }
}
