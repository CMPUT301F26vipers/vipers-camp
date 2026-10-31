/*
 * Rating.kt
 * A star rating an entrant gives a campsite after staying there.
 * Stored in the Firestore "ratings" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * A 1–5 star rating. Its ID is "campsiteId_userId", so rating again replaces the old rating.
 *
 * @property id "campsiteId_userId".
 * @property campsiteId The campsite rated.
 * @property userId Device ID of the entrant who rated it.
 * @property stars 1 to 5.
 */
data class Rating(
    val id: String = "",
    val campsiteId: String = "",
    val userId: String = "",
    val stars: Int = 5,
)
