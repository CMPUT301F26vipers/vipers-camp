/*
 * ViewFilteredCampsitesTest.kt
 * Intent tests for US 01.01.03: viewing a filtered campsite list on Home, one test per acceptance
 * criterion:
 * 1. Results appear as a scrollable list that updates when search text or filters change.
 * 2. The number of results is shown, or an empty-state message when there are none.
 * 3. A loading indicator is shown while results load.
 * 4. Tapping a result opens that campsite's details (01.01.05).
 * Uses fake repositories, so it doesn't touch Firestore.
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
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ViewFilteredCampsitesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campsites = listOf(
        Campsite(id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 6),
        Campsite(id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 4),
        Campsite(id = "3", name = "Bow Valley Campground", address = "Banff, AB", region = Region.BANFF, maxOccupancy = 2),
        Campsite(id = "4", name = "Hinton Old Mill", address = "Hinton, AB", region = Region.HINTON, removed = true),
    )

    private fun setupHome(
        onOpenCampsite: (String) -> Unit = {},
        campsiteRepo: CampsiteRepository? = null,
    ): CampsiteSearchController {
        val repo = campsiteRepo ?: FakeCampsiteRepository(campsites).apply {
            runBlocking {
                saveAvailability(
                    AvailabilityCalendar(campsiteId = "1", openNights = listOf("2027-06-01", "2027-06-02")),
                )
                saveAvailability(
                    AvailabilityCalendar(campsiteId = "2", openNights = listOf("2027-06-05")),
                )
            }
        }
        val controller = CampsiteSearchController(repo, FakeLotteryRepository())
        composeRule.setContent {
            VipersTheme {
                HomeScreen(controller = controller, onOpenCampsite = onOpenCampsite)
            }
        }
        return controller
    }

    private fun search(text: String) = composeRule.onNodeWithTag("search_field").performTextInput(text)

    private fun clearSearch() = composeRule.onNodeWithTag("search_field").performTextClearance()

    private fun assertResultCount(count: Int) =
        composeRule.onAllNodesWithTag("campsite_result").assertCountEquals(count)

    @Test
    fun resultsListUpdatesWhenSearchTextOrFiltersChange() {
        val controller = setupHome()

        // Initially all 3 active campsites are shown in the list
        composeRule.onNodeWithTag("campsite_list").assertIsDisplayed()
        assertResultCount(3)
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Bow Valley Campground").assertIsDisplayed()

        // 1. Search text changes
        search("Bow")
        assertResultCount(1)
        composeRule.onNodeWithText("Bow Valley Campground").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").assertDoesNotExist()

        clearSearch()
        assertResultCount(3)

        // 2. Filter changes
        composeRule.onNodeWithTag("button_filters").performClick()
        composeRule.onNodeWithTag("field_filter_region").performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        composeRule.onNodeWithTag("button_apply_filters").performClick()

        assertResultCount(2)
        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Lakeside Sites").assertIsDisplayed()
        composeRule.onNodeWithText("Bow Valley Campground").assertDoesNotExist()

        // Clear filters via active filters bar
        composeRule.onNodeWithTag("button_clear_filters_bar").performClick()
        assertResultCount(3)
    }

    @Test
    fun numberOfResultsIsShownAboveList() {
        setupHome()

        // Shows total number of results when multiple match
        composeRule.onNodeWithTag("text_result_count").assertIsDisplayed()
        composeRule.onNodeWithText("3 campsites").assertIsDisplayed()

        // Updates to singular when only one matches
        search("Bow")
        composeRule.onNodeWithTag("text_result_count").assertIsDisplayed()
        composeRule.onNodeWithText("1 campsite").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoCampsitesMatch() {
        setupHome()

        search("Unknown Campground")
        assertResultCount(0)

        // Result count label is hidden, empty state message is shown
        composeRule.onNodeWithTag("text_result_count").assertDoesNotExist()
        composeRule.onNodeWithText("No campsites found").assertIsDisplayed()
        composeRule.onNodeWithText("Check the spelling, or clear the search.").assertIsDisplayed()
    }

    @Test
    fun loadingIndicatorShownWhileResultsLoad() {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakeCampsiteRepository(campsites)
        val suspendingRepo = object : CampsiteRepository by delegate {
            override suspend fun getAllCampsites(): List<Campsite> {
                gate.await()
                return delegate.getAllCampsites()
            }
        }

        setupHome(campsiteRepo = suspendingRepo)

        // While the repository is awaiting the gate, loading indicator is shown
        composeRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("campsite_list").assertDoesNotExist()

        // Unblock load and verify results appear while indicator disappears
        gate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("loading_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("campsite_list").assertIsDisplayed()
        assertResultCount(3)
    }

    @Test
    fun tappingResultOpensCampsiteDetails() {
        var clickedCampsiteId: String? = null
        setupHome(onOpenCampsite = { clickedCampsiteId = it })

        composeRule.onNodeWithText("Hinton Ridge Campground").performClick()
        assertEquals("1", clickedCampsiteId)
    }
}
