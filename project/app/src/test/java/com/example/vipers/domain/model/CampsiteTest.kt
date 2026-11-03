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
import java.util.Locale

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

    @Test
    fun ratingText_saysNoRatingsYetWithNoRatings() {
        assertEquals("No ratings yet", Campsite(ratingSum = 0, ratingCount = 0).ratingText())
    }

    @Test
    fun ratingText_showsTheAverageWithOneDecimal() {
        // 46 stars over 10 ratings is 4.6
        assertEquals("★ 4.6", Campsite(ratingSum = 46, ratingCount = 10).ratingText())
        assertEquals("★ 5.0", Campsite(ratingSum = 5, ratingCount = 1).ratingText())
    }

    @Test
    fun ratingText_roundsToOneDecimal() {
        // 13 stars over 3 ratings is 4.333...
        assertEquals("★ 4.3", Campsite(ratingSum = 13, ratingCount = 3).ratingText())
    }

    @Test
    fun ratingText_alwaysUsesADot() {
        val before = Locale.getDefault()
        // French writes 4,5, but the card should still say 4.5
        Locale.setDefault(Locale.FRANCE)
        try {
            assertEquals("★ 4.5", Campsite(ratingSum = 9, ratingCount = 2).ratingText())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun guestsText_showsTheMaximumOccupancy() {
        assertEquals("8 guests", Campsite(maxOccupancy = 8).guestsText())
    }

    @Test
    fun guestsText_usesSingularForOneGuest() {
        assertEquals("1 guest", Campsite(maxOccupancy = 1).guestsText())
    }

    @Test
    fun matchesSearch_matchesTheStartOfTheName() {
        val campsite = Campsite(name = "Hinton Ridge Campground")
        assertTrue(campsite.matchesSearch("Hin"))
        assertTrue(campsite.matchesSearch("Hinton Ridge Campground"))
    }

    @Test
    fun matchesSearch_ignoresUpperAndLowerCase() {
        val campsite = Campsite(name = "Hinton Ridge Campground")
        assertTrue(campsite.matchesSearch("hin"))
        assertTrue(campsite.matchesSearch("HINTON RIDGE"))
    }

    @Test
    fun matchesSearch_doesNotMatchTheMiddleOfTheName() {
        // "Ridge" is in the name but the name doesn't start with it
        assertFalse(Campsite(name = "Hinton Ridge Campground").matchesSearch("Ridge"))
        assertFalse(Campsite(name = "Hinton Ridge Campground").matchesSearch("lake louise"))
    }

    @Test
    fun matchesSearch_emptyTextMatchesEveryCampsite() {
        assertTrue(Campsite(name = "Bow Valley Campground").matchesSearch(""))
        assertTrue(Campsite(name = "Bow Valley Campground").matchesSearch("   "))
    }

    @Test
    fun matchesSearch_ignoresSpacesAroundTheText() {
        assertTrue(Campsite(name = "Bow Valley Campground").matchesSearch("  bow "))
    }
}
