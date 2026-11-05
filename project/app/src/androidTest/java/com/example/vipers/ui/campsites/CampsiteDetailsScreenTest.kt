/*
 * CampsiteDetailsScreenTest.kt
 *
 * Contains instrumented UI tests for the CampsiteDetailsScreen to verify
 * correct layout rendering and user interaction callbacks.
 *
 * Known issues: None.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import junit.framework.TestCase
import org.junit.Rule
import org.junit.Test

/**
 * Validates the visual states and click interactions for the details screen of a specific campsite.
 */
class CampsiteDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screen_whenCampsiteNotFound_displaysLoadingText() {
        // 1. ARRANGE: Empty repo so viewModel.campsite stays null
        val fakeRepo = FakeCampsiteRepository(initial = emptyList())
        val viewModel = CampsiteDetailsViewModel(fakeRepo, "missingId")

        // 2. ACT
        composeTestRule.setContent {
            CampsiteDetailsScreen(
                campsiteId = "missingId",
                onBack = {},
                viewModel = viewModel
            )
        }

        // 3. ASSERT
        composeTestRule.onNodeWithText("Loading...").assertIsDisplayed()
    }

    @Test
    fun screen_whenCampsiteLoaded_displaysAllDetailsAndSections() {
        // 1. ARRANGE
        val targetId = "camp1"
        val dummyData = listOf(
            Campsite(
                id = targetId,
                ownerId = "user12",
                name = "Maligne Lake Camp",
                region = Region.JASPER
            )
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = CampsiteDetailsViewModel(fakeRepo, targetId)

        // 2. ACT
        composeTestRule.setContent {
            CampsiteDetailsScreen(
                campsiteId = targetId,
                onBack = {},
                viewModel = viewModel
            )
        }

        // 3. ASSERT
        // TopBar & BottomBar
        composeTestRule.onNodeWithText("My Campsite").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Lottery").assertIsDisplayed()

        // Gallery & Map Placeholders
        composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Map").assertIsDisplayed()

        // Campsite Name & 2x2 Action Buttons
        composeTestRule.onNodeWithText("Maligne Lake Camp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("QR Code").assertIsDisplayed()
        composeTestRule.onNodeWithText("Availability").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reservations").assertIsDisplayed()

        // Lotteries Section
        composeTestRule.onNodeWithText("Lotteries").assertIsDisplayed()
        composeTestRule.onNodeWithText("May weekends").assertIsDisplayed()
        composeTestRule.onNodeWithText("September").assertIsDisplayed()

        // Details Section (scrolls if needed, or checks existence on screen)
        composeTestRule.onNodeWithText("Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("JASPER · Up to 6 guests · $50 / night").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cookhouse").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tennis court").assertIsDisplayed()
    }

    @Test
    fun actionButtons_whenClicked_triggerCallbacks() {
        // 1. ARRANGE
        val targetId = "camp1"
        val dummyData = listOf(
            Campsite(id = targetId, ownerId = "user12", name = "Maligne Lake Camp", region = Region.JASPER)
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = CampsiteDetailsViewModel(fakeRepo, targetId)

        var editClicked = false
        var qrClicked = false
        var availabilityClicked = false
        var reservationsClicked = false
        var createLotteryClicked = false

        // 2. ACT
        composeTestRule.setContent {
            CampsiteDetailsScreen(
                campsiteId = targetId,
                onBack = {},
                onEditClick = { editClicked = true },
                onQrCodeClick = { qrClicked = true },
                onAvailabilityClick = { availabilityClicked = true },
                onReservationsClick = { reservationsClicked = true },
                onCreateLotteryClick = { createLotteryClicked = true },
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Edit").performClick()
        composeTestRule.onNodeWithText("QR Code").performClick()
        composeTestRule.onNodeWithText("Availability").performClick()
        composeTestRule.onNodeWithText("Reservations").performClick()
        composeTestRule.onNodeWithText("Create Lottery").performClick()

        // 3. ASSERT
        TestCase.assertTrue(editClicked)
        TestCase.assertTrue(qrClicked)
        TestCase.assertTrue(availabilityClicked)
        TestCase.assertTrue(reservationsClicked)
        TestCase.assertTrue(createLotteryClicked)
    }
}