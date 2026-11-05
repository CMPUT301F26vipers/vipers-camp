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
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
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
        val dummyCampsite = Campsite(
            id = targetId,
            ownerId = "user12",
            name = "Maligne Lake Camp",
            address = "Maligne Lake Rd, Jasper, AB",
            region = Region.JASPER,
            description = "Lakeside tent sites with canoe rentals nearby.",
            maxOccupancy = 6,
            tags = listOf("Cookhouse", "Tennis court"),
            pricePerNightCents = 5000,
            rules = "Quiet hours after 10 PM",
            cancellationPolicy = "Free up to 7 days before"
        )
        val dummyLotteries = listOf(
            Lottery(
                id = "lot1",
                ownerId = "user12",
                campsiteIds = listOf(targetId),
                nightsOffered = listOf("May weekends"),
                registrationStart = 0,
                registrationEnd = Long.MAX_VALUE,
                bookingStart = Long.MAX_VALUE,
                bookingEnd = Long.MAX_VALUE
            ),
            Lottery(
                id = "lot2",
                ownerId = "user12",
                campsiteIds = listOf(targetId),
                nightsOffered = listOf("September"),
                registrationStart = 0,
                registrationEnd = 0,
                bookingStart = 0,
                bookingEnd = 0
            )
        )
        val fakeRepo = FakeCampsiteRepository(initial = listOf(dummyCampsite))
        val fakeLotteryRepo = FakeLotteryRepository(initialLotteries = dummyLotteries)
        val viewModel = CampsiteDetailsViewModel(fakeRepo, fakeLotteryRepo, targetId)

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

        // Details Section
        composeTestRule.onNodeWithText("Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("JASPER · Up to 6 guests · $50 / night").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lakeside tent sites with canoe rentals nearby.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cookhouse").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tennis court").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maligne Lake Rd, Jasper, AB", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Quiet hours after 10 PM", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Free up to 7 days before", substring = true).assertIsDisplayed()
    }

    @Test
    fun actionButtons_whenClicked_triggerCallbacks() {
        // 1. ARRANGE
        val targetId = "camp1"
        val dummyCampsite = Campsite(
            id = targetId,
            ownerId = "user12",
            name = "Maligne Lake Camp",
            region = Region.JASPER
        )
        val dummyLottery = Lottery(
            id = "lot1",
            campsiteIds = listOf(targetId),
            nightsOffered = listOf("May weekends"),
            registrationStart = 0,
            registrationEnd = Long.MAX_VALUE,
            bookingEnd = Long.MAX_VALUE
        )
        val fakeRepo = FakeCampsiteRepository(initial = listOf(dummyCampsite))
        val fakeLotteryRepo = FakeLotteryRepository(initialLotteries = listOf(dummyLottery))
        val viewModel = CampsiteDetailsViewModel(fakeRepo, fakeLotteryRepo, targetId)

        var editClicked = false
        var qrClicked = false
        var availabilityClicked = false
        var reservationsClicked = false
        var createLotteryClicked = false
        var clickedLotteryId = ""

        // 2. ACT
        composeTestRule.setContent {
            CampsiteDetailsScreen(
                campsiteId = targetId,
                onBack = {},
                onEditClick = { editClicked = true },
                onQrCodeClick = { qrClicked = true },
                onAvailabilityClick = { availabilityClicked = true },
                onReservationsClick = { reservationsClicked = true },
                onLotteryClick = { clickedLotteryId = it },
                onCreateLotteryClick = { createLotteryClicked = true },
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Edit").performClick()
        composeTestRule.onNodeWithText("QR Code").performClick()
        composeTestRule.onNodeWithText("Availability").performClick()
        composeTestRule.onNodeWithText("Reservations").performClick()
        composeTestRule.onNodeWithText("Create Lottery").performClick()
        composeTestRule.onNodeWithText("May weekends").performClick()

        // 3. ASSERT
        TestCase.assertTrue(editClicked)
        TestCase.assertTrue(qrClicked)
        TestCase.assertTrue(availabilityClicked)
        TestCase.assertTrue(reservationsClicked)
        TestCase.assertTrue(createLotteryClicked)
        TestCase.assertEquals("lot1", clickedLotteryId)
    }
}
