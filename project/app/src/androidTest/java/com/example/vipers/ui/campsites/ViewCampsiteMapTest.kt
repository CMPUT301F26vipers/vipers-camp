/*
 * ViewCampsiteMapTest.kt
 * Checks the map action on the entrant and owner details screens using fake campsites (T-04).
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.VipersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ViewCampsiteMapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hinton = Campsite(
        id = "hinton", ownerId = "priya", name = "Hinton Ridge Campground",
        address = "Hinton, AB", region = Region.HINTON, maxOccupancy = 8,
        description = "Outdoor cookhouse and on-site tennis court.",
        tags = listOf("Cookhouse", "Tennis court"), rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before", pricePerNightCents = 6500,
        lat = 53.40537, lng = -117.58528,
    )
    private var launched: Intent? = null

    private fun openDetails(owner: Boolean, campsite: Campsite = hinton) {
        val sites = FakeCampsiteRepository(listOf(campsite))
        val lotteries = FakeLotteryRepository()
        val context = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) {
                launched = intent
            }
        }
        val entrant = CampsiteDetailsController(sites, lotteries, campsite.id, "sam")
        val host = OwnerCampsiteController(sites, lotteries, campsite.id, "priya")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                VipersTheme {
                    if (owner) {
                        OwnerCampsiteScreen(
                            campsiteId = campsite.id, onBack = {}, onEdit = {},
                            onOpenAvailability = {}, onOpenReservations = {},
                            onCreateLottery = {}, onLotteryClick = {}, controller = host,
                        )
                    } else {
                        CampsiteDetailsScreen(campsite.id, onBack = {}, controller = entrant)
                    }
                }
            }
        }
        compose.onNodeWithText(campsite.name).performScrollTo().assertIsDisplayed()
    }

    private fun checkPin() {
        compose.onNodeWithText("Open in Maps").performScrollTo().performClick()
        compose.runOnIdle {
            val intent = launched
            assertNotNull(intent)
            assertEquals(Intent.ACTION_VIEW, intent?.action)
            assertEquals("geo:53.40537,-117.58528?q=53.40537,-117.58528", intent?.dataString)
            assertEquals(0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun entrantOpensTheSavedCoordinates() {
        openDetails(owner = false)
        checkPin()
    }

    @Test
    fun ownerOpensTheSameSavedCoordinates() {
        openDetails(owner = true)
        checkPin()
    }

    @Test
    fun entrantWithoutCoordinatesHasNoMapAction() {
        openDetails(owner = false, campsite = hinton.copy(lat = null, lng = null))
        compose.onNodeWithText("Open in Maps").assertDoesNotExist()
    }
}
