/*
 * FakeProfileRepository.kt
 * An in-memory ProfileRepository for unit and intent tests (a test double for the Repository pattern).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository

/** Keeps profiles in a map instead of Firestore. Pass [initial] to start with some profiles. */
class FakeProfileRepository(initial: List<UserProfile> = emptyList()) : ProfileRepository {

    private val profiles = initial.associateBy { it.id }.toMutableMap()

    override suspend fun getProfile(id: String): UserProfile? = profiles[id]

    override suspend fun saveProfile(profile: UserProfile) {
        profiles[profile.id] = profile
    }
}
