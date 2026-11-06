/*
 * FilterCampsitesTest.kt
 * Intent tests for US 01.01.02: filtering campsites by region, date range, and guests on Home,
 * one test per acceptance criterion. Uses the fake campsite and lottery repositories, so it
 * doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FilterCampsitesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campsites = listOf(
        Campsite(id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 6),
        Campsite(id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 4),
        Campsite(id = "3", name = "Bow Valley Campground", address = "Banff, AB", region = Region.BANFF, maxOccupancy = 2),
        Campsite(id = "4", name = "Hinton Old Mill", address = "Hinton, AB", region = Region.HINTON, removed = true),
    )

    private lateinit var campsiteRepository: FakeCampsiteRepository

    @Before
    fun openHome() = runBlocking {
        campsiteRepository = FakeCampsiteRepository(campsites)
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01", "2027-06-02")),
        )
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(campsiteId = "2", openNights = listOf("2027-06-05"), heldNights = listOf("2027-06-05")),
        )
        campsiteRepository.saveAvailability(
            AvailabilityCalendar(campsiteId = "3", openNights = listOf("2027-06-10", "2027-06-11")),
        )

        val controller = CampsiteSearchController(
            campsiteRepository,
            FakeLotteryRepository(),
        )
        composeRule.setContent { VipersTheme { HomeScreen(controller) } }
    }

    private fun openFilters() = composeRule.onNodeWithTag("button_filters").performClick()

    private fun applyFilters() = composeRule.onNodeWithTag("button_apply_filters").performClick()

    private fun clearFilters() = composeRule.onNodeWithTag("button_clear_filters").performClick()

    private fun search(text: String) = composeRule.onNodeWithTag("search_field").performTextInput(text)

    private fun assertShown(text: String) = composeRule.onNodeWithText(text).assertIsDisplayed()

    private fun assertNotShown(text: String) = composeRule.onNodeWithText(text).assertDoesNotExist()

    private fun assertResultCount(count: Int) =
        composeRule.onAllNodesWithTag("campsite_result").assertCountEquals(count)

    @Test
    fun filtersButtonOpensFilterDialog() {
        composeRule.onNodeWithTag("button_filters").assertIsDisplayed()
        openFilters()

        assertShown("Filters")
        assertShown("Region")
        assertShown("Dates")
        assertShown("Guests")
        composeRule.onNodeWithTag("button_apply_filters").assertIsDisplayed()
        composeRule.onNodeWithTag("button_clear_filters").assertIsDisplayed()
    }

    @Test
    fun regionFilterShowsOnlyCampsitesInChosenRegion() {
        openFilters()
        composeRule.onNodeWithTag("field_filter_region").performClick()
        composeRule.onNodeWithText("Banff").performClick()
        applyFilters()

        assertResultCount(1)
        assertShown("Bow Valley Campground")
        assertNotShown("Hinton Ridge Campground")
        assertNotShown("Hinton Lakeside Sites")
    }

    @Test
    fun dateFilterShowsOnlyCampsitesWithAvailableNight() {
        openFilters()
        composeRule.onNodeWithTag("field_filter_dates").performTextInput("2027-06-01 to 2027-06-03")
        applyFilters()

        // Only campsite 1 has an open night in that range
        assertResultCount(1)
        assertShown("Hinton Ridge Campground")
        assertNotShown("Bow Valley Campground")
        assertNotShown("Hinton Lakeside Sites")

        // Date range where campsite 2 has an open night, but it is held by confirmed reservation
        openFilters()
        composeRule.onNodeWithTag("field_filter_dates").performTextClearance()
        composeRule.onNodeWithTag("field_filter_dates").performTextInput("2027-06-04 to 2027-06-06")
        applyFilters()

        assertResultCount(0)
        assertShown("No campsites found")
    }

    @Test
    fun guestFilterHidesCampsitesBelowChosenNumber() {
        openFilters()
        // Increase guest count to 5 using the plus button
        repeat(5) {
            composeRule.onNodeWithTag("button_guests_plus").performClick()
        }
        applyFilters()

        // Only campsite 1 (maxOccupancy = 6) is >= 5 guests
        assertResultCount(1)
        assertShown("Hinton Ridge Campground")
        assertNotShown("Hinton Lakeside Sites")
        assertNotShown("Bow Valley Campground")
    }

    @Test
    fun filtersCanBeCombinedWithEachOtherAndSearchText() {
        openFilters()
        // Filter by region Hinton
        composeRule.onNodeWithTag("field_filter_region").performClick()
        composeRule.onNodeWithText("Hinton").performClick()

        // Filter by guests at least 4
        repeat(4) {
            composeRule.onNodeWithTag("button_guests_plus").performClick()
        }
        applyFilters()

        // Campsite 1 (6 guests) and Campsite 2 (4 guests) match
        assertResultCount(2)
        assertShown("Hinton Ridge Campground")
        assertShown("Hinton Lakeside Sites")

        // Typing search text narrows the filtered results
        search("Hinton L")
        assertResultCount(1)
        assertShown("Hinton Lakeside Sites")
        assertNotShown("Hinton Ridge Campground")
    }

    @Test
    fun clearFiltersRestoresFullList() {
        openFilters()
        composeRule.onNodeWithTag("field_filter_region").performClick()
        composeRule.onNodeWithText("Banff").performClick()
        applyFilters()
        assertResultCount(1)

        openFilters()
        clearFilters()

        // Restores all 3 unremoved campsites
        assertResultCount(3)
        assertShown("Bow Valley Campground")
        assertShown("Hinton Lakeside Sites")
        assertShown("Hinton Ridge Campground")
        composeRule.onNodeWithTag("button_clear_filters_bar").assertDoesNotExist()
    }

    @Test
    fun clearFiltersFromActiveBarRestoresFullList() {
        openFilters()
        composeRule.onNodeWithTag("field_filter_region").performClick()
        composeRule.onNodeWithText("Banff").performClick()
        applyFilters()
        assertResultCount(1)

        composeRule.onNodeWithTag("button_clear_filters_bar").performClick()
        assertResultCount(3)
    }
}
