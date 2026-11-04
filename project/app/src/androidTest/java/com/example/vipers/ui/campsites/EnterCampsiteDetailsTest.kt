/*
 * EnterCampsiteDetailsTest.kt
 * Intent tests for US 02.01.02a: the Add Campsite form has every detail field, the region
 * dropdown lists every region, and everything the owner types is saved as entered.
 * Required-field errors, whole-number checks and tags are tested in AddCampsiteTest.
 * Known issues: showing the saved details on the details screens is tested by 01.01.05 and
 * 02.01.04, which build those screens.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EnterCampsiteDetailsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repo = FakeCampsiteRepository()
    private var publishedCalled = false

    private fun openForm() {
        val controller = CampsiteController(repo, ownerId = "my-phone")
        composeRule.setContent {
            VipersTheme {
                CampsiteFormScreen(onBack = {}, onPublished = { publishedCalled = true }, controller = controller)
            }
        }
    }

    private fun type(tag: String, text: String) =
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun addTag(text: String) {
        type("field_tag", text)
        composeRule.onNodeWithTag("button_add_tag").performClick()
    }

    @Test
    fun formHasEveryDetailField() {
        openForm()
        for (tag in listOf("field_name", "field_address", "field_region", "field_max_guests", "field_description", "field_tag")) {
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun regionDropdownListsEveryRegion() {
        openForm()
        composeRule.onNodeWithTag("field_region").performClick()
        for (region in Region.entries) {
            composeRule.onNodeWithText(region.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun everyDetailIsSavedAsEntered() {
        openForm()
        type("field_name", "Hinton Ridge Campground")
        type("field_address", "Hinton, AB")
        composeRule.onNodeWithTag("field_region").performClick()
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "6")
        type("field_description", "Outdoor cookhouse and tennis court")
        addTag("Cookhouse")
        addTag("Tennis court")
        composeRule.onNodeWithTag("button_publish").performClick()

        composeRule.waitUntil(5_000) { publishedCalled }
        val saved = runBlocking { repo.getCampsitesByOwner("my-phone") }.single()
        assertEquals("Hinton Ridge Campground", saved.name)
        assertEquals("Hinton, AB", saved.address)
        assertEquals(Region.HINTON, saved.region)
        assertEquals(6, saved.maxOccupancy)
        assertEquals("Outdoor cookhouse and tennis court", saved.description)
        assertEquals(listOf("Cookhouse", "Tennis court"), saved.tags)
    }
}
