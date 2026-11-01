/*
 * UserProfile.kt
 * The profile of one user. There are no logins, so the device ID is the user's ID.
 * Stored in the Firestore "users" collection.
 * Known issues: Firestore's automatic mapping saves a Kotlin property named "isAdmin" as "admin",
 * so FirestoreProfileRepository reads and writes that field by hand.
 */
package com.example.vipers.domain.model

/**
 * A user of the app. Anyone can be both an entrant and a campsite owner.
 *
 * @property id Device ID; also the Firestore document ID.
 * @property name Display name; required.
 * @property email Optional email, empty if not given.
 * @property phone Optional phone number, empty if not given.
 * @property imageId ID of the profile photo, or null if there is none.
 * @property notificationsEnabled Whether the user gets notifications; on by default.
 * @property isAdmin True for admins; set by hand in the Firebase console.
 * @property createdAt When the profile was created, in milliseconds since 1970.
 */
data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val imageId: String? = null,
    val notificationsEnabled: Boolean = true,
    val isAdmin: Boolean = false,
    val createdAt: Long = 0,
)
