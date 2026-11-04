
/*
 * ProfileScreenTest.kt
 * UI tests for editing profile information (US 01.07.03).
 * Uses a fake repository instead of Firestore.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileScreen_displaysExistingInformation() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com",
            phone = "7805551234"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithText("Sam").assertIsDisplayed()
        composeRule.onNodeWithText("sam@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("7805551234").assertIsDisplayed()
    }

    @Test
    fun editingName_savesUpdatedProfile() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithText("Sam")
            .performTextReplacement("Sam Patel")

        composeRule.onNodeWithText("Save Changes").performClick()

        composeRule.onNodeWithText("Profile updated successfully.")
            .assertIsDisplayed()

        runBlocking {
            assertEquals("Sam Patel", fakeRepo.getProfile("user1")?.name)
        }
    }

    @Test
    fun blankName_showsValidationError() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val viewModel = ProfileViewModel(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithText("Sam")
            .performTextReplacement("")

        composeRule.onNodeWithText("Save Changes").performClick()

        composeRule.onNodeWithText("Name is required")
            .assertIsDisplayed()

        runBlocking {
            assertEquals("Sam", fakeRepo.getProfile("user1")?.name)
        }
    }
}
