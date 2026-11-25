/*
 * FirestoreProfileRepository.kt
 * The real ProfileRepository: saves and loads profiles in the Firestore "users" collection
 * (Repository pattern). The document ID is the device ID. setPhoto also saves the new photo and
 * deletes the old one in the "images" collection, in the same batch (US 01.08.02).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties,
 * and so "isAdmin" keeps that exact name (set by hand in the Firebase console).
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores profiles in Firestore. */
class FirestoreProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ProfileRepository {

    private val collection = db.collection("users")

    override suspend fun getProfile(id: String): UserProfile? {
        val snapshot = collection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toUserProfile() else null
    }

    override suspend fun getAllProfiles(): List<UserProfile> =
        collection.get().await().documents.map { it.toUserProfile() }

    override suspend fun saveProfile(profile: UserProfile) {
        // Not awaited, same as addCampsite: offline, set() only finishes once the phone
        // reconnects. Firestore keeps the write on the phone and sends it later.
        collection.document(profile.id).set(profile.toMap())
    }

    override suspend fun setNotificationsEnabled(id: String, enabled: Boolean) {
        // update() only changes this field. Not awaited, for the same reason as saveProfile.
        collection.document(id).update("notificationsEnabled", enabled)
    }

    override suspend fun setPhoto(
        id: String,
        newImage: ImageAsset?,
        oldImageId: String?,
    ): String? {
        require(newImage == null || newImage.isSmallEnough()) { "Image is too big to save" }
        val images = db.collection("images")
        // One batch, so if Firestore rejects any part (e.g. the new image) nothing changes and
        // the old image isn't deleted. Offline, the batch is kept on the phone like any write,
        // so it isn't awaited.
        val batch = db.batch()
        val newId = newImage?.let {
            val doc = images.document() // Firestore makes a new random ID
            batch.set(doc, it.copy(id = doc.id).toMap())
            doc.id
        }
        batch.update(collection.document(id), "imageId", newId)
        if (oldImageId != null) batch.delete(images.document(oldImageId))
        batch.commit()
        return newId
    }

    override suspend fun deleteProfile(id: String, imageId: String?) {
        // One batch, so the photo isn't left behind. Not awaited, like setPhoto.
        val batch = db.batch()
        batch.delete(collection.document(id))
        if (imageId != null) batch.delete(db.collection("images").document(imageId))
        batch.commit()
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
