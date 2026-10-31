/*
 * ProfileRepository.kt
 * What the app can do with user profiles, without saying how they are stored (Repository pattern).
 * Known issues: only the methods needed for the half-way checkpoint. Listing and deleting
 * profiles (admin stories) are added by the stories that need them.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.UserProfile

/** Saves and loads [UserProfile]s. A profile's ID is its device ID. */
interface ProfileRepository {

    /** Loads the profile with this [id], or returns null if the device has no profile yet. */
    suspend fun getProfile(id: String): UserProfile?

    /** Creates or updates [profile], using its ID as the document ID. */
    suspend fun saveProfile(profile: UserProfile)
}
