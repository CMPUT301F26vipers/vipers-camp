/*
 * AdminController.kt
 * Loads campsites and images for the admin browsing screens (US 03.04.01, US 03.06.01).
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
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads campsites and images for the admin browsing screens. */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository? = null,
) : ViewModel() {

    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    var ownerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loadFailed by mutableStateOf(false)
        private set

    var images by mutableStateOf<List<ImageAsset>>(emptyList())
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

    /** Returns the image asset with the given [imageId], or null if not found. */
    fun getImage(imageId: String): ImageAsset? {
        return images.firstOrNull { it.id == imageId }
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
            images = repo.getAllImages()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            imagesLoadFailed = true
            imagesError = "Unable to load images."
        } finally {
            isImagesLoading = false
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
                    imageRepository = application.container.imageRepository,
                )
            }
        }
    }
}
