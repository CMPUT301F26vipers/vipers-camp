/*
 * CampsiteSearchControllerTest.kt
 * Unit tests for CampsiteSearchController (US 01.01.01), using the fake campsite repository.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteSearchControllerTest {

    private val campsites = listOf(
        Campsite(id = "1", name = "Hinton Ridge Campground", address = "Hinton, AB", region = Region.HINTON),
        Campsite(id = "2", name = "Hinton Lakeside Sites", address = "Hinton, AB", region = Region.HINTON),
        Campsite(id = "3", name = "Bow Valley Campground", address = "Banff, AB", region = Region.BANFF),
        Campsite(id = "4", name = "Hinton Old Mill", address = "Hinton, AB", region = Region.HINTON, removed = true),
    )

    private fun loadedController(): CampsiteSearchController = runBlocking {
        val controller = CampsiteSearchController(FakeCampsiteRepository(campsites))
        controller.fetchCampsites()
        controller
    }

    @Test
    fun results_emptySearchShowsEveryCampsiteSortedByName() {
        val controller = loadedController()
        assertEquals(
            listOf("Bow Valley Campground", "Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun results_showOnlyNamesStartingWithTheText() {
        val controller = loadedController()
        controller.onSearchTextChange("hin")
        assertEquals(
            listOf("Hinton Lakeside Sites", "Hinton Ridge Campground"),
            controller.results.map { it.name },
        )
    }

    @Test
    fun results_updateAsTheUserKeepsTyping() {
        val controller = loadedController()
        controller.onSearchTextChange("Hinton")
        assertEquals(2, controller.results.size)
        controller.onSearchTextChange("Hinton R")
        assertEquals(listOf("Hinton Ridge Campground"), controller.results.map { it.name })
    }

    @Test
    fun results_areEmptyWhenNothingMatches() {
        val controller = loadedController()
        controller.onSearchTextChange("lake louise")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun results_neverIncludeRemovedCampsites() {
        val controller = loadedController()
        controller.onSearchTextChange("Hinton Old")
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun fetchCampsites_stopsLoadingWhenDone() {
        val controller = loadedController()
        assertFalse(controller.isLoading)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchCampsites_setsLoadFailedWhenTheRepositoryFails() = runBlocking {
        // a repository that always fails, like Firestore when the phone is offline
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> = throw Exception("offline")
        }
        val controller = CampsiteSearchController(failing)
        controller.fetchCampsites()
        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.results.isEmpty())
    }

    @Test
    fun fetchCampsites_doesNotSetLoadFailedWhenCancelled() = runBlocking {
        // a repository that never answers, so the load is still running when it's cancelled
        val slow = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAllCampsites(): List<Campsite> = awaitCancellation()
        }
        val controller = CampsiteSearchController(slow)
        val load = launch { controller.fetchCampsites() }
        yield() // let the load start
        load.cancelAndJoin()
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
    }
}
