/*
 * CampsiteTest.kt
 * Unit tests for the Campsite model's methods, including the QR code link (US 02.02.01).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun priceInDollars_showsCentsOnlyWhenThereAreSome() {
        assertEquals("50", Campsite(pricePerNightCents = 5000).priceInDollars())
        assertEquals("65.50", Campsite(pricePerNightCents = 6550).priceInDollars())
        assertEquals("0", Campsite(pricePerNightCents = 0).priceInDollars())
    }

    @Test
    fun totalPriceCents_isThePriceTimesTheNights() {
        // 2 nights at $65 is $130
        assertEquals(13000L, Campsite(pricePerNightCents = 6500).totalPriceCents(2))
        assertEquals(0L, Campsite(pricePerNightCents = 0).totalPriceCents(3))
    }

    @Test
    fun totalPriceText_showsTheTotalInDollars() {
        // 2 nights at $50 is $100, like mockup E11
        assertEquals("$100", Campsite(pricePerNightCents = 5000).totalPriceText(2))
        // 3 nights at $65.50 is $196.50
        assertEquals("$196.50", Campsite(pricePerNightCents = 6550).totalPriceText(3))
        assertEquals("$0", Campsite(pricePerNightCents = 5000).totalPriceText(0))
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
    fun guestsText_showsTheMaximumOccupancy() {
        assertEquals("8 guests", Campsite(maxOccupancy = 8).guestsText())
    }

    @Test
    fun guestsText_usesSingularForOneGuest() {
        assertEquals("1 guest", Campsite(maxOccupancy = 1).guestsText())
    }

    @Test
    fun summaryText_showsLocationGuestsAndPrice() {
        val campsite = Campsite(address = "Hinton, AB", maxOccupancy = 8, pricePerNightCents = 6500)
        assertEquals("Hinton, AB · Up to 8 guests · $65 / night", campsite.summaryText())
    }

    @Test
    fun summaryText_leavesOutAPriceOfZero() {
        val campsite = Campsite(address = "Banff, AB", maxOccupancy = 1, pricePerNightCents = 0)
        assertEquals("Banff, AB · Up to 1 guest", campsite.summaryText())
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

    @Test
    fun isOwnedBy_ownerId_isTrue() {
        assertTrue(Campsite(ownerId = "priya-phone").isOwnedBy("priya-phone"))
    }

    @Test
    fun isOwnedBy_anotherId_isFalse() {
        assertFalse(Campsite(ownerId = "priya-phone").isOwnedBy("sam-phone"))
    }

    @Test
    fun isOwnedBy_emptyId_isFalse() {
        assertFalse(Campsite(ownerId = "priya-phone").isOwnedBy(""))
        // a campsite with no owner set doesn't belong to an empty ID either
        assertFalse(Campsite(ownerId = "").isOwnedBy(""))
    }

    @Test
    fun validate_validCampsiteHasNoErrors() {
        val campsite = Campsite(name = "Hinton Ridge Campground", address = "Hinton, AB", maxOccupancy = 6)
        assertTrue(campsite.validate().isEmpty())
    }

    @Test
    fun validate_blankNameAndAddressAreErrors() {
        val errors = Campsite(name = "  ", address = "", maxOccupancy = 6).validate()
        assertEquals("Enter a name", errors["name"])
        assertEquals("Enter a location", errors["address"])
    }

    @Test
    fun validate_maxOccupancyMustBeAtLeastOne() {
        val campsite = Campsite(name = "Hinton Ridge Campground", address = "Hinton, AB", maxOccupancy = 0)
        assertEquals(mapOf("maxOccupancy" to "Must be at least 1"), campsite.validate())
    }

    @Test

    fun hasRules_emptyOrBlank_isFalse() {
        assertFalse(Campsite(rules = "").hasRules)
        assertFalse(Campsite(rules = "   ").hasRules)
    }

    @Test
    fun hasRules_withText_isTrue() {
        assertTrue(Campsite(rules = "Quiet hours after 10 PM").hasRules)
    }

    @Test
    fun hasCancellationPolicy_emptyOrBlank_isFalse() {
        assertFalse(Campsite(cancellationPolicy = "").hasCancellationPolicy)
        assertFalse(Campsite(cancellationPolicy = "   ").hasCancellationPolicy)
    }

    @Test
    fun hasCancellationPolicy_withText_isTrue() {
        assertTrue(Campsite(cancellationPolicy = "Free up to 7 days before").hasCancellationPolicy)
    }

    @Test
    fun matchesRegion_matchesWhenSameRegion() {
        val campsite = Campsite(region = Region.JASPER)
        assertTrue(campsite.matchesRegion(Region.JASPER))
    }

    @Test
    fun matchesRegion_doesNotMatchWhenDifferentRegion() {
        val campsite = Campsite(region = Region.JASPER)
        assertFalse(campsite.matchesRegion(Region.BANFF))
    }

    @Test
    fun matchesRegion_nullMatchesAnyRegion() {
        assertTrue(Campsite(region = Region.HINTON).matchesRegion(null))
        assertTrue(Campsite(region = Region.KANANASKIS).matchesRegion(null))
    }

    @Test
    fun matchesGuests_matchesWhenOccupancyIsEqualOrGreater() {
        val campsite = Campsite(maxOccupancy = 6)
        assertTrue(campsite.matchesGuests(6))
        assertTrue(campsite.matchesGuests(4))
    }

    @Test
    fun matchesGuests_doesNotMatchWhenOccupancyIsBelowFilter() {
        val campsite = Campsite(maxOccupancy = 4)
        assertFalse(campsite.matchesGuests(6))
    }

    @Test
    fun matchesGuests_nullOrZeroOrNegativeMatchesAnyCampsite() {
        val campsite = Campsite(maxOccupancy = 2)
        assertTrue(campsite.matchesGuests(null))
        assertTrue(campsite.matchesGuests(0))
        assertTrue(campsite.matchesGuests(-1))
    }

    // the Hinton campsite from scenario 2, with the map location Find location gives for it
    private val mappedHinton = Campsite(
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        maxOccupancy = 8,
        lat = 53.405,
        lng = -117.585,
    )

    @Test
    fun coordinatesText_showsThreeDecimals() {
        assertEquals("53.405, -117.585", mappedHinton.coordinatesText())
    }

    @Test
    fun coordinatesText_noMapLocation_isNull() {
        assertEquals(null, mappedHinton.copy(lat = null, lng = null).coordinatesText())
        assertEquals(null, mappedHinton.copy(lng = null).coordinatesText())
    }

    @Test
    fun validate_mapLocationIsOptional() {
        assertTrue(mappedHinton.copy(lat = null, lng = null).validate().isEmpty())
        assertTrue(mappedHinton.validate().isEmpty())
    }

    @Test
    fun validate_onlyOneCoordinateIsAnError() {
        assertEquals(setOf("mapLocation"), mappedHinton.copy(lng = null).validate().keys)
        assertEquals(setOf("mapLocation"), mappedHinton.copy(lat = null).validate().keys)
    }

    @Test
    fun validate_coordinatesOutOfRangeAreAnError() {
        assertEquals(setOf("mapLocation"), mappedHinton.copy(lat = 90.5).validate().keys)
        assertEquals(setOf("mapLocation"), mappedHinton.copy(lng = -180.5).validate().keys)
        // the edges are real coordinates
        assertTrue(mappedHinton.copy(lat = -90.0, lng = 180.0).validate().isEmpty())
    }

    @Test
    fun qrLink_holdsTheCampsiteId() {
        assertEquals("vipers-camp://campsite/hinton", Campsite(id = "hinton").qrLink())
    }

    @Test
    fun qrLink_isDifferentForEachCampsite() {
        assertNotEquals(Campsite(id = "hinton").qrLink(), Campsite(id = "jasper").qrLink())
    }

    @Test
    fun qrLink_staysTheSameAfterTheCampsiteIsEdited() {
        val before = Campsite(id = "hinton", name = "Hinton Ridge", pricePerNightCents = 5000)
        val after = before.copy(name = "Hinton Ridge Campground", pricePerNightCents = 6500)

        assertEquals(before.qrLink(), after.qrLink())
    }

    @Test
    fun idFromQrLink_readsBackTheIdFromQrLink() {
        val campsite = Campsite(id = "aB3xK9pQ")

        assertEquals("aB3xK9pQ", Campsite.idFromQrLink(campsite.qrLink()))
    }

    @Test
    fun idFromQrLink_ignoresSpacesAroundTheLink() {
        assertEquals("hinton", Campsite.idFromQrLink("  vipers-camp://campsite/hinton\n"))
    }

    @Test
    fun idFromQrLink_withAnotherAppsCode_isNull() {
        assertNull(Campsite.idFromQrLink("https://example.com/campsite/hinton"))
        assertNull(Campsite.idFromQrLink("Hinton Ridge Campground"))
        assertNull(Campsite.idFromQrLink(""))
    }

    @Test
    fun idFromQrLink_withNoId_isNull() {
        assertNull(Campsite.idFromQrLink("vipers-camp://campsite/"))
        assertNull(Campsite.idFromQrLink("vipers-camp://campsite/   "))
    }

    @Test
    fun idFromQrLink_withExtraPartsAfterTheId_isNull() {
        assertNull(Campsite.idFromQrLink("vipers-camp://campsite/hinton/edit"))
        assertNull(Campsite.idFromQrLink("vipers-camp://campsite/hin ton"))
    }
}
