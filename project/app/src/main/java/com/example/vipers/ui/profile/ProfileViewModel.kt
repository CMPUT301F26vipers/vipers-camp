/*
 * ProfileViewModel.kt
 * Holds the user's profile information and handles editing and saving it.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads and updates the current user's profile. */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val userId: String,
) : ViewModel() {

    private var profile: UserProfile? = null

    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    var errors by mutableStateOf(emptyMap<String, String>())
        private set

    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
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
                profile = profileRepository.getProfile(userId)
                val current = profile

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
    fun onNameChange(value: String) {
        name = value
        errors = errors - "name"
        saveFailed = false
        saved = false
    }

    /** Updates the email and clears its previous validation error. */
    fun onEmailChange(value: String) {
        email = value
        errors = errors - "email"
        saveFailed = false
        saved = false
    }

    /** Updates the phone and clears its previous validation error. */
    fun onPhoneChange(value: String) {
        phone = value
        errors = errors - "phone"
        saveFailed = false
        saved = false
    }

    /** Checks the form and saves the updated profile. */
    fun saveProfile() {
        val current = profile ?: return

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp
                val container = application.container

                ProfileViewModel(container.profileRepository, container.deviceId)
            }
        }
    }
}
