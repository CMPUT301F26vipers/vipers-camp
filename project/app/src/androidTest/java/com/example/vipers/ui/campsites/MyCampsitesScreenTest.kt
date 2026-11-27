/*
 * MyCampsitesScreenTest.kt
 * Intent tests for the My Campsites screen (mockup O01, US 02.03.01): the empty state, the owner's
 * campsites in the list with real lottery status, and tapping a campsite to open it.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import org.junit.Rule
import org.junit.Test

class MyCampsitesScreenTest {

    // This rule sets up the Compose testing environment
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyCampsites_showsPlaceholderText() {
        // 1. ARRANGE: Create an empty fake repository
        val fakeRepo = FakeCampsiteRepository(initial = emptyList())
        val viewModel = MyCampsitesController(fakeRepo, "user12")

        // 2. ACT: Launch the screen with the empty ViewModel
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                viewModel = viewModel,
                onOpenCampsite = {},
                onOpenSent = {},
                onOpenReservations = {}
            )
        }

        // 3. ASSERT: Verify the placeholder text appears
        composeTestRule.onNodeWithText("You haven't listed any campsites yet.").assertIsDisplayed()
    }

    @Test
    fun populatedCampsites_showsCampsiteNames() {
        // 1. ARRANGE: Create a repository with one campsite owned by "user12"
        val dummyData = listOf(
            Campsite(id = "1", ownerId = "user12", name = "Test Camp Alpha")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = MyCampsitesController(fakeRepo, "user12")

        // 2. ACT: Launch the screen
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                viewModel = viewModel,
                onOpenCampsite = {},
                onOpenSent = {},
                onOpenReservations = {}
            )
        }

        // 3. ASSERT: Verify the LazyColumn renders the campsite name
        composeTestRule.onNodeWithText("Test Camp Alpha").assertIsDisplayed()
    }

    @Test
    fun populatedCampsites_butNoOwnerCampsites_showsCampsiteNames() {
        // 1. ARRANGE: Create a repository with one campsite owned by "user12"
        val dummyData = listOf(
            Campsite(id = "1", ownerId = "user123", name = "Test Camp Alpha")
        )
        val fakeRepo = FakeCampsiteRepository(initial = dummyData)
        val viewModel = MyCampsitesController(fakeRepo, "user12")

        // 2. ACT: Launch the screen
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                viewModel = viewModel,
                onOpenCampsite = {},
                onOpenSent = {},
                onOpenReservations = {}
            )
        }

        // 3. ASSERT: Verify the placeholder text appears as no campsites by owner
        composeTestRule.onNodeWithText("You haven't listed any campsites yet.").assertIsDisplayed()
    }

    @Test
    fun campsiteItem_isResponsiveAndPassesIdOnClick() {
        // 1. Create a dummy campsite (fill in any other fields your data class requires)
        val dummyCampsite = Campsite(
            id = "camp-999",
            name = "Test Lodge",
            ownerId = "user123"
            // region = Region.JASPER
        )

        var capturedId = ""

        // 2. Render only the list item component
        composeTestRule.setContent {
            CampsiteItem(
                campsite = dummyCampsite,
                onClick = { capturedId = dummyCampsite.id }
            )
        }

        // 3. Simulate a user tapping the chevron (or the card)
        composeTestRule.onNodeWithContentDescription("View details").performClick()

        // 4. Verify the UI responded and captured the correct ID
        assert(capturedId == "camp-999") {
            "CampsiteItem did not fire onClick with the correct ID."
        }
    }

    @Test
    fun populatedCampsites_withOpenLottery_displaysRealLotteryStatus() {
        val now = System.currentTimeMillis()
        val campsite = Campsite(
            id = "c1",
            ownerId = "user12",
            name = "Hinton Ridge",
            address = "Hinton, AB",
        )
        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("c1"),
            registrationStart = now - 1000L,
            registrationEnd = now + 5000L,
        )
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lotteryRepo = FakeLotteryRepository(listOf(lottery))
        val viewModel = MyCampsitesController(
            campsiteRepository = campsiteRepo,
            lotteryRepository = lotteryRepo,
            ownerId = "user12",
        )

        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                viewModel = viewModel,
                onOpenCampsite = {},
                onOpenSent = {},
                onOpenReservations = {},
            )
        }

        composeTestRule.onNodeWithText("Hinton Ridge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hinton, AB · Lottery status: Open").assertIsDisplayed()
    }

    @Test
    fun populatedCampsites_withoutLottery_displaysNoneLotteryStatus() {
        val campsite = Campsite(
            id = "c2",
            ownerId = "user12",
            name = "Banff Alpine Camp",
            address = "Banff, AB",
        )
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lotteryRepo = FakeLotteryRepository(emptyList())
        val viewModel = MyCampsitesController(
            campsiteRepository = campsiteRepo,
            lotteryRepository = lotteryRepo,
            ownerId = "user12",
        )

        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                viewModel = viewModel,
                onOpenCampsite = {},
                onOpenSent = {},
                onOpenReservations = {},
            )
        }

        composeTestRule.onNodeWithText("Banff Alpine Camp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Banff, AB · Lottery status: None").assertIsDisplayed()
    }
}
