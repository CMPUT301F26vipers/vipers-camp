/*
 * CampsiteSearchController.kt
 * The controller (a ViewModel) behind the Home search (US 01.01.01). It loads the campsites once
 * and filters them by name on the device as the user types, as the CRC card describes.
 * Known issues: the region, date and guest filters (01.01.02), the result count and loading
 * indicator (01.01.03) and the active lottery badge (01.01.04) are added by those stories.
 */
package com.example.vipers.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Turns the entrant's search text into the list of campsites shown on Home.
 * Takes the [campsiteRepository] in its constructor so tests can pass a fake one.
 */
class CampsiteSearchController(
    private val campsiteRepository: CampsiteRepository,
) : ViewModel() {

    /** What the user typed in the search bar. */
    var searchText by mutableStateOf("")
        private set

    /** True while the campsites are loading. */
    var isLoading by mutableStateOf(false)
        private set

    /** True if the last load failed, e.g. because the phone is offline. */
    var loadFailed by mutableStateOf(false)
        private set

    // every campsite that isn't removed; the repository already skips removed ones
    private var allCampsites by mutableStateOf(emptyList<Campsite>())

    /** The campsites whose name starts with [searchText], sorted by name. */
    val results: List<Campsite>
        get() = allCampsites
            .filter { it.matchesSearch(searchText) }
            .sortedBy { it.name.lowercase() }

    /** Called as the user types; the [results] update right away. */
    fun onSearchTextChange(text: String) {
        searchText = text
    }

    /**
     * Loads the campsites in the background. Home calls this each time it opens. The load runs in
     * [viewModelScope], so it keeps going if the user switches tabs before it finishes.
     */
    fun loadCampsites() {
        viewModelScope.launch { fetchCampsites() }
    }

    /**
     * Gets the campsites from the repository and sets [isLoading] and [loadFailed].
     * [loadCampsites] runs this in the background; unit tests call it directly.
     */
    suspend fun fetchCampsites() {
        isLoading = true
        try {
            allCampsites = campsiteRepository.getAllCampsites()
            loadFailed = false
        } catch (e: CancellationException) {
            // the load was stopped on purpose (the controller is gone), so it didn't fail
            throw e
        } catch (e: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    companion object {
        /** Makes the controller with the app's real repository (from CampApp's container). */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteSearchController(app.container.campsiteRepository)
            }
        }
    }
}
