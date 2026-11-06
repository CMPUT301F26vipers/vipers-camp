/*
 * MyCampsitesController.kt
 *
 * The controller (a ViewModel) for the My Campsites screen (O01). Fetches and manages the state of
 * the user's listed campsites.
 *
 * Known issues: None.
 */
package com.example.vipers.ui.campsites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Controller for the My Campsites screen.
 * Fetches and holds the state of campsites owned by the current user.
 *
 * @param camp_repo The repository that holds all the campsites.
 * @param ownerId The current user's ID; only their campsites are shown.
 */
class MyCampsitesController(private val camp_repo: CampsiteRepository, private val ownerId: String): ViewModel() {

    // Using 'by' allows Compose to treat this seamlessly as a List<Campsite>
    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    init {
        // Automatically fetch data when the ViewModel is created
        loadCampsites()
    }

    /** Loads the owner's campsites again, e.g. when coming back from Add Campsite. */
    fun loadCampsites() {
        viewModelScope.launch {
            try {
                campsites = camp_repo.getCampsitesByOwner(ownerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a failed read shouldn't crash the app; keep showing what we had
            }
        }
    }

    companion object {
        /** Builds a [MyCampsitesController] for this device's user with the app's repositories. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {

                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp)
                val container = application.container

                // Build and return the ViewModel with the repository inside
                MyCampsitesController(container.campsiteRepository, container.deviceId)
            }
        }
    }
}