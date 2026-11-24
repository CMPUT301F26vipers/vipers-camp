/*
 * FirestoreImageRepository.kt
 * The real ImageRepository: saves each image as Base64 text in a document of the Firestore "images"
 * collection (Repository pattern). This way photos need no file storage and no extra library
 * (US 02.01.02b).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 * Listing every image downloads every image, which is fine for the admin browser at our size.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.repository.ImageRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Stores images in Firestore. */
class FirestoreImageRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ImageRepository {

    private val collection = db.collection("images")

    override suspend fun addImage(image: ImageAsset): String {
        require(image.isSmallEnough()) { "Image is too big to save" }
        val doc = collection.document() // Firestore makes a new random ID
        // Not awaited, like addCampsite: offline, set() only finishes once the phone reconnects,
        // so Publish would hang. Firestore keeps the write on the phone and sends it later.
        doc.set(image.copy(id = doc.id).toMap())
        return doc.id
    }

    override suspend fun getImage(id: String): ImageAsset? {
        val snapshot = collection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toImageAsset() else null
    }

    override suspend fun getAllImages(): List<ImageAsset> =
        collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
            .documents.map { it.toImageAsset() }

    override suspend fun deleteImage(id: String) {
        // Not awaited, for the same reason as addImage.
        collection.document(id).delete()
    }
}

/** Turns an image into the fields saved in its Firestore document. */
fun ImageAsset.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "data" to data,
    "uploaderId" to uploaderId,
    "ownerType" to ownerType,
    "ownerId" to ownerId,
    "label" to label,
    "createdAt" to createdAt,
)

/** Builds an image from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toImageAsset(): ImageAsset = ImageAsset(
    id = id,
    data = getString("data") ?: "",
    uploaderId = getString("uploaderId") ?: "",
    ownerType = getString("ownerType") ?: ImageAsset.OWNER_CAMPSITE,
    ownerId = getString("ownerId") ?: "",
    label = getString("label") ?: "",
    createdAt = getLong("createdAt") ?: 0,
)
