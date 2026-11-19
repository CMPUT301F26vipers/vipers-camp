/*
 * Place.kt
 * One place that Find location found for an address (US 02.01.02c): its name, the area it's in,
 * and its map coordinates. Shown in the "Choose a place" dialog (mockup O03); the coordinates of
 * the chosen one are saved with the campsite.
 * Known issues: none. Never stored; only the chosen coordinates are saved, in the campsite.
 */
package com.example.vipers.domain.model

import java.util.Locale

/**
 * A place found for an address.
 *
 * @property name What the place is called, e.g. "Hinton".
 * @property area The province and country it's in, e.g. "Alberta, Canada"; may be empty.
 * @property lat Latitude in degrees, -90 to 90.
 * @property lng Longitude in degrees, -180 to 180.
 */
data class Place(
    val name: String,
    val area: String,
    val lat: Double,
    val lng: Double,
) {
    /** The coordinates as the owner sees them, e.g. "53.405, -117.585". */
    fun coordinatesText(): String = coordinatesText(lat, lng)

    companion object {
        /**
         * [lat] and [lng] with 3 decimal places (about 100 m), e.g. "53.405, -117.585". Always uses
         * a dot for decimals, whatever the phone's language.
         */
        fun coordinatesText(lat: Double, lng: Double): String =
            String.format(Locale.US, "%.3f, %.3f", lat, lng)
    }
}
