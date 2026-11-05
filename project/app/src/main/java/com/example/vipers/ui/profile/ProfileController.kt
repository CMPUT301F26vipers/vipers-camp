/*
 * ProfileController.kt
 * The controller (a ViewModel) behind the Welcome screen (T-01). It holds the name, email and
 * phone the user types and creates their profile under this device's ID (US 01.09.01).
 * Known issues: editing the profile from the Profile tab is added in US 01.07.03. Saving
 * doesn't wait for the server (so it works offline), so if Firestore rejects the write the app
 * still opens Home, and Welcome shows again on the next launch.
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
 * Creates the profile for [deviceId], the current user's ID.
 * Takes the [profileRepository] in its constructor so tests can pass a fake one.
 */
class ProfileController(
    private val profileRepository: ProfileRepository,
    private val deviceId: String,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    /** True while the profile is being saved. */
    var isSaving by mutableStateOf(false)
        private set

    /** True once the profile is saved; the app then opens Home. */
    var created by mutableStateOf(false)
        private set

    /** True if saving failed. */
    var saveFailed by mutableStateOf(false)
        private set

    fun onNameChange(text: String) { name = text }
    fun onEmailChange(text: String) { email = text }
    fun onPhoneChange(text: String) { phone = text }

    /** Continue stays disabled until a name is entered (email and phone are optional). */
    val canContinue: Boolean
        get() = name.isNotBlank() && !isSaving

    /** Saves the profile in the background, if a name has been entered. */
    fun createProfile() {
        if (!canContinue) return
        viewModelScope.launch { save(toProfile()) }
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
