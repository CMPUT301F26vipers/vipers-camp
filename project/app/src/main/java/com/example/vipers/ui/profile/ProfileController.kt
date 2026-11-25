/*
 * ProfileController.kt
 * The controller (a ViewModel) behind Welcome (T-01), Profile (US 01.07.03) and the notifications
 * switch on Settings (US 01.10.01). Loads, creates, edits and saves the current user's profile
 * under this device's ID (US 01.09.01). Also adds, replaces and removes the profile photo, which
 * is saved as soon as it's picked (US 01.08.02).
 * Known issues: Saving doesn't wait for the server (so it works offline).
 * If Firestore rejects the write, the app still opens Home, and Welcome
 * appears again on the next launch. Saving the Profile form, the Settings switch and the photo
 * first reads the latest saved profile. Offline, that read only works if the profile is in
 * Firestore's cache on the phone; if it isn't, saving shows "Couldn't save" instead of saving
 * for later. The photo and the switch only write their own field, so they can't undo each other.
 * A new photo, the profile change and deleting the old photo are one Firestore batch, so the
 * profile never points to a missing image. The Profile form saves the whole profile onto the
 * latest copy, so saving it at the exact moment the switch is flipped on Settings (a different
 * screen) could still undo the switch.
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
import com.example.vipers.data.device.DevicePhotoReader
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.PhotoReader
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Loads, creates and updates the profile for [deviceId], the current user's ID.
 * Takes the [profileRepository] in its constructor so tests can pass a fake one.
 * [photoReader] shrinks a picked profile photo, which the [profileRepository] saves with the
 * profile (US 01.08.02).
 */
class ProfileController(
    private val profileRepository: ProfileRepository,
    private val deviceId: String,
    private val photoReader: PhotoReader,
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

    /** The ID of the saved profile photo, or null if there is none (US 01.08.02). */
    var imageId by mutableStateOf<String?>(null)
        private set

    /** True while a picked photo is being got ready and saved, or the photo is being removed. */
    var isSavingPhoto by mutableStateOf(false)
        private set

    /** Why the last photo change didn't work, or null (US 01.08.02). */
    var photoError by mutableStateOf<String?>(null)
        private set

    /**
     * True if Change photo and Remove can be tapped: not while the photo or the form is being
     * saved, since both write the same profile.
     */
    val canChangePhoto: Boolean
        get() = !isSavingPhoto && !isSaving

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
                    imageId = current.imageId
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
        // a photo change saves the profile too, so wait for it to finish (US 01.08.02)
        if (errors.isNotEmpty() || isSaving || isSavingPhoto) return

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
     * switch moves straight away. Only this field is written, so edits saved from another screen
     * are kept. If there is no profile or saving fails, the switch goes back and
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
            // only this field, so a photo saved at the same time isn't undone (US 01.08.02)
            profileRepository.setNotificationsEnabled(deviceId, enabled)
            profile = latest.copy(notificationsEnabled = enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notificationsEnabled = before
            notificationsSaveFailed = true
        }
    }

    /** Gets the photo the user picked ready and saves it in the background; see [changePhoto]. */
    fun choosePhoto(uri: String) {
        if (!canChangePhoto) return
        // set here, not in the coroutine, so a second tap can't start another save
        isSavingPhoto = true
        viewModelScope.launch { changePhoto(uri) }
    }

    /** Clears [photoError], e.g. when the user opens the photo picker again. */
    fun clearPhotoError() {
        photoError = null
    }

    /** Removes the profile photo in the background; see [deletePhoto]. */
    fun removePhoto() {
        if (!canChangePhoto) return
        isSavingPhoto = true
        viewModelScope.launch { deletePhoto() }
    }

    /**
     * Shrinks the photo at [uri], saves it as a new image and makes it the profile photo
     * (US 01.08.02). The image is labelled with the user's name so admins can find it in the image
     * browser (US 03.06.01). The new image, the profile change and deleting the photo it replaces
     * are one write. If anything fails, [photoError] says what and the old photo stays. Unit tests
     * call it directly.
     */
    suspend fun changePhoto(uri: String, now: Long = System.currentTimeMillis()) {
        isSavingPhoto = true
        photoError = null
        try {
            val jpeg = try {
                photoReader.readJpeg(uri, ImageAsset.MAX_JPEG_BYTES)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoError = "Couldn't use that photo. Try another one."
                return
            }
            // the latest copy, for the user's current name and the photo being replaced
            val latest = profileRepository.getProfile(deviceId)
            if (latest == null) {
                photoError = "Couldn't save the photo. Try again."
                return
            }
            val image = ImageAsset.fromJpeg(
                jpeg,
                uploaderId = deviceId,
                ownerType = ImageAsset.OWNER_PROFILE,
                ownerId = deviceId,
                label = latest.name,
                createdAt = now,
            )
            if (!image.isSmallEnough()) {
                photoError = "This photo is too big. Try another one."
                return
            }
            // saves the image, points the profile at it and deletes the old one, all in one write
            val newId = profileRepository.setPhoto(deviceId, image, latest.imageId)
            profile = latest.copy(imageId = newId)
            imageId = newId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            photoError = "Couldn't save the photo. Try again."
        } finally {
            isSavingPhoto = false
        }
    }

    /**
     * Takes the photo off the profile, so the default avatar shows, and deletes the image in the
     * same write (US 01.08.02). If that fails, [photoError] says so and the photo stays. Unit
     * tests call it directly.
     */
    suspend fun deletePhoto() {
        isSavingPhoto = true
        photoError = null
        try {
            val latest = profileRepository.getProfile(deviceId)
            if (latest == null) {
                photoError = "Couldn't remove the photo. Try again."
                return
            }
            val oldId = latest.imageId
            if (oldId != null) {
                // takes it off the profile and deletes the image in one write
                profileRepository.setPhoto(deviceId, null, oldId)
                profile = latest.copy(imageId = null)
            }
            imageId = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            photoError = "Couldn't remove the photo. Try again."
        } finally {
            isSavingPhoto = false
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
        /**
         * Makes the controller with the app's real repositories, this device's ID and its photo
         * reader.
         */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                ProfileController(
                    app.container.profileRepository,
                    app.container.deviceId,
                    DevicePhotoReader(app),
                )
            }
        }
    }
}
