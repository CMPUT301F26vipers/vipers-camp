/*
 * Campsite.kt
 * A campsite an owner lists so entrants can find it and join its lotteries.
 * Stored in the Firestore "campsites" collection.
 * Known issues: price, rules, cancellation policy, photo and map location are stored but not
 * yet filled in by the Add Campsite form.
 */
package com.example.vipers.domain.model

import java.util.Locale

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
    /** The average star rating, or 0.0 if nobody has rated it yet. */
    fun averageRating(): Double {
        // avoid division by zero
        if (ratingCount == 0) return 0.0
        return ratingSum.toDouble() / ratingCount
    }

    /** The nightly price as text for the UI, e.g. "$50 / night" or "$65.50 / night". */
    fun priceText(): String {
        val dollars = pricePerNightCents / 100
        val cents = pricePerNightCents % 100
        // only show cents when there are some
        return if (cents == 0L) "$$dollars / night" else "$$dollars.%02d / night".format(cents)
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
     * (mockups E05 and O04). The price is left out while it's 0, which campsites have until the
     * form asks for one (T-09).
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
     * True if [name] starts with [searchText], ignoring upper/lower case and spaces around the
     * text. An empty [searchText] matches every campsite (US 01.01.01).
     */
    fun matchesSearch(searchText: String): Boolean {
        return name.startsWith(searchText.trim(), ignoreCase = true)
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
     */
    fun validate(): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = "Enter a name"
        if (address.isBlank()) errors["address"] = "Enter a location"
        if (maxOccupancy < 1) errors["maxOccupancy"] = "Must be at least 1"
        return errors
    }
}
