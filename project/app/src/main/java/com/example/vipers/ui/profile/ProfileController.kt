/*
 * ProfileController.kt
 * The controller (a ViewModel) behind Welcome (T-01), Profile (US 01.07.03) and the notifications
 * switch on Settings (US 01.10.01). Loads, creates, edits and saves the current user's profile
 * under this device's ID (US 01.09.01).
 * Known issues: Saving doesn't wait for the server (so it works offline).
 * If Firestore rejects the write, the app still opens Home, and Welcome
 * appears again on the next launch. Saving the Profile form and the Settings switch first reads
 * the latest saved profile (so they don't undo each other's changes). Offline, that read only
 * works if the profile is in Firestore's cache on the phone; if it isn't, saving shows
 * "Couldn't save" instead of saving for later.
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

    /** Whether this user gets notifications, as shown by the Settings switch (US 01.10.01). */
    var notificationsEnabled by mutableStateOf(true)
        private set

    /** True if saving the notifications switch failed; the switch is then set back. */
    var notificationsSaveFailed by mutableStateOf(false)
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
                    notificationsEnabled = current.notificationsEnabled
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
                // Settings may have changed the saved profile since this screen loaded it
                // (US 01.10.01), so the edits go onto the latest copy instead of the old one.
                val latest = profileRepository.getProfile(deviceId)
                val toSave = latest?.copy(
                    name = updated.name,
                    email = updated.email,
                    phone = updated.phone,
                ) ?: updated
                profileRepository.saveProfile(toSave)
                profile = toSave
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

    /** Turns notifications on or off for this user and saves the choice (US 01.10.01). */
    fun onNotificationsSwitchChange(enabled: Boolean) {
        viewModelScope.launch { saveNotificationsEnabled(enabled) }
    }

    /**
     * Saves [enabled] as this user's notification setting in their profile (US 01.10.01). The
     * switch moves straight away. It reads the latest saved profile first, so edits saved from
     * another screen are kept. If there is no profile or saving fails, the switch goes back and
     * [notificationsSaveFailed] is set. Unit tests call it directly.
     */
    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        val before = notificationsEnabled
        notificationsEnabled = enabled
        notificationsSaveFailed = false
        try {
            val latest = profileRepository.getProfile(deviceId)
            if (latest == null) {
                notificationsEnabled = before
                notificationsSaveFailed = true
                return
            }
            val updated = latest.copy(notificationsEnabled = enabled)
            profileRepository.saveProfile(updated)
            profile = updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notificationsEnabled = before
            notificationsSaveFailed = true
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
