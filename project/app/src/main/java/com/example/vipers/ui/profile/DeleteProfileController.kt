/*
 * DeleteProfileController.kt
 * The controller (a ViewModel) behind "Delete Profile" on the Profile screen (US 01.08.01,
 * mockup E18): checks if the user owns campsites so the dialog can warn about them, then deletes
 * the profile through DeletionService.
 * Known issues: deleting needs a connection (the lottery and reservation steps are
 * transactions), so offline it shows an error and nothing is deleted.
 */
package com.example.vipers.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.service.DeletionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Deletes the profile of [deviceId], the current user, with [deletionService]. Reads
 * [campsiteRepository] to know if they own campsites. Tests pass fake repositories.
 */
class DeleteProfileController(
    private val deletionService: DeletionService,
    private val campsiteRepository: CampsiteRepository,
    private val deviceId: String,
) : ViewModel() {

    /** True while the "Delete your profile?" dialog is open. */
    var isConfirming by mutableStateOf(false)
        private set

    /**
     * Whether the user owns campsites that would be removed too, or null if that couldn't be
     * checked (the dialog then warns anyway).
     */
    var ownsCampsites by mutableStateOf<Boolean?>(false)
        private set

    /** True while the profile is being deleted. */
    var isDeleting by mutableStateOf(false)
        private set

    /** True once the profile is deleted; the app then goes back to Welcome (T-01). */
    var deleted by mutableStateOf(false)
        private set

    /** Why deleting didn't work, or null. */
    var deleteError by mutableStateOf<String?>(null)
        private set

    /** Opens the dialog after checking if the user owns campsites. */
    fun askToDelete() {
        if (isDeleting) return
        deleteError = null
        viewModelScope.launch {
            ownsCampsites = try {
                campsiteRepository.getCampsitesByOwner(deviceId).isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            isConfirming = true
        }
    }

    /** Closes the dialog without deleting anything. */
    fun cancel() {
        isConfirming = false
    }

    /** Closes the dialog and deletes the profile in the background; see [delete]. */
    fun confirm() {
        if (isDeleting) return
        isConfirming = false
        isDeleting = true
        viewModelScope.launch { delete() }
    }

    /**
     * Deletes the profile and sets [deleted], or [deleteError] if it fails (the profile is then
     * kept, so the user can try again). Unit tests call it directly.
     */
    suspend fun delete() {
        isDeleting = true
        deleteError = null
        try {
            deletionService.deleteProfile(deviceId)
            deleted = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deleteError = "Couldn't delete your profile. Check your connection and try again."
        } finally {
            isDeleting = false
        }
    }

    companion object {
        /** Makes the controller with the app's real repositories and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                DeleteProfileController(
                    app.container.deletionService,
                    app.container.campsiteRepository,
                    app.container.deviceId,
                )
            }
        }
    }
}
