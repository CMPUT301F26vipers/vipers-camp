/*
 * CampsiteController.kt
 * The controller (a ViewModel) behind the Add Campsite form (US 02.01.01, 02.01.02a). It holds
 * what the owner has typed, checks it, and saves the campsite with the owner's device ID. It can
 * also fill the form from a saved campsite and save its owner's changes (US 02.01.03), including
 * the nightly price (T-09), its optional rules and cancellation policy (T-11), and its optional map
 * location, which Find location looks up from the address (US 02.01.02c).
 * Known issues: the photo (02.01.02b) is added to the form by that story. Until then an edit keeps
 * its saved value, and that story needs to add it to withFormValues.
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
import com.example.vipers.data.device.GeocoderPlaceFinder
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Place
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.PlaceFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds the Add Campsite and Edit Campsite form, and publishes the campsite for [ownerId] or saves
 * the changes to it. [placeFinder] looks up the address for Find location (US 02.01.02c).
 * Takes the [campsiteRepository] and [placeFinder] in its constructor so tests can pass fake ones.
 */
class CampsiteController(
    private val campsiteRepository: CampsiteRepository,
    private val ownerId: String,
    private val placeFinder: PlaceFinder,
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

    /** Nightly price in dollars as typed, e.g. "65" or "65.50", checked when publishing (T-09). */
    var price by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set

    /** The tag being typed, before the owner taps Add. */
    var tagInput by mutableStateOf("")
        private set
    var tags by mutableStateOf(emptyList<String>())
        private set

    /** Campsite rules as typed; optional, so empty means none (T-11). */
    var rules by mutableStateOf("")
        private set

    /** Cancellation policy as typed; optional, so empty means none (T-11). */
    var cancellationPolicy by mutableStateOf("")
        private set

    /** The map location's latitude, or null if the campsite has none (US 02.01.02c). */
    var lat by mutableStateOf<Double?>(null)
        private set

    /** The map location's longitude, or null if the campsite has none (US 02.01.02c). */
    var lng by mutableStateOf<Double?>(null)
        private set

    /** The name of the place chosen with Find location, or null if none was chosen this time. */
    var placeName by mutableStateOf<String?>(null)
        private set

    /** The places found for the address; the "Choose a place" dialog (O03) shows while any are. */
    var places by mutableStateOf(emptyList<Place>())
        private set

    /** True while Find location is looking up the address. */
    var isFindingPlaces by mutableStateOf(false)
        private set

    /** Why Find location has no places to show, or null (US 02.01.02c). */
    var findLocationError by mutableStateOf<String?>(null)
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
    /** Updates the name as the owner types and clears its error. */
    fun onNameChange(text: String) { name = text; clearError("name") }
    /** Updates the address as the owner types and clears its errors. */
    fun onAddressChange(text: String) {
        address = text
        clearError("address")
        findLocationError = null
    }
    /** Sets the region the owner picked and clears its error. */
    fun onRegionChange(choice: Region) { region = choice; clearError("region") }
    /** Updates max guests as typed and clears its error; it's checked when publishing. */
    fun onMaxGuestsChange(text: String) { maxGuests = text; clearError("maxOccupancy") }
    /** Updates the nightly price as typed and clears its error; it's checked when publishing (T-09). */
    fun onPriceChange(text: String) { price = text; clearError("pricePerNightCents") }
    /** Updates the description as the owner types; it's optional. */
    fun onDescriptionChange(text: String) { description = text }
    /** Updates the tag being typed, before the owner taps Add. */
    fun onTagInputChange(text: String) { tagInput = text }

    /** Updates the rules as the owner types (T-11). */
    fun onRulesChange(text: String) { rules = text }

    /** Updates the cancellation policy as the owner types (T-11). */
    fun onCancellationPolicyChange(text: String) { cancellationPolicy = text }

    /** Adds the typed tag, unless it's empty or already added (ignoring case). */
    fun addTag() {
        val tag = tagInput.trim()
        if (tag.isNotEmpty() && tags.none { it.equals(tag, ignoreCase = true) }) {
            tags = tags + tag
        }
        tagInput = ""
    }

    /** Removes [tag] from the campsite's tags. */
    fun removeTag(tag: String) {
        tags = tags - tag
    }

    /** Looks up the typed address in the background (US 02.01.02c); see [fetchPlaces]. */
    fun findLocation() {
        if (isFindingPlaces) return
        viewModelScope.launch { fetchPlaces() }
    }

    /**
     * Looks up the typed address and puts up to [MAX_PLACES] matches in [places], for the owner to
     * choose from (US 02.01.02c). If the address is empty, nothing matches or the lookup fails,
     * [findLocationError] says so instead. Unit tests call it directly.
     */
    suspend fun fetchPlaces() {
        places = emptyList()
        findLocationError = null
        val typed = address.trim()
        if (typed.isEmpty()) {
            findLocationError = "Enter an address first"
            return
        }
        isFindingPlaces = true
        try {
            val found = placeFinder.find(typed, MAX_PLACES)
            if (found.isEmpty()) {
                findLocationError = "No places found. Check the address and try again."
            } else {
                places = found
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            findLocationError = "Couldn't look up the address. Check your connection and try again."
        } finally {
            isFindingPlaces = false
        }
    }

    /** Uses [place]'s coordinates as the campsite's map location and closes the dialog. */
    fun choosePlace(place: Place) {
        lat = place.lat
        lng = place.lng
        placeName = place.name
        places = emptyList()
        clearError("mapLocation")
    }

    /** Closes the "Choose a place" dialog without changing the map location. */
    fun dismissPlaces() {
        places = emptyList()
    }

    /** Removes the map location, so the campsite is saved without one (US 02.01.02c). */
    fun removeMapLocation() {
        lat = null
        lng = null
        placeName = null
        clearError("mapLocation")
    }

    /**
     * True if leaving should ask first: when adding, if the owner has filled in anything; when
     * editing, if the form differs from what was loaded (US 02.01.03).
     */
    val hasUnsavedChanges: Boolean
        get() {
            if (published) return false
            val loaded = loadedCampsite
            if (mode == FormMode.EDIT && loaded != null) {
                return loaded.withFormValues(toCampsite()) != loaded
            }
            return name.isNotBlank() || address.isNotBlank() || region != null ||
                maxGuests.isNotBlank() || price.isNotBlank() || description.isNotBlank() ||
                tags.isNotEmpty() || rules.isNotBlank() || cancellationPolicy.isNotBlank() ||
                lat != null
        }

    /**
     * Checks the form and, if everything is filled in correctly, saves the campsite in the
     * background: a new one with [save], or the owner's changes with [saveEdits] (US 02.01.03).
     * If something is wrong, [errors] says what and nothing is saved.
     */
    fun publish() {
        if (isSaving) return
        val campsite = toCampsite()
        val formErrors = campsite.validate().toMutableMap()
        // The model can't tell a missing region or a non-number apart from a valid default,
        // so the form checks those itself.
        if (region == null) formErrors["region"] = "Choose a region"
        if (maxGuests.isBlank()) {
            formErrors["maxOccupancy"] = "Enter max guests"
        } else if (maxGuests.trim().toIntOrNull() == null) {
            formErrors["maxOccupancy"] = "Enter a whole number"
        }
        if (price.isBlank()) {
            formErrors["pricePerNightCents"] = "Enter a price"
        } else if (priceInCents() == null) {
            formErrors["pricePerNightCents"] = "Enter dollars, e.g. 65.50"
        }
        errors = formErrors
        if (formErrors.isNotEmpty()) return

        viewModelScope.launch {
            if (mode == FormMode.EDIT) saveEdits(campsite) else save(campsite)
        }
    }

    /** Saves [campsite] and sets [published] or [saveFailed]. Unit tests call it directly. */
    suspend fun save(campsite: Campsite) {
        runSave {
            campsiteRepository.addCampsite(campsite)
            published = true
        }
    }

    /**
     * Saves the form values in [campsite] over the campsite [loadForEdit] loaded, keeping its ID and
     * every field the form doesn't have (US 02.01.03). First re-checks the saved campsite still
     * belongs to this owner; if not, sets [notAllowed] and saves nothing. Sets [published] or
     * [saveFailed] like [save]. Unit tests call it directly.
     */
    suspend fun saveEdits(campsite: Campsite) {
        runSave {
            val loaded = loadedCampsite
            val saved = loaded?.let { campsiteRepository.getCampsite(it.id) }
            if (saved == null || !saved.isOwnedBy(ownerId)) {
                notAllowed = true
            } else {
                campsiteRepository.updateCampsite(loaded.withFormValues(campsite))
                published = true
            }
        }
    }

    // What save and saveEdits share: runs write, tracking isSaving and saveFailed.
    private suspend fun runSave(write: suspend () -> Unit) {
        isSaving = true
        saveFailed = false
        try {
            write()
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
        price = campsite.priceInDollars()
        description = campsite.description
        tags = campsite.tags
        rules = campsite.rules
        cancellationPolicy = campsite.cancellationPolicy
        lat = campsite.lat
        lng = campsite.lng
        placeName = null
    }

    /** The campsite the form describes right now. */
    fun toCampsite() = Campsite(
        ownerId = ownerId,
        name = name.trim(),
        address = address.trim(),
        region = region ?: Region.BANFF, // only used if publish() has already checked it
        description = description.trim(),
        maxOccupancy = maxGuests.trim().toIntOrNull() ?: 0,
        pricePerNightCents = priceInCents() ?: 0, // only used if publish() has already checked it
        tags = tags,
        rules = rules.trim(),
        cancellationPolicy = cancellationPolicy.trim(),
        lat = lat,
        lng = lng,
    )

    // This campsite with the fields the form has replaced by those in form.
    private fun Campsite.withFormValues(form: Campsite) = copy(
        name = form.name,
        address = form.address,
        region = form.region,
        description = form.description,
        maxOccupancy = form.maxOccupancy,
        pricePerNightCents = form.pricePerNightCents,
        tags = form.tags,
        rules = form.rules,
        cancellationPolicy = form.cancellationPolicy,
        lat = form.lat,
        lng = form.lng,
    )

    // The typed price in cents ("65.5" is 6550), or null unless it's a number of dollars, 0 or
    // more, with at most 2 decimal places. A "$" in front is allowed.
    private fun priceInCents(): Long? {
        val parts = price.trim().removePrefix("$").split(".")
        val dollars = parts[0]
        val decimals = if (parts.size == 2) parts[1] else ""
        if (parts.size > 2 || dollars.isEmpty() || decimals.length > 2) return null
        // only digits, so "-5" and "1e3" aren't prices
        if (!dollars.all { it.isDigit() } || !decimals.all { it.isDigit() }) return null
        val wholeDollars = dollars.toLongOrNull() ?: return null
        // "5" after the dot is 50 cents
        return wholeDollars * 100 + decimals.padEnd(2, '0').toLong()
    }

    private fun clearError(field: String) {
        errors = errors - field
    }

    companion object {
        /** The most places Find location shows for one address (US 02.01.02c). */
        const val MAX_PLACES = 5

        /**
         * Makes the controller with the app's real repository, this device's ID and the phone's
         * Geocoder.
         */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                CampsiteController(
                    app.container.campsiteRepository,
                    app.container.deviceId,
                    GeocoderPlaceFinder(app),
                )
            }
        }
    }
}
