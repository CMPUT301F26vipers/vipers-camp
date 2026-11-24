/*
 * FakeProfileRepository.kt
 * An in-memory ProfileRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: setPhoto adds the new image, changes the profile and deletes the old image one
 * after the other, not in one write like Firestore's batch. It adds the image first, so a failed
 * upload (FakeImageRepository.failAdds) still changes nothing, like the real one.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.ProfileRepository

/**
 * Keeps profiles in a map instead of Firestore. Pass [initial] to start with some profiles, and
 * [images] for setPhoto to save and delete profile photos in, like the real one does.
 */
class FakeProfileRepository(
    initial: List<UserProfile> = emptyList(),
    private val images: ImageRepository? = null,
) : ProfileRepository {

    private val profiles = initial.associateBy { it.id }.toMutableMap()

    override suspend fun getProfile(id: String): UserProfile? = profiles[id]

    override suspend fun getAllProfiles(): List<UserProfile> = profiles.values.toList()

    override suspend fun saveProfile(profile: UserProfile) {
        profiles[profile.id] = profile
    }

    override suspend fun setNotificationsEnabled(id: String, enabled: Boolean) {
        profiles[id] = profileFor(id).copy(notificationsEnabled = enabled)
    }

    override suspend fun setPhoto(id: String, newImage: ImageAsset?, oldImageId: String?): String? {
        val profile = profileFor(id)
        val photos = images ?: throw IllegalStateException("Give the fake an ImageRepository")
        val newId = newImage?.let { photos.addImage(it) }
        profiles[id] = profile.copy(imageId = newId)
        if (oldImageId != null) photos.deleteImage(oldImageId)
        return newId
    }

    // Firestore drops an update to a profile that doesn't exist; the fake throws so tests notice
    private fun profileFor(id: String): UserProfile =
        profiles[id] ?: throw NoSuchElementException("No profile with ID $id")
}
