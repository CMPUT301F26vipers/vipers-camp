/*
 * ProfileRepository.kt
 * What the app can do with user profiles, without saying how they are stored (Repository pattern).
 * Known issues: deleting profiles (admin story) is added by the story that needs it.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile

/** Saves and loads [UserProfile]s. A profile's ID is its device ID. */
interface ProfileRepository {

    /** Loads the profile with this [id], or returns null if the device has no profile yet. */
    suspend fun getProfile(id: String): UserProfile?

    /** Creates or updates [profile], using its ID as the document ID. */
    suspend fun saveProfile(profile: UserProfile)

    /**
     * Saves [enabled] as the notification setting of the user with this [id] and changes nothing
     * else, so it can't undo a photo saved at the same time (US 01.10.01).
     */
    suspend fun setNotificationsEnabled(id: String, enabled: Boolean)

    /**
     * Saves [newImage] and makes it the profile photo of the user with this [id] (or takes the
     * photo off if it's null), and deletes the image [oldImageId] if given, all in one write:
     * either all of it happens or none of it does, so the profile never points to a missing or
     * deleted image. Changes nothing else in the profile (US 01.08.02).
     *
     * @return the new image's ID, or null if [newImage] is null.
     * @throws IllegalArgumentException if [newImage] is too big ([ImageAsset.isSmallEnough]).
     */
    suspend fun setPhoto(id: String, newImage: ImageAsset?, oldImageId: String?): String?

    /** Returns all user profiles. */
    suspend fun getAllProfiles(): List<UserProfile>

    /**
     * The current name of the user with this [id], or "Deleted user" if they have no profile.
     * Waiting lists and reservations use this instead of keeping their own copy of the name.
     */
    suspend fun getDisplayName(id: String): String = getProfile(id)?.name ?: "Deleted user"
}
