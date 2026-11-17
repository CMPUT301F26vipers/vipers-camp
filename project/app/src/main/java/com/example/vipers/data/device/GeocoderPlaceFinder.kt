/*
 * GeocoderPlaceFinder.kt
 * The real PlaceFinder: looks up an address with Android's built-in Geocoder (US 02.01.02c), so
 * no map library or API key is needed.
 * Known issues: the Geocoder needs a network connection, and some phones have no Geocoder service
 * at all; both make find() throw, and the form shows an error. Uses the older blocking lookup on a
 * background thread, which still works on every Android version the app supports.
 */
package com.example.vipers.data.device

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.example.vipers.domain.model.Place
import com.example.vipers.domain.repository.PlaceFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Finds places with the phone's Geocoder, using [context] for its language. */
class GeocoderPlaceFinder(private val context: Context) : PlaceFinder {

    override suspend fun find(address: String, maxResults: Int): List<Place> {
        if (!Geocoder.isPresent()) throw IOException("This phone has no Geocoder")
        // the lookup goes over the network, so it runs off the main thread
        val found = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            Geocoder(context).getFromLocationName(address, maxResults)
        }
        return found.orEmpty()
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { it.toPlace() }
    }
}

// A Geocoder result as a Place: its own name (or first address line), then the province and
// country it's in.
private fun Address.toPlace(): Place {
    val name = featureName ?: locality ?: getAddressLine(0) ?: "Unnamed place"
    val area = listOfNotNull(locality, adminArea, countryName)
        .filter { it != name }
        .joinToString(", ")
    return Place(name = name, area = area, lat = latitude, lng = longitude)
}
