/*
 * AdminCampsitesViewModel.kt
 * Loads campsites for the admin browsing screen.
 * Known issues: none.
 */
package com.example.vipers.ui.admin

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
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads campsites for the admin browsing screen. */
class AdminCampsitesViewModel(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    var ownerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loadFailed by mutableStateOf(false)
        private set

    init {
        loadCampsites()
    }

    /** Returns the display name for the given [ownerId]. */
    fun getOwnerName(ownerId: String): String {
        return ownerNames[ownerId] ?: "Deleted user"
    }

    /** Loads all campsites that have not been removed, along with owner display names. */
    fun loadCampsites() {
        viewModelScope.launch {
            isLoading = true
            loadFailed = false

            try {
                val loadedCampsites = campsiteRepository.getAllCampsites()
                val uniqueOwnerIds = loadedCampsites.map { it.ownerId }.distinct()
                val namesMap = uniqueOwnerIds.associateWith { ownerId ->
                    profileRepository.getDisplayName(ownerId)
                }
                ownerNames = namesMap
                campsites = loadedCampsites
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp

                AdminCampsitesViewModel(
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                )
            }
        }
    }
}
