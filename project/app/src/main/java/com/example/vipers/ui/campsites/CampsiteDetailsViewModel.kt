/*
 * CampsiteDetailsViewModel.kt
 *
 * Manages the state for viewing a specific campsite's details.
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

class CampsiteDetailsViewModel(private val campsiteRepository: CampsiteRepository, campsiteId: String): ViewModel() {

    // Nullable because it takes a moment to load from the repository
    var campsite by mutableStateOf<Campsite?>(null)
        private set

    init{
        loadCampsite(campsiteId)
    }

    private fun loadCampsite(campsiteId: String){
        viewModelScope.launch {
            campsite = campsiteRepository.getCampsite(campsiteId)
        }
    }

    companion object {
        // Factory requires the ID passed from the navigation route
        fun provideFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp)
                val container = application.container

                CampsiteDetailsViewModel(
                    campsiteRepository = container.campsiteRepository,
                    campsiteId = campsiteId
                )
            }
        }
    }
}