/*
 * BrowseCampsitesScreenTest.kt
 * UI tests for BrowseCampsitesScreen (US 03.04.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseCampsitesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun campsitesBelongingToDifferentOwners_appearInList() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View"),
            Campsite(id = "c2", ownerId = "owner2", name = "Lakeside Camp")
        )
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val profileRepo = FakeProfileRepository(
            listOf(
                UserProfile(id = "owner1", name = "Alice Smith"),
                UserProfile(id = "owner2", name = "Bob Jones")
            )
        )
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Mountain View").assertIsDisplayed()
        composeRule.onNodeWithText("Lakeside Camp").assertIsDisplayed()
    }

    @Test
    fun eachCampsiteDisplaysNameLocationAndOwnerDisplayName() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View", address = "Banff, AB"),
            Campsite(id = "c2", ownerId = "owner2", name = "Lakeside Camp", address = "Jasper, AB")
        )
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val profileRepo = FakeProfileRepository(
            listOf(
                UserProfile(id = "owner1", name = "Alice Smith"),
                UserProfile(id = "owner2", name = "Bob Jones")
            )
        )
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Mountain View").assertIsDisplayed()
        composeRule.onNodeWithText("Banff, AB · Alice Smith").assertIsDisplayed()

        composeRule.onNodeWithText("Lakeside Camp").assertIsDisplayed()
        composeRule.onNodeWithText("Jasper, AB · Bob Jones").assertIsDisplayed()
    }

    @Test
    fun searchFieldFiltersCampsitesCorrectly() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View"),
            Campsite(id = "c2", ownerId = "owner2", name = "Lakeside Camp")
        )
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val profileRepo = FakeProfileRepository()
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Search campsites").performTextInput("Mountain")

        composeRule.onNodeWithText("Mountain View").assertIsDisplayed()
        composeRule.onNodeWithText("Lakeside Camp").assertDoesNotExist()
    }

    @Test
    fun emptyRepositoryDisplaysNoCampsitesFoundMessage() {
        val campsiteRepo = FakeCampsiteRepository(emptyList())
        val profileRepo = FakeProfileRepository()
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("No campsites found.").assertIsDisplayed()
    }

    @Test
    fun searchWithNoMatchingCampsitesDisplaysNoMatchingCampsitesMessage() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View")
        )
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val profileRepo = FakeProfileRepository()
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Search campsites").performTextInput("NonExistent")

        composeRule.onNodeWithText("No matching campsites.").assertIsDisplayed()
    }

    @Test
    fun clickingBackButtonInvokesOnBackCallback() {
        val campsiteRepo = FakeCampsiteRepository(emptyList())
        val profileRepo = FakeProfileRepository()
        val controller = AdminController(campsiteRepo, profileRepo)
        var backClicked = false

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(onBack = { backClicked = true }, controller = controller)
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backClicked)
    }

    @Test
    fun clickingCampsiteRowInvokesOnOpenCampsiteCallback() {
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View", address = "Banff, AB")
        )
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val profileRepo = FakeProfileRepository()
        val controller = AdminController(campsiteRepo, profileRepo)
        var openedCampsiteId: String? = null

        composeRule.setContent {
            VipersTheme {
                BrowseCampsitesScreen(
                    onBack = {},
                    onOpenCampsite = { openedCampsiteId = it },
                    controller = controller
                )
            }
        }

        composeRule.onNodeWithText("Mountain View").performClick()

        assertEquals("c1", openedCampsiteId)
    }
}
