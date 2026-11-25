/*
 * PlaceTest.kt
 * Unit tests for Place, a place Find location found for an address (US 02.01.02c).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaceTest {

    @Test
    fun coordinatesText_roundsToThreeDecimals() {
        val hinton = Place("Hinton", "Alberta, Canada", lat = 53.40512, lng = -117.58468)

        assertEquals("53.405, -117.585", hinton.coordinatesText())
    }

    @Test
    fun coordinatesText_padsShortNumbers() {
        assertEquals("53.400, -117.000", Place.coordinatesText(53.4, -117.0))
    }

    @Test
    fun coordinatesText_usesADotWhateverThePhoneLanguage() {
        val before = Locale.getDefault()
        try {
            // French phones write decimals with a comma
            Locale.setDefault(Locale.FRANCE)
            assertEquals("53.405, -117.585", Place.coordinatesText(53.405, -117.585))
        } finally {
            Locale.setDefault(before)
        }
    }
}
