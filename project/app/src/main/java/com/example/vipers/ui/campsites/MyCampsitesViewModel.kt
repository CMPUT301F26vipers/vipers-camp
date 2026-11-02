package com.example.vipers.ui.campsites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the My Campsites screen.
 * Fetches and holds the state of campsites owned by the current user.
 */
class MyCampsitesViewModel(private val camp_repo: CampsiteRepository): ViewModel() {

    private val _campsites = MutableStateFlow<List<Campsite>>(emptyList())
    val campsites: StateFlow<List<Campsite>> = _campsites

    fun loadCampsites(ownerID: String){
        viewModelScope.launch {
            _campsites.value = camp_repo.getCampsitesByOwner(ownerID)
        }
    }
}