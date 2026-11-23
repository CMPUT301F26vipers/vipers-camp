/*
 * AdminController.kt
 * Loads campsites, user profiles, and images for the admin browsing screens (US 03.04.01, US 03.05.01, US 03.06.01).
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
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads campsites, user profiles, and images for the admin browsing screens. */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository? = null,
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

    /** Whether user profiles are currently being loaded. */
    var isProfilesLoading by mutableStateOf(false)
        private set

    val profilesLoading: Boolean
        get() = isProfilesLoading

    /** Whether the most recent attempt to load profiles failed. */
    var profilesLoadFailed by mutableStateOf(false)
        private set

    /** Error message if loading profiles failed. */
    var profilesError by mutableStateOf<String?>(null)
        private set

    val profileError: String?
        get() = profilesError

    var images by mutableStateOf<List<ImageAsset>>(emptyList())
        private set

    /** Maps image IDs to their resolved owner or fallback display names. */
    var imageOwnerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isImagesLoading by mutableStateOf(false)
        private set

    val imagesLoading: Boolean
        get() = isImagesLoading

    var imagesLoadFailed by mutableStateOf(false)
        private set

    var imagesError by mutableStateOf<String?>(null)
        private set

    val imageError: String?
        get() = imagesError

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

    /** Returns the profile with the given [profileId] from the loaded profiles, or null if not found. */
    fun getProfile(profileId: String): UserProfile? {
        return profiles.firstOrNull { it.id == profileId }
    }

    /** Returns the active campsites owned by [ownerId] from the loaded campsites list. */
    fun getCampsitesForOwner(ownerId: String): List<Campsite> {
        return campsites.filter { it.ownerId == ownerId && !it.removed }
    }

    /** Returns the image asset with the given [imageId], or null if not found. */
    fun getImage(imageId: String): ImageAsset? {
        return images.firstOrNull { it.id == imageId }
    }

    /** Returns the resolved owner or fallback name for the given [image]. */
    fun getImageOwnerName(image: ImageAsset): String {
        return imageOwnerNames[image.id]
            ?: image.label.trim().ifBlank { null }
            ?: genericImageLabel(image.ownerType)
    }

    /** Returns the resolved owner or fallback name for the image with the given [imageId]. */
    fun getImageOwnerName(imageId: String): String {
        val image = getImage(imageId)
        return imageOwnerNames[imageId]
            ?: image?.let { getImageOwnerName(it) }
            ?: "Photo"
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
        isProfilesLoading = true
        profilesLoadFailed = false
        profilesError = null
        viewModelScope.launch { fetchProfiles() }
    }

    /** Loads all user profiles and determines campsite ownership. Can be called directly by unit tests. */
    internal suspend fun fetchProfiles() {
        isProfilesLoading = true
        profilesLoadFailed = false
        profilesError = null

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
            profilesLoadFailed = true
            profilesError = "Unable to load profiles."
        } finally {
            isProfilesLoading = false
        }
    }

    /** Loads all uploaded images in the background. */
    fun loadImages() {
        viewModelScope.launch { fetchImages() }
    }

    /** Loads all uploaded images, newest first. Can be called directly from unit tests using runBlocking. */
    suspend fun fetchImages() {
        isImagesLoading = true
        imagesLoadFailed = false
        imagesError = null

        try {
            val repo = imageRepository ?: throw IllegalStateException("ImageRepository not configured")
            val loadedImages = repo.getAllImages()

            val resolvedNames = mutableMapOf<String, String>()
            val ownerNameCache = mutableMapOf<Pair<String, String>, String?>()

            for (image in loadedImages) {
                val key = Pair(image.ownerType, image.ownerId)
                val resolvedOwner = ownerNameCache.getOrPut(key) {
                    try {
                        when (image.ownerType) {
                            ImageAsset.OWNER_CAMPSITE -> {
                                campsiteRepository.getCampsite(image.ownerId)?.name?.trim()?.ifBlank { null }
                            }
                            ImageAsset.OWNER_PROFILE -> {
                                profileRepository.getProfile(image.ownerId)?.name?.trim()?.ifBlank { null }
                            }
                            else -> null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalName = resolvedOwner
                    ?: image.label.trim().ifBlank { null }
                    ?: genericImageLabel(image.ownerType)

                resolvedNames[image.id] = finalName
            }

            imageOwnerNames = resolvedNames
            images = loadedImages
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            imagesLoadFailed = true
            imagesError = "Unable to load images."
        } finally {
            isImagesLoading = false
        }
    }

    private fun genericImageLabel(ownerType: String): String {
        return if (ownerType == ImageAsset.OWNER_CAMPSITE) "Campsite photo" else "Profile photo"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp

                AdminController(
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                    imageRepository = application.container.imageRepository,
                )
            }
        }
    }
}
