/*
 * MyCampsitesViewModel.kt
 *
 * ViewModel for the My Campsites screen. Fetches and manages the state of the user's listed campsites.
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
import kotlinx.coroutines.launch

/**
 * ViewModel for the My Campsites screen.
 * Fetches and holds the state of campsites owned by the current user.
 * [camp_repo] is the current repository that holds all the campsites.
 */
class MyCampsitesViewModel(private val camp_repo: CampsiteRepository, private val ownerId: String): ViewModel() {

    // Using 'by' allows Compose to treat this seamlessly as a List<Campsite>
    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    init {
        // Automatically fetch data when the ViewModel is created
        loadCampsites()
    }

    private fun loadCampsites() {
        viewModelScope.launch {
            campsites = camp_repo.getCampsitesByOwner(ownerId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {

                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp)
                val container = application.container

                // Build and return the ViewModel with the repository inside
                MyCampsitesViewModel(container.campsiteRepository, container.deviceId)
            }
        }
    }
}