/*
 * Campsite.kt
 * A campsite an owner lists so entrants can find it and join its lotteries.
 * Stored in the Firestore "campsites" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A campsite listed by an owner.
 *
 * Every field has a default so Firestore can rebuild the object and tests can set only what they need.
 *
 * @property id Firestore document ID.
 * @property ownerId Device ID of the user who listed it.
 * @property name Campsite name shown in search and on cards.
 * @property address Free-text location, e.g. "Hinton, AB".
 * @property region Region from the fixed list, used by the search filter.
 * @property description Description and amenities, e.g. "outdoor cookhouse".
 * @property maxOccupancy Most guests allowed in one group; at least 1.
 * @property tags Short labels such as "Cookhouse" or "Tennis court".
 * @property pricePerNightCents Nightly price in cents (5000 = $50.00).
 * @property rules Campsite rules, e.g. "Quiet hours after 10 PM".
 * @property cancellationPolicy E.g. "Free up to 7 days before".
 * @property imageId ID of the promo image, or null if there is none.
 * @property lat Latitude from Find location, or null if not set.
 * @property lng Longitude from Find location, or null if not set.
 * @property ratingSum Total of all star ratings, used to work out the average.
 * @property ratingCount Number of ratings received.
 * @property removed True once an admin removes it; removed campsites are hidden.
 */
data class Campsite(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val address: String = "",
    val region: Region = Region.BANFF,
    val description: String = "",
    val maxOccupancy: Int = 1,
    val tags: List<String> = emptyList(),
    val pricePerNightCents: Long = 0,
    val rules: String = "",
    val cancellationPolicy: String = "",
    val imageId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val ratingSum: Int = 0,
    val ratingCount: Int = 0,
    val removed: Boolean = false,
) {
    /** True if the owner has written rules; an empty or blank [rules] means none (T-11). */
    val hasRules: Boolean
        get() = rules.isNotBlank()

    /**
     * True if the owner has written a cancellation policy; an empty or blank [cancellationPolicy]
     * means none (T-11).
     */
    val hasCancellationPolicy: Boolean
        get() = cancellationPolicy.isNotBlank()

    /** The average star rating, or 0.0 if nobody has rated it yet. */
    fun averageRating(): Double {
        // avoid division by zero
        if (ratingCount == 0) return 0.0
        return ratingSum.toDouble() / ratingCount
    }

    /**
     * Calculates the entrant's match percentage after the hard filters pass (W-01).
     *
     * @param freeNights Number of available nights within the requested dates.
     * @param requestedNights Total nights including both endpoints, or zero for any dates.
     * @param preferredTags Optional amenities. A missing preference earns full credit.
     * @return A whole percentage: 50 points for availability, 30 for tags and 20 for rating.
     */
    fun matchScore(freeNights: Int, requestedNights: Int, preferredTags: Set<String>): Int {
        val availability = if (requestedNights == 0) 1.0 else freeNights.toDouble() / requestedNights
        val wanted = preferredTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val offered = tags.map { it.trim().lowercase() }.toSet()
        val amenities = if (wanted.isEmpty()) 1.0 else
            wanted.count { it in offered }.toDouble() / wanted.size
        val stars = averageRating().coerceIn(0.0, 5.0)
        return (50 * availability.coerceIn(0.0, 1.0) + 30 * amenities + 4 * stars).roundToInt()
    }

    /** The nightly price in dollars, as the campsite form shows it: "50" or "65.50" (T-09). */
    fun priceInDollars(): String {
        return dollarsText(pricePerNightCents)
    }

    /** The nightly price as text for the UI, e.g. "$50 / night" or "$65.50 / night". */
    fun priceText(): String {
        return "$${priceInDollars()} / night"
    }

    /**
     * The total for staying [nights] nights: the nightly price times [nights] (T-09). A reservation
     * stores this total when it's made, so changing the price later doesn't change it.
     */
    fun totalPriceCents(nights: Int): Long {
        return pricePerNightCents * nights
    }

    /** The total for staying [nights] nights as text for the UI, e.g. "$100" (US 01.04.01). */
    fun totalPriceText(nights: Int): String {
        return "$${dollarsText(totalPriceCents(nights))}"
    }

    /**
     * The link stored in this campsite's QR code (US 02.02.01), e.g. "vipers-camp://campsite/abc".
     * It only uses [id], so the code stays the same when the owner edits the campsite.
     */
    fun qrLink(): String {
        return QR_LINK_PREFIX + id
    }

    companion object {
        /** The start of every campsite QR link; the campsite ID follows it. */
        const val QR_LINK_PREFIX = "vipers-camp://campsite/"

        /**
         * The campsite ID in a scanned QR [link] made by [qrLink], or null if it isn't one of our
         * campsite links (another app's QR code, an empty ID, or extra parts after the ID).
         * Scanning (US 01.12.01) uses this. Campsite IDs only use letters, digits, - and _, so
         * anything else (like ?, # or %) means it isn't ours, and it can't break the screen route.
         */
        fun idFromQrLink(link: String): String? {
            val text = link.trim()
            if (!text.startsWith(QR_LINK_PREFIX)) return null
            val id = text.removePrefix(QR_LINK_PREFIX)
            if (!QR_ID_PATTERN.matches(id)) return null
            return id
        }

        // Firestore IDs are letters and digits; our test and seed IDs also use - and _
        private val QR_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

        /** An amount in cents as dollars: "50" or "65.50". Cents only show when there are some. */
        fun dollarsText(amountCents: Long): String {
            val dollars = amountCents / 100
            val cents = amountCents % 100
            return if (cents == 0L) "$dollars" else "$dollars.%02d".format(Locale.US, cents)
        }
    }

    /**
     * The average rating as text for the UI, e.g. "★ 4.5", or "No ratings yet" if nobody has
     * rated it (US 01.01.04).
     */
    fun ratingText(): String {
        if (ratingCount == 0) return "No ratings yet"
        // always a dot, even on phones set to a language that writes 4,5
        return "★ %.1f".format(Locale.US, averageRating())
    }

    /** The maximum occupancy as text for the UI, e.g. "6 guests" or "1 guest". */
    fun guestsText(): String {
        return if (maxOccupancy == 1) "1 guest" else "$maxOccupancy guests"
    }

    /**
     * The one-line summary under a campsite's name, e.g. "Hinton, AB · Up to 6 guests · $50 / night"
     * (mockups E05 and O04). The price is left out while it's 0, which campsites added before the
     * form asked for a price (T-09) still have.
     */
    fun summaryText(): String {
        val parts = mutableListOf(address, "Up to ${guestsText()}")
        if (pricePerNightCents > 0) parts.add(priceText())
        return parts.joinToString(" · ")
    }

    /** True if the campsite has map coordinates (both [lat] and [lng] are set). */
    fun hasLocation(): Boolean {
        return lat != null && lng != null
    }

    /**
     * The map coordinates as the owner sees them, e.g. "53.405, -117.585", or null if the
     * campsite has no map location (US 02.01.02c).
     */
    fun coordinatesText(): String? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        return Place.coordinatesText(latitude, longitude)
    }

    /**
     * True if [name] starts with [searchText], ignoring upper/lower case and spaces around the
     * text. An empty [searchText] matches every campsite (US 01.01.01).
     */
    fun matchesSearch(searchText: String): Boolean {
        return name.startsWith(searchText.trim(), ignoreCase = true)
    }

    /**
     * True if this campsite is in [filterRegion], or true if [filterRegion] is null, so it matches
     * any region (US 01.01.02).
     */
    fun matchesRegion(filterRegion: Region?): Boolean {
        return filterRegion == null || region == filterRegion
    }

    /**
     * True if this campsite's maximum occupancy is at least [minGuests], or true if [minGuests]
     * is null or not positive (US 01.01.02).
     */
    fun matchesGuests(minGuests: Int?): Boolean {
        return minGuests == null || minGuests <= 0 || maxOccupancy >= minGuests
    }

    /**
     * True if [userId] is this campsite's owner, so they may edit it (US 02.01.03). An empty
     * [userId] is never the owner, even on a campsite with no [ownerId] set.
     */
    fun isOwnedBy(userId: String): Boolean {
        return userId.isNotEmpty() && ownerId == userId
    }

    /**
     * Checks the fields the Add Campsite form requires (US 02.01.02a) and returns an error
     * message for each field that's wrong, keyed by field name. An empty map means it's valid.
     * The map location is optional, but if it's set, [lat] and [lng] must both be set and be real
     * coordinates (US 02.01.02c).
     */
    fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = "Enter a name"
        if (address.isBlank()) errors["address"] = "Enter a location"
        if (maxOccupancy < 1) errors["maxOccupancy"] = "Must be at least 1"
        if (!hasValidCoordinates()) errors["mapLocation"] = "Find the location again"
        return errors
    }

    // No coordinates at all is fine; otherwise both must be set and in range.
    private fun hasValidCoordinates(): Boolean {
        if (lat == null && lng == null) return true
        val latitude = lat ?: return false
        val longitude = lng ?: return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
