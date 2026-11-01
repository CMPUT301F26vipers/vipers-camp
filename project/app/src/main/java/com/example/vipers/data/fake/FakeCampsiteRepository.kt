/*
 * FakeCampsiteRepository.kt
 * An in-memory CampsiteRepository for unit and intent tests (a test double for the Repository
 * pattern). Nothing is saved to Firestore, and everything is lost when the app closes.
 * Known issues: none. Lives in main (not test) so both unit tests and intent tests can use it.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.repository.CampsiteRepository

/** Keeps campsites in a map instead of Firestore. Pass [initial] to start with some campsites. */
class FakeCampsiteRepository(initial: List<Campsite> = emptyList()) : CampsiteRepository {

    // Campsites by ID, like documents in a Firestore collection.
    private val campsites = initial.associateBy { it.id }.toMutableMap()
    private val calendars = mutableMapOf<String, AvailabilityCalendar>()
    private var nextId = 1

    override suspend fun addCampsite(campsite: Campsite): String {
        val id = "campsite-${nextId++}"
        campsites[id] = campsite.copy(id = id)
        return id
    }

    override suspend fun getCampsite(id: String): Campsite? = campsites[id]

    override suspend fun getCampsitesByOwner(ownerId: String): List<Campsite> =
        campsites.values.filter { it.ownerId == ownerId && !it.removed }

    override suspend fun getAllCampsites(): List<Campsite> =
        campsites.values.filter { !it.removed }

    override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
        calendars[campsiteId] ?: AvailabilityCalendar(campsiteId = campsiteId)

    override suspend fun saveAvailability(calendar: AvailabilityCalendar) {
        calendars[calendar.campsiteId] = calendar
    }
}
