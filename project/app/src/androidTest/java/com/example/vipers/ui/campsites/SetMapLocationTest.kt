/*
 * SetMapLocationTest.kt
 * Intent tests for US 02.01.02c: Find location on the Add / Edit Campsite form (mockups O02, O05)
 * lists up to 5 places with their coordinates (mockup O03), the chosen one is shown and saved, a
 * campsite can be saved without one, a failed or empty lookup shows an error, and My Campsite
 * (mockup O04) shows the coordinates or hides the map box. Uses a fake PlaceFinder and fake
 * repositories, so it needs no network and doesn't touch Firestore.
 * Known issues: the real Geocoder isn't tested here, since its results depend on Google's servers.
 */
package com.example.vipers.ui.campsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakePlaceFinder
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Place
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SetMapLocationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // what the Geocoder could return for "Hinton, AB" (mockup O03)
    private val hintonPlaces = listOf(
        Place("Hinton", "Alberta, Canada", 53.405, -117.585),
        Place("Hinton Golf Course", "Hinton, Alberta", 53.392, -117.561),
        Place("Hinton Nordic Centre", "Hinton, Alberta", 53.412, -117.603),
    )

    // the Hinton campsite from scenario 2 in the project description, saved with a map location
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        maxOccupancy = 8,
        pricePerNightCents = 6500,
        lat = 53.405,
        lng = -117.585,
    )

    private val repo = FakeCampsiteRepository()
    private var publishedCalled = false

    /** Opens the form with [finder] for Find location; with [campsiteId] it edits that campsite. */
    private fun openForm(
        finder: FakePlaceFinder = FakePlaceFinder(hintonPlaces),
        campsites: FakeCampsiteRepository = repo,
        campsiteId: String? = null,
    ) {
        val controller = CampsiteController(campsites, ownerId = "my-phone", placeFinder = finder)
        composeRule.setContent {
            VipersTheme {
                CampsiteFormScreen(
                    onBack = {},
                    onPublished = { publishedCalled = true },
                    campsiteId = campsiteId,
                    controller = controller,
                )
            }
        }
    }

    private fun type(tag: String, text: String) =
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun tap(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo().performClick()

    private fun findLocation(address: String = "Hinton, AB") {
        type("field_address", address)
        tap("button_find_location")
    }

    private fun fillTheRestOfTheForm() {
        type("field_name", "Hinton Ridge Campground")
        tap("field_region")
        composeRule.onNodeWithText("Hinton").performClick()
        type("field_max_guests", "8")
        type("field_price", "65")
    }

    // Publish sits below the scrolling form, so it needs no scrolling
    private fun publish() = composeRule.onNodeWithTag("button_publish").performClick()

    @Test
    fun findLocationListsThePlacesWithTheirCoordinates() {
        openForm()

        findLocation()

        composeRule.onNodeWithText("Choose a place").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Golf Course").assertIsDisplayed()
        composeRule.onNodeWithText("Alberta, Canada · 53.405, -117.585").assertIsDisplayed()
        composeRule.onNodeWithText("Hinton, Alberta · 53.412, -117.603").assertIsDisplayed()
    }

    @Test
    fun useThisPlaceIsOffUntilAPlaceIsTapped() {
        openForm()
        findLocation()

        composeRule.onNodeWithTag("button_use_place").assertIsNotEnabled()
        composeRule.onNodeWithTag("place_1").performClick()
        composeRule.onNodeWithTag("button_use_place").assertIsEnabled()
    }

    @Test
    fun theChosenPlaceIsShownSoTheOwnerCanCheckIt() {
        openForm()
        findLocation()

        composeRule.onNodeWithTag("place_1").performClick()
        composeRule.onNodeWithTag("button_use_place").performClick()

        composeRule.onNodeWithText("Choose a place").assertDoesNotExist()
        composeRule.onNodeWithTag("map_location", useUnmergedTree = true)
            .assertTextEquals("Map location: Hinton Golf Course · 53.392, -117.561")
    }

    @Test
    fun publishingSavesTheChosenCoordinates() {
        openForm()
        fillTheRestOfTheForm()
        findLocation()
        composeRule.onNodeWithTag("place_0").performClick()
        composeRule.onNodeWithTag("button_use_place").performClick()

        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        val saved = runBlocking { repo.getCampsitesByOwner("my-phone") }.single()
        assertEquals(53.405, saved.lat!!, 0.0)
        assertEquals(-117.585, saved.lng!!, 0.0)
    }

    @Test
    fun aCampsiteWithoutAMapLocationCanStillBeSaved() {
        openForm()
        type("field_address", "Hinton, AB")
        fillTheRestOfTheForm()

        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        assertFalse(runBlocking { repo.getCampsitesByOwner("my-phone") }.single().hasLocation())
    }

    @Test
    fun cancelClosesTheDialogWithoutSettingALocation() {
        openForm()
        findLocation()

        composeRule.onNodeWithTag("place_0").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Choose a place").assertDoesNotExist()
        composeRule.onNodeWithTag("map_location", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun nothingFoundShowsAnErrorAndKeepsTheAddress() {
        openForm(finder = FakePlaceFinder(emptyList()))

        findLocation("Nowhere at all")

        composeRule.onNodeWithText("No places found. Check the address and try again.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Nowhere at all").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a place").assertDoesNotExist()
    }

    @Test
    fun aFailedLookupShowsAnErrorAndKeepsTheOtherFields() {
        openForm(finder = FakePlaceFinder(fails = true))
        type("field_name", "Hinton Ridge Campground")

        findLocation()

        composeRule.onNodeWithText(
            "Couldn't look up the address. Check your connection and try again.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Hinton Ridge Campground").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editShowsTheSavedLocationAndRemoveSavesWithoutIt() {
        val campsites = FakeCampsiteRepository(listOf(hinton))
        openForm(campsites = campsites, campsiteId = "hinton")

        composeRule.onNodeWithTag("map_location", useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals("Map location: 53.405, -117.585")
        tap("button_remove_location")
        composeRule.onNodeWithTag("map_location", useUnmergedTree = true).assertDoesNotExist()
        publish()

        composeRule.waitUntil(5_000) { publishedCalled }
        assertFalse(runBlocking { campsites.getCampsite("hinton") }!!.hasLocation())
    }

    /** Shows My Campsite (O04) for [campsite]. */
    private fun openMyCampsite(campsite: Campsite) {
        val controller = OwnerCampsiteController(
            FakeCampsiteRepository(listOf(campsite)),
            FakeLotteryRepository(),
            campsite.id,
            userId = "my-phone",
        )
        composeRule.setContent {
            VipersTheme {
                OwnerCampsiteScreen(
                    campsiteId = campsite.id,
                    onBack = {},
                    onEdit = {},
                    onOpenAvailability = {},
                    onOpenReservations = {},
                    onCreateLottery = {},
                    onLotteryClick = {},
                    controller = controller,
                )
            }
        }
    }

    @Test
    fun myCampsiteShowsTheMapLocationsCoordinates() {
        openMyCampsite(hinton)

        composeRule.onNodeWithTag("owner_map_location").assertIsDisplayed()
        composeRule.onNodeWithText("53.405, -117.585").assertIsDisplayed()
    }

    @Test
    fun myCampsiteHidesTheMapBoxWithoutAMapLocation() {
        openMyCampsite(hinton.copy(lat = null, lng = null))

        composeRule.onNodeWithText("Hinton Ridge Campground").assertIsDisplayed()
        composeRule.onNodeWithTag("owner_map_location").assertDoesNotExist()
    }

    @Test
    fun changingTheAddressAfterFindingShowsAHint() {
        openForm()
        findLocation()
        composeRule.onNodeWithTag("place_0").performClick()
        composeRule.onNodeWithTag("button_use_place").performClick()
        composeRule.onNodeWithTag("address_changed_hint", useUnmergedTree = true)
            .assertDoesNotExist()

        type("field_address", " Alberta")

        composeRule.onNodeWithTag("address_changed_hint", useUnmergedTree = true)
            .assertIsDisplayed()
        // the old location is kept until the owner finds it again or removes it
        composeRule.onNodeWithTag("map_location", useUnmergedTree = true).assertIsDisplayed()
    }
}
