package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.Campsite
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
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Launch the screen with the empty ViewModel
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                ownerID = "user12",
                viewModel = viewModel
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
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Launch the screen
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                ownerID = "user12",
                viewModel = viewModel
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
        val viewModel = MyCampsitesViewModel(fakeRepo)

        // 2. ACT: Launch the screen
        composeTestRule.setContent {
            MyCampsitesScreen(
                onAddCampsite = {},
                ownerID = "user12",
                viewModel = viewModel
            )
        }

        // 3. ASSERT: Verify the placeholder text appears as no campsites by owner
        composeTestRule.onNodeWithText("You haven't listed any campsites yet.").assertIsDisplayed()
    }
}
