/*
 * FakePlaceFinder.kt
 * A PlaceFinder for unit and intent tests (a test double): returns the places it was given instead
 * of asking the phone's Geocoder (US 02.01.02c).
 * Known issues: none. Lives in main (not test) so both unit tests and intent tests can use it.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.Place
import com.example.vipers.domain.repository.PlaceFinder
import java.io.IOException

/**
 * Returns up to the asked number of [places] for any address, or throws an [IOException] when
 * [fails] is true, like a lookup made offline.
 */
class FakePlaceFinder(
    private val places: List<Place> = emptyList(),
    private val fails: Boolean = false,
) : PlaceFinder {

    /** The addresses looked up so far, oldest first. */
    val searches = mutableListOf<String>()

    override suspend fun find(address: String, maxResults: Int): List<Place> {
        searches.add(address)
        if (fails) throw IOException("Lookup failed")
        return places.take(maxResults)
    }
}
