/*
 * CampsiteRepository.kt
 * What the app can do with campsites, without saying how they are stored (Repository pattern).
 * FirestoreCampsiteRepository stores them in Firestore; FakeCampsiteRepository keeps them in
 * memory for tests.
 * Known issues: only the methods needed for the half-way checkpoint. Search, ratings and
 * removal from the CRC card are added by the stories that need them.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite

/** Saves and loads [Campsite]s. */
interface CampsiteRepository {

    /**
     * Saves a new [campsite] and returns its ID: [campsite]'s own ID if it has one (e.g. from
     * [newCampsiteId]), or a new one. Offline, the campsite is saved on the phone first and
     * uploaded once it reconnects.
     */
    suspend fun addCampsite(campsite: Campsite): String

    /**
     * Makes a new, unused campsite ID without saving anything, so a photo can be saved for a
     * campsite before the campsite itself (US 02.01.02b).
     */
    fun newCampsiteId(): String

    /** Loads the campsite with this [id], or returns null if there is none. */
    suspend fun getCampsite(id: String): Campsite?

    /**
     * Saves the details an owner can edit (name, address, region, description, maxOccupancy, tags,
     * pricePerNightCents, rules, cancellationPolicy, the map location lat and lng, and the photo's
     * imageId) to the campsite with [campsite]'s ID (US 02.01.03, 02.01.02c, 02.01.02b). Every
     * other stored field keeps its saved value, whatever [campsite] holds.
     *
     * @throws NoSuchElementException if there is no campsite with that ID.
     */
    suspend fun updateCampsite(campsite: Campsite)

    /** Loads the campsites listed by [ownerId] that haven't been removed (My Campsites). */
    suspend fun getCampsitesByOwner(ownerId: String): List<Campsite>

    /** Loads every campsite that hasn't been removed (Home search). */
    suspend fun getAllCampsites(): List<Campsite>

    /** Loads the open and booked nights of [campsiteId], or an empty calendar if none are set. */
    suspend fun getAvailability(campsiteId: String): AvailabilityCalendar

    /** Saves [calendar] for its campsite, replacing the old one. */
    suspend fun saveAvailability(calendar: AvailabilityCalendar)
}
