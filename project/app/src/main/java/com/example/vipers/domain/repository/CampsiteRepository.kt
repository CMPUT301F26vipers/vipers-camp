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

    /** Saves a new [campsite] and returns the ID it was given. */
    suspend fun addCampsite(campsite: Campsite): String

    /** Loads the campsite with this [id], or returns null if there is none. */
    suspend fun getCampsite(id: String): Campsite?

    /** Loads the campsites listed by [ownerId] that haven't been removed (My Campsites). */
    suspend fun getCampsitesByOwner(ownerId: String): List<Campsite>

    /** Loads every campsite that hasn't been removed (Home search). */
    suspend fun getAllCampsites(): List<Campsite>

    /** Loads the open and booked nights of [campsiteId], or an empty calendar if none are set. */
    suspend fun getAvailability(campsiteId: String): AvailabilityCalendar

    /** Saves [calendar] for its campsite, replacing the old one. */
    suspend fun saveAvailability(calendar: AvailabilityCalendar)
}
