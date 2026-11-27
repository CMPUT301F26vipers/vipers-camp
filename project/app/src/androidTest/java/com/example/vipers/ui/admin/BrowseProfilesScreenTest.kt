/*
 * BrowseProfilesScreenTest.kt
 * UI tests for BrowseProfilesScreen (US 03.05.01).
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
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseProfilesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileCards_showCorrectNameEmailAndPhone() {
        val profiles = listOf(
            UserProfile(
                id = "u1",
                name = "Alice Smith",
                email = "alice@example.com",
                phone = "555-0101"
            )
        )
        val profileRepo = FakeProfileRepository(profiles)
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Alice Smith").assertIsDisplayed()
        composeRule.onNodeWithText("alice@example.com · 555-0101").assertIsDisplayed()
    }

    @Test
    fun usersWhoOwnCampsites_showOwner_whileUsersWithoutCampsites_showEntrant() {
        val profiles = listOf(
            UserProfile(id = "owner1", name = "Alice Smith"),
            UserProfile(id = "entrant1", name = "Bob Jones")
        )
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner1", name = "Mountain View", removed = false)
        )
        val profileRepo = FakeProfileRepository(profiles)
        val campsiteRepo = FakeCampsiteRepository(campsites)
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Alice Smith").assertIsDisplayed()
        composeRule.onNodeWithText("Owner").assertIsDisplayed()

        composeRule.onNodeWithText("Bob Jones").assertIsDisplayed()
        composeRule.onNodeWithText("Entrant").assertIsDisplayed()
    }

    @Test
    fun searchFieldFiltersProfilesCorrectly() {
        val profiles = listOf(
            UserProfile(id = "u1", name = "Alice Smith", email = "alice@example.com"),
            UserProfile(id = "u2", name = "Bob Jones", email = "bob@example.com")
        )
        val profileRepo = FakeProfileRepository(profiles)
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, profileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Search profiles").performTextInput("Alice")

        composeRule.onNodeWithText("Alice Smith").assertIsDisplayed()
        composeRule.onNodeWithText("Bob Jones").assertDoesNotExist()
    }

    @Test
    fun failedProfileLoad_showsAppropriateErrorAndRetry() {
        val failingProfileRepo = object : ProfileRepository by FakeProfileRepository() {
            override suspend fun getAllProfiles(): List<UserProfile> {
                throw IllegalStateException("Database error")
            }
        }
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, failingProfileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Unable to load profiles.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun retrySuccessfullyLoadsProfiles_afterFailureIsCleared() {
        var shouldFail = true
        val profiles = listOf(UserProfile(id = "u1", name = "Alice Smith"))
        val baseProfileRepo = FakeProfileRepository(profiles)
        val flappyProfileRepo = object : ProfileRepository by baseProfileRepo {
            override suspend fun getAllProfiles(): List<UserProfile> {
                if (shouldFail) throw IllegalStateException("Database error")
                return baseProfileRepo.getAllProfiles()
            }
        }
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, flappyProfileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        composeRule.onNodeWithText("Unable to load profiles.").assertIsDisplayed()

        shouldFail = false
        composeRule.onNodeWithText("Retry").performClick()

        composeRule.onNodeWithText("Alice Smith").assertIsDisplayed()
    }

    @Test
    fun campsiteLoading_doesNotClearProfileLoadingStateOrCausePrematureEmptyState() {
        val deferred = CompletableDeferred<Unit>()
        val profiles = listOf(UserProfile(id = "u1", name = "Alice Smith"))
        val delayedProfileRepo = object : ProfileRepository by FakeProfileRepository(profiles) {
            override suspend fun getAllProfiles(): List<UserProfile> {
                deferred.await()
                return profiles
            }
        }
        val campsiteRepo = FakeCampsiteRepository(listOf(Campsite(id = "c1", name = "Pine Camp")))
        val controller = AdminController(campsiteRepo, delayedProfileRepo)

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = {}, controller = controller)
            }
        }

        // Even though campsite loading has finished (isLoading is false), profiles are still loading
        // (isProfilesLoading is true). The screen must not show "No profiles found." prematurely.
        composeRule.onNodeWithText("No profiles found.").assertDoesNotExist()

        // Unblock profile loading
        deferred.complete(Unit)

        // Profiles now appear
        composeRule.onNodeWithText("Alice Smith").assertIsDisplayed()
    }

    @Test
    fun clickingBackButtonInvokesOnBackCallback() {
        val profileRepo = FakeProfileRepository()
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, profileRepo)
        var backClicked = false

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(onBack = { backClicked = true }, controller = controller)
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backClicked)
    }

    @Test
    fun clickingProfileCardInvokesOnOpenProfileCallback() {
        val profiles = listOf(UserProfile(id = "u1", name = "Alice Smith"))
        val profileRepo = FakeProfileRepository(profiles)
        val campsiteRepo = FakeCampsiteRepository()
        val controller = AdminController(campsiteRepo, profileRepo)
        var openedProfileId: String? = null

        composeRule.setContent {
            VipersTheme {
                BrowseProfilesScreen(
                    onBack = {},
                    onOpenProfile = { openedProfileId = it },
                    controller = controller
                )
            }
        }

        composeRule.onNodeWithText("Alice Smith").performClick()

        assertEquals("u1", openedProfileId)
    }
}
