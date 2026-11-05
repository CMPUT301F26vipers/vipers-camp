/*
 * ProfileController.kt
 * The controller (a ViewModel) behind Welcome (T-01) and Profile (US 01.07.03).
 * Loads, creates, edits and saves the current user's profile under this device's ID (US 01.09.01).
 * Known issues: none.
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
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads, creates and updates the profile for [deviceId], the current user's ID.
 * Takes the [profileRepository] in its constructor so tests can pass a fake one.
 */
class ProfileController(
    private val profileRepository: ProfileRepository,
    private val deviceId: String,
) : ViewModel() {

    private var profile: UserProfile? = null

    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    /** Field-specific validation errors, keyed by field name. */
    var errors by mutableStateOf(emptyMap<String, String>())
        private set

    /** True while the profile is being loaded from the repository. */
    var isLoading by mutableStateOf(true)
        private set

    /** True while the profile is being saved. */
    var isSaving by mutableStateOf(false)
        private set

    /** True once the profile is created on Welcome screen; the app then opens Home. */
    var created by mutableStateOf(false)
        private set

    /** True once profile edits have been saved from the Profile screen. */
    var saved by mutableStateOf(false)
        private set

    /** True if loading the profile failed. */
    var loadFailed by mutableStateOf(false)
        private set

    /** True if saving failed. */
    var saveFailed by mutableStateOf(false)
        private set

    init {
        loadProfile()
    }

    /** Loads the profile and fills the form with its current information. */
    fun loadProfile() {
        viewModelScope.launch {
            isLoading = true
            loadFailed = false

            try {
                val current = profileRepository.getProfile(deviceId)
                profile = current

                if (current != null) {
                    name = current.name
                    email = current.email
                    phone = current.phone
                } else {
                    loadFailed = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    /** Updates the name and clears its previous validation error. */
    fun onNameChange(text: String) {
        name = text
        errors = errors - "name"
        saveFailed = false
        saved = false
    }

    /** Updates the email and clears its previous validation error. */
    fun onEmailChange(text: String) {
        email = text
        errors = errors - "email"
        saveFailed = false
        saved = false
    }

    /** Updates the phone and clears its previous validation error. */
    fun onPhoneChange(text: String) {
        phone = text
        errors = errors - "phone"
        saveFailed = false
        saved = false
    }

    /** Clears the saved confirmation flag after it has been displayed. */
    fun clearSaved() {
        saved = false
    }

    /** Continue stays disabled until a name is entered (email and phone are optional). */
    val canContinue: Boolean
        get() = name.isNotBlank() && !isSaving

    /** Saves the profile in the background, if a name has been entered (Welcome screen flow). */
    fun createProfile() {
        if (!canContinue) return
        viewModelScope.launch { save(toProfile()) }
    }

    /** Checks the form and saves the updated profile (Profile screen editing flow). */
    fun saveProfile() {
        val current = profile ?: toProfile()

        val updated = current.copy(
            name = name.trim(),
            email = email.trim(),
            phone = phone.trim(),
        )

        saved = false
        errors = updated.validateEdit()
        if (errors.isNotEmpty() || isSaving) return

        viewModelScope.launch {
            isSaving = true
            saveFailed = false
            saved = false

            try {
                profileRepository.saveProfile(updated)
                profile = updated
                // The form may have changed while the repository was saving.
                saved = name.trim() == updated.name &&
                    email.trim() == updated.email && phone.trim() == updated.phone
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed = true
            } finally {
                isSaving = false
            }
        }
    }

    /** Saves [profile] and sets [created] or [saveFailed]. Unit tests call it directly. */
    suspend fun save(profile: UserProfile) {
        isSaving = true
        saveFailed = false
        try {
            profileRepository.saveProfile(profile)
            created = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveFailed = true
        } finally {
            isSaving = false
        }
    }

    /** The profile the form describes right now, with this device's ID as its ID. */
    fun toProfile() = UserProfile(
        id = deviceId,
        name = name.trim(),
        email = email.trim(),
        phone = phone.trim(),
        createdAt = System.currentTimeMillis(),
    )

    companion object {
        /** Makes the controller with the app's real repository and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ProfileController(app.container.profileRepository, app.container.deviceId)
            }
        }
    }
}
