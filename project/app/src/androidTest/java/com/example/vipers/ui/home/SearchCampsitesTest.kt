/*
 * SearchCampsitesTest.kt
 * Intent tests for US 01.01.01: searching campsites by name on Home, one test per acceptance
 * criterion. Uses the fake campsite and lottery repositories, so it doesn't touch Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchCampsitesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campsites = listOf(
        Campsite(id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB", region = Region.HINTON),
        Campsite(id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB", region = Region.HINTON),
        Campsite(id = "3", name = "Bow Valley Campground", address = "Banff, AB", region = Region.BANFF),
        Campsite(id = "4", name = "Hinton Old Mill", address = "Hinton, AB", region = Region.HINTON, removed = true),
    )

    @Before
    fun openHome() {
        val controller = CampsiteSearchController(
            FakeCampsiteRepository(campsites),
            FakeLotteryRepository(),
        )
        composeRule.setContent { VipersTheme { HomeScreen(controller) } }
    }

    private fun search(text: String) = composeRule.onNodeWithTag("search_field").performTextInput(text)

    private fun assertShown(text: String) = composeRule.onNodeWithText(text).assertIsDisplayed()

    private fun assertNotShown(text: String) = composeRule.onNodeWithText(text).assertDoesNotExist()

    private fun assertResultCount(count: Int) =
        composeRule.onAllNodesWithTag("campsite_result").assertCountEquals(count)

    @Test
    fun homeHasSearchBarAtTheTop() {
        composeRule.onNodeWithTag("search_field").assertIsDisplayed()
        assertShown("Search campsites")
    }

    @Test
    fun emptySearchShowsAllCampsites() {
        assertResultCount(3)
        assertShown("Hinton Ridge Campground")
        assertShown("Hinton Lakeside Sites")
        assertShown("Bow Valley Campground")
    }

    @Test
    fun typingShowsOnlyNamesStartingWithTheText() {
        search("Hinton")
        assertResultCount(2)
        assertShown("Hinton Ridge Campground")
        assertShown("Hinton Lakeside Sites")
        assertNotShown("Bow Valley Campground")
    }

    @Test
    fun searchIgnoresUpperAndLowerCase() {
        search("bOW")
        assertResultCount(1)
        assertShown("Bow Valley Campground")
    }

    @Test
    fun noMatchShowsNoCampsitesFound() {
        search("lake louise")
        assertResultCount(0)
        assertShown("No campsites found")
    }

    @Test
    fun removedCampsitesNeverAppear() {
        assertNotShown("Hinton Old Mill")
        search("Hinton Old")
        assertNotShown("Hinton Old Mill")
        assertShown("No campsites found")
    }

    @Test
    fun clearingTheSearchShowsAllCampsitesAgain() {
        search("Bow")
        assertResultCount(1)
        composeRule.onNodeWithTag("search_field").performTextClearance()
        assertResultCount(3)
    }
}
