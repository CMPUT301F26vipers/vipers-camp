/*
 * AdminController.kt
 * Loads campsites and user profiles for the admin browsing screens (US 03.04.01, US 03.05.01).
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads campsites and user profiles for the admin browsing screens. */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    var ownerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var profiles by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    var ownerProfileIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loadFailed by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadCampsites()
    }

    /** Returns the display name for the given [ownerId]. */
    fun getOwnerName(ownerId: String): String {
        return ownerNames[ownerId] ?: "Deleted user"
    }

    /** Returns whether the user with the given [profileId] owns at least one campsite. */
    fun isCampsiteOwner(profileId: String): Boolean {
        return profileId in ownerProfileIds
    }

    /** Returns whether the user with the given [profileId] owns at least one campsite. */
    fun isOwner(profileId: String): Boolean = isCampsiteOwner(profileId)

    /** Returns the profile with the given [profileId] from the loaded profiles, or null if not found. */
    fun getProfile(profileId: String): UserProfile? {
        return profiles.firstOrNull { it.id == profileId }
    }

    /** Returns the active campsites owned by [ownerId] from the loaded campsites list. */
    fun getCampsitesForOwner(ownerId: String): List<Campsite> {
        return campsites.filter { it.ownerId == ownerId && !it.removed }
    }

    /** Loads all campsites that have not been removed, along with owner display names. */
    fun loadCampsites() {
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            error = null

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
                error = "Unable to load campsites."
            } finally {
                isLoading = false
            }
        }
    }

    /** Loads all user profiles and determines campsite ownership in the background. */
    fun loadProfiles() {
        viewModelScope.launch { fetchProfiles() }
    }

    /** Loads all user profiles and determines campsite ownership. Can be called directly by unit tests. */
    suspend fun fetchProfiles() {
        isLoading = true
        loadFailed = false
        error = null

        try {
            val loadedProfiles = profileRepository.getAllProfiles()
            val owners = loadedProfiles
                .filter { profile -> campsiteRepository.getCampsitesByOwner(profile.id).isNotEmpty() }
                .map { it.id }
                .toSet()
            ownerProfileIds = owners
            profiles = loadedProfiles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
            error = "Unable to load profiles."
        } finally {
            isLoading = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp

                AdminController(
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                )
            }
        }
    }
}
