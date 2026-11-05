/*
 * CampsiteController.kt
 * The controller (a ViewModel) behind the Add Campsite form (US 02.01.01, 02.01.02a). It holds
 * what the owner has typed, checks it, and saves the campsite with the owner's device ID. It can
 * also fill the form from a saved campsite so its owner can edit it (US 02.01.03).
 * Known issues: photo (02.01.02b), Find location (02.01.02c), price (T-09), and rules and
 * cancellation policy (T-11) are added to the form by those stories.
 */
package com.example.vipers.ui.campsites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds the Add Campsite form and publishes the campsite for [ownerId].
 * Takes the [campsiteRepository] in its constructor so tests can pass a fake one.
 */
class CampsiteController(
    private val campsiteRepository: CampsiteRepository,
    private val ownerId: String,
) : ViewModel() {

    /** Whether the form is adding a new campsite or editing a saved one (US 02.01.03). */
    var mode by mutableStateOf(FormMode.CREATE)
        private set

    /** The saved campsite being edited, or null unless [loadForEdit] filled the form (US 02.01.03). */
    var loadedCampsite by mutableStateOf<Campsite?>(null)
        private set

    /** True if the campsite to edit doesn't exist or isn't this owner's (US 02.01.03). */
    var notAllowed by mutableStateOf(false)
        private set

    /** True if the campsite to edit couldn't be loaded, e.g. offline with no saved copy (US 02.01.03). */
    var loadFailed by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set
    var address by mutableStateOf("")
        private set

    /** The chosen region, or null until the owner picks one from the dropdown. */
    var region by mutableStateOf<Region?>(null)
        private set

    /** Max guests as typed, so we can tell the owner if it isn't a whole number. */
    var maxGuests by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set

    /** The tag being typed, before the owner taps Add. */
    var tagInput by mutableStateOf("")
        private set
    var tags by mutableStateOf(emptyList<String>())
        private set

    /** Error message for each field that's wrong, keyed by field name ("name", "region"...). */
    var errors by mutableStateOf(emptyMap<String, String>())
        private set

    /** True while the campsite is being saved. */
    var isSaving by mutableStateOf(false)
        private set

    /** True once the campsite has been saved; the screen then shows a message and closes. */
    var published by mutableStateOf(false)
        private set

    /** True if saving failed. Being offline isn't a failure; Firestore uploads it later. */
    var saveFailed by mutableStateOf(false)
        private set

    // Typing in a field clears its error, so the message goes away once it's fixed.
    fun onNameChange(text: String) { name = text; clearError("name") }
    fun onAddressChange(text: String) { address = text; clearError("address") }
    fun onRegionChange(choice: Region) { region = choice; clearError("region") }
    fun onMaxGuestsChange(text: String) { maxGuests = text; clearError("maxOccupancy") }
    fun onDescriptionChange(text: String) { description = text }
    fun onTagInputChange(text: String) { tagInput = text }

    /** Adds the typed tag, unless it's empty or already added (ignoring case). */
    fun addTag() {
        val tag = tagInput.trim()
        if (tag.isNotEmpty() && tags.none { it.equals(tag, ignoreCase = true) }) {
            tags = tags + tag
        }
        tagInput = ""
    }

    fun removeTag(tag: String) {
        tags = tags - tag
    }

    /** True if the owner has filled in anything, so leaving should ask first. */
    val hasUnsavedChanges: Boolean
        get() = !published && (
            name.isNotBlank() || address.isNotBlank() || region != null ||
                maxGuests.isNotBlank() || description.isNotBlank() || tags.isNotEmpty()
            )

    /**
     * Checks the form and, if everything is filled in correctly, saves the campsite in the
     * background. If something is wrong, [errors] says what and nothing is saved.
     */
    fun publish() {
        if (isSaving) return
        val campsite = toCampsite()
        val formErrors = campsite.validate().toMutableMap()
        // The model can't tell a missing region or a non-number apart from a valid default,
        // so the form checks those two itself.
        if (region == null) formErrors["region"] = "Choose a region"
        if (maxGuests.isBlank()) {
            formErrors["maxOccupancy"] = "Enter max guests"
        } else if (maxGuests.trim().toIntOrNull() == null) {
            formErrors["maxOccupancy"] = "Enter a whole number"
        }
        errors = formErrors
        if (formErrors.isNotEmpty()) return

        viewModelScope.launch { save(campsite) }
    }

    /** Saves [campsite] and sets [published] or [saveFailed]. Unit tests call it directly. */
    suspend fun save(campsite: Campsite) {
        isSaving = true
        saveFailed = false
        try {
            campsiteRepository.addCampsite(campsite)
            published = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saveFailed = true
        } finally {
            isSaving = false
        }
    }

    /**
     * Loads the campsite with this [campsiteId] for editing (US 02.01.03). If it exists and belongs
     * to this owner, switches to [FormMode.EDIT] and fills in the form. Otherwise sets [notAllowed]
     * and fills in nothing. If the load itself fails, sets [loadFailed] and fills in nothing.
     */
    suspend fun loadForEdit(campsiteId: String) {
        notAllowed = false
        loadFailed = false
        try {
            val campsite = campsiteRepository.getCampsite(campsiteId)
            if (campsite != null && campsite.isOwnedBy(ownerId)) {
                fillForm(campsite)
            } else {
                notAllowed = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    private fun fillForm(campsite: Campsite) {
        loadedCampsite = campsite
        mode = FormMode.EDIT
        name = campsite.name
        address = campsite.address
        region = campsite.region
        maxGuests = campsite.maxOccupancy.toString()
        description = campsite.description
        tags = campsite.tags
    }

    /** The campsite the form describes right now. */
    fun toCampsite() = Campsite(
        ownerId = ownerId,
        name = name.trim(),
        address = address.trim(),
        region = region ?: Region.BANFF, // only used if publish() has already checked it
        description = description.trim(),
        maxOccupancy = maxGuests.trim().toIntOrNull() ?: 0,
        tags = tags,
    )

    private fun clearError(field: String) {
        errors = errors - field
    }

    companion object {
        /** Makes the controller with the app's real repository and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteController(app.container.campsiteRepository, app.container.deviceId)
            }
        }
    }
}
