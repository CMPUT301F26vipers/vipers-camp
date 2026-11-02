package com.example.vipers.ui.campsites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the My Campsites screen.
 * Fetches and holds the state of campsites owned by the current user.
 * [camp_repo] is the current repository that holds all the campsites.
 */
class MyCampsitesViewModel(private val camp_repo: CampsiteRepository): ViewModel() {

    private val _campsites = MutableStateFlow<List<Campsite>>(emptyList())
    val campsites: StateFlow<List<Campsite>> = _campsites

    fun loadCampsites(ownerID: String){
        viewModelScope.launch {
            _campsites.value = camp_repo.getCampsitesByOwner(ownerID)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Android gives us a hook to grab the main application class
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp)

                // Reach into the container and grab the repository
                val repository = application.container.campsiteRepository

                // Build and return the ViewModel with the repository inside
                MyCampsitesViewModel(repository)
            }
        }
    }
}