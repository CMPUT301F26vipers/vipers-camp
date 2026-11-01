/*
 * CampsiteTest.kt
 * Unit tests for the Campsite model's methods.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteTest {

    @Test
    fun averageRating_isZeroWithNoRatings() {
        assertEquals(0.0, Campsite(ratingSum = 0, ratingCount = 0).averageRating(), 0.001)
    }

    @Test
    fun averageRating_keepsTheDecimal() {
        // 9 stars over 2 ratings is 4.5, not 4
        assertEquals(4.5, Campsite(ratingSum = 9, ratingCount = 2).averageRating(), 0.001)
    }

    @Test
    fun priceText_showsWholeDollarsPerNight() {
        assertEquals("$50 / night", Campsite(pricePerNightCents = 5000).priceText())
    }

    @Test
    fun priceText_showsCentsWhenThereAreSome() {
        assertEquals("$65.50 / night", Campsite(pricePerNightCents = 6550).priceText())
        assertEquals("$65.05 / night", Campsite(pricePerNightCents = 6505).priceText())
    }

    @Test
    fun hasLocation_needsBothCoordinates() {
        assertTrue(Campsite(lat = 53.405, lng = -117.585).hasLocation())
        assertFalse(Campsite(lat = 53.405).hasLocation())
        assertFalse(Campsite().hasLocation())
    }
}
