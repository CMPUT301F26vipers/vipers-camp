/*
 * FirestoreProfileRepository.kt
 * The real ProfileRepository: saves and loads profiles in the Firestore "users" collection
 * (Repository pattern). The document ID is the device ID.
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties,
 * and so "isAdmin" keeps that exact name (set by hand in the Firebase console).
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores profiles in Firestore. */
class FirestoreProfileRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ProfileRepository {

    private val collection = db.collection("users")

    override suspend fun getProfile(id: String): UserProfile? {
        val snapshot = collection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toUserProfile() else null
    }

    override suspend fun saveProfile(profile: UserProfile) {
        collection.document(profile.id).set(profile.toMap()).await()
    }
}

/** Turns a profile into the fields saved in its Firestore document. */
fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "email" to email,
    "phone" to phone,
    "imageId" to imageId,
    "notificationsEnabled" to notificationsEnabled,
    "isAdmin" to isAdmin,
    "createdAt" to createdAt,
)

/** Builds a profile from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    id = id,
    name = getString("name") ?: "",
    email = getString("email") ?: "",
    phone = getString("phone") ?: "",
    imageId = getString("imageId"),
    notificationsEnabled = getBoolean("notificationsEnabled") ?: true,
    isAdmin = getBoolean("isAdmin") ?: false,
    createdAt = getLong("createdAt") ?: 0,
)
