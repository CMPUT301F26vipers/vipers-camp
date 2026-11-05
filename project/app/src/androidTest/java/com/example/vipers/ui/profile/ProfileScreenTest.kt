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
import androidx.compose.ui.test.performScrollTo
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
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
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
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
            )
        }

        composeRule.onNodeWithText("Sam")
            .performTextReplacement("Sam Patel")

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

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
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
            )
        }

        composeRule.onNodeWithText("Sam")
            .performTextReplacement("")

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.onNodeWithText("Name is required")
            .performScrollTo().assertIsDisplayed()

        runBlocking {
            assertEquals("Sam", fakeRepo.getProfile("user1")?.name)
        }
    }

    @Test
    fun invalidEmail_showsValidationError() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
            )
        }

        composeRule.onNodeWithText("sam@example.com")
            .performTextReplacement("not-an-email")

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.onNodeWithText("Invalid email address")
            .performScrollTo().assertIsDisplayed()

        runBlocking {
            assertEquals("sam@example.com", fakeRepo.getProfile("user1")?.email)
        }
    }

    @Test
    fun invalidPhone_showsValidationError() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            phone = "7805551234"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
            )
        }

        composeRule.onNodeWithText("7805551234")
            .performTextReplacement("123")

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.onNodeWithText("Invalid phone number")
            .performScrollTo().assertIsDisplayed()

        runBlocking {
            assertEquals("7805551234", fakeRepo.getProfile("user1")?.phone)
        }
    }

    @Test
    fun clearingOptionalFields_savesSuccessfully() {
        val profile = UserProfile(
            id = "user1",
            name = "Sam",
            email = "sam@example.com",
            phone = "7805551234"
        )
        val fakeRepo = FakeProfileRepository(listOf(profile))
        val controller = ProfileController(fakeRepo, "user1")

        composeRule.setContent {
            ProfileScreen(
                onOpenSettings = {},
                onOpenPolicy = {},
                controller = controller
            )
        }

        composeRule.onNodeWithText("sam@example.com")
            .performTextReplacement("")
        composeRule.onNodeWithText("7805551234")
            .performTextReplacement("")

        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        runBlocking {
            val saved = fakeRepo.getProfile("user1")!!
            assertEquals("", saved.email)
            assertEquals("", saved.phone)
        }
    }
}
