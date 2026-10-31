/*
 * FirestoreCampsiteRepository.kt
 * The real CampsiteRepository: saves and loads campsites in the Firestore "campsites" collection
 * (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.repository.CampsiteRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores campsites in Firestore. */
class FirestoreCampsiteRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CampsiteRepository {

    private val collection = db.collection("campsites")

    override suspend fun addCampsite(campsite: Campsite): String {
        val doc = collection.document() // Firestore makes a new random ID
        doc.set(campsite.copy(id = doc.id).toMap()).await()
        return doc.id
    }

    override suspend fun getCampsite(id: String): Campsite? {
        val snapshot = collection.document(id).get().await()
        return if (snapshot.exists()) snapshot.toCampsite() else null
    }

    override suspend fun getCampsitesByOwner(ownerId: String): List<Campsite> =
        collection
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("removed", false)
            .get().await()
            .documents.map { it.toCampsite() }

    override suspend fun getAllCampsites(): List<Campsite> =
        collection
            .whereEqualTo("removed", false)
            .get().await()
            .documents.map { it.toCampsite() }
}

/** Turns a campsite into the fields saved in its Firestore document. */
fun Campsite.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "ownerId" to ownerId,
    "name" to name,
    "address" to address,
    "region" to region.name,
    "description" to description,
    "maxOccupancy" to maxOccupancy,
    "tags" to tags,
    "pricePerNightCents" to pricePerNightCents,
    "rules" to rules,
    "cancellationPolicy" to cancellationPolicy,
    "imageId" to imageId,
    "lat" to lat,
    "lng" to lng,
    "ratingSum" to ratingSum,
    "ratingCount" to ratingCount,
    "removed" to removed,
)

/** Builds a campsite from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toCampsite(): Campsite = Campsite(
    id = id,
    ownerId = getString("ownerId") ?: "",
    name = getString("name") ?: "",
    address = getString("address") ?: "",
    region = getString("region")?.let { Region.valueOf(it) } ?: Region.BANFF,
    description = getString("description") ?: "",
    maxOccupancy = getInt("maxOccupancy") ?: 1,
    tags = getStringList("tags"),
    pricePerNightCents = getLong("pricePerNightCents") ?: 0,
    rules = getString("rules") ?: "",
    cancellationPolicy = getString("cancellationPolicy") ?: "",
    imageId = getString("imageId"),
    lat = getDouble("lat"),
    lng = getDouble("lng"),
    ratingSum = getInt("ratingSum") ?: 0,
    ratingCount = getInt("ratingCount") ?: 0,
    removed = getBoolean("removed") ?: false,
)
