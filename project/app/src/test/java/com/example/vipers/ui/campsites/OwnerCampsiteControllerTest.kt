/*
 * OwnerCampsiteControllerTest.kt
 * Unit tests for OwnerCampsiteController (US 02.01.04): loading one of the owner's campsites with
 * its details and lotteries, using the fake campsite and lottery repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerCampsiteControllerTest {

    // the Hinton campsite from scenario 2 in the project description
    private val hinton = Campsite(
        id = "hinton",
        ownerId = "owner-priya",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse and an on-site tennis court.",
        maxOccupancy = 8,
        tags = listOf("Cookhouse", "Tennis court"),
        pricePerNightCents = 6500,
        rules = "Quiet hours after 10 PM",
        cancellationPolicy = "Free up to 7 days before",
    )
    private val banff = Campsite(id = "banff", name = "Bow River Sites", address = "Banff, AB")

    private val lotteries = listOf(
        Lottery(id = "may", campsiteIds = listOf("hinton"), nightsDescription = "May 2027 weekends"),
        Lottery(id = "june", campsiteIds = listOf("hinton"), nightsDescription = "June 2027 weekends"),
        Lottery(id = "other", campsiteIds = listOf("banff"), nightsDescription = "May 2027"),
    )

    private fun loadedController(campsiteId: String = "hinton"): OwnerCampsiteController =
        runBlocking {
            val controller = OwnerCampsiteController(
                FakeCampsiteRepository(listOf(hinton, banff)),
                FakeLotteryRepository(lotteries),
                campsiteId,
            )
            controller.fetchData()
            controller
        }

    @Test
    fun fetchData_loadsTheCampsiteWithAllItsDetails() {
        val controller = loadedController()
        assertEquals(hinton, controller.campsite)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_loadsOnlyThisCampsitesLotteries() {
        val controller = loadedController()
        assertEquals(listOf("may", "june"), controller.lotteries.map { it.id })
    }

    @Test
    fun fetchData_leavesCampsiteEmptyWhenNotFound() {
        val controller = loadedController(campsiteId = "missing")
        assertNull(controller.campsite)
        assertTrue(controller.lotteries.isEmpty())
        assertNull(controller.error)
    }

    @Test
    fun fetchData_showsTheLatestSavedValues() = runBlocking {
        // a repository whose campsite changes, like after the owner edits it (US 02.01.03)
        var saved = hinton
        val campsites = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = saved
        }
        val controller = OwnerCampsiteController(campsites, FakeLotteryRepository(), "hinton")
        controller.fetchData()
        assertEquals("Hinton Ridge Campground", controller.campsite?.name)

        saved = hinton.copy(name = "Hinton Ridge Sites", maxOccupancy = 6)
        controller.fetchData()
        assertEquals("Hinton Ridge Sites", controller.campsite?.name)
        assertEquals(6, controller.campsite?.maxOccupancy)
    }

    @Test
    fun fetchData_setsErrorWhenTheRepositoryFails() = runBlocking {
        // a repository that always fails, like Firestore when the phone is offline
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = throw Exception("offline")
        }
        val controller = OwnerCampsiteController(failing, FakeLotteryRepository(), "hinton")
        controller.fetchData()
        assertEquals("Unable to load details.", controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun fetchData_doesNotSetErrorWhenCancelled() = runBlocking {
        // a repository that never answers, so the load is still running when it's cancelled
        val slow = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getCampsite(id: String): Campsite? = awaitCancellation()
        }
        val controller = OwnerCampsiteController(slow, FakeLotteryRepository(), "hinton")
        val load = launch { controller.fetchData() }
        yield() // let the load start
        load.cancelAndJoin()
        assertNull(controller.error)
        assertFalse(controller.loading)
    }
}
