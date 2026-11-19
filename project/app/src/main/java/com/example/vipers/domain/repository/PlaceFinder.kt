/*
 * PlaceFinder.kt
 * Looks up the places that match an address, for Find location on the campsite form
 * (US 02.01.02c). GeocoderPlaceFinder uses Android's built-in Geocoder; FakePlaceFinder returns
 * set places for tests, so the form can be tested without a phone or a network.
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.Place

/** Finds [Place]s for an address. */
interface PlaceFinder {

    /**
     * Returns up to [maxResults] places that match [address], best match first, or an empty list
     * if nothing matches.
     *
     * @throws java.io.IOException if the lookup can't be done, e.g. offline.
     */
    suspend fun find(address: String, maxResults: Int): List<Place>
}
