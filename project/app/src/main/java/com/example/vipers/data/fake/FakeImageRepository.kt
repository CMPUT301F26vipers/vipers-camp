/*
 * FakeImageRepository.kt
 * An in-memory ImageRepository for unit and intent tests (a test double for the Repository
 * pattern). Nothing is saved to Firestore (US 02.01.02b).
 * Known issues: new IDs come from one counter shared by every fake and never reset, so tests
 * shouldn't expect a particular ID. Lives in main (not test) so both unit tests and intent tests
 * can use it.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.repository.ImageRepository
import java.io.IOException

/**
 * Keeps images in a map instead of Firestore. Pass [initial] to start with some images. While
 * [failAdds] is true, [addImage] throws an [IOException], like an upload that fails.
 */
class FakeImageRepository(
    initial: List<ImageAsset> = emptyList(),
    var failAdds: Boolean = false,
) : ImageRepository {

    // Images by ID, like documents in a Firestore collection.
    private val images = initial.associateBy { it.id }.toMutableMap()

    /** IDs passed to [deleteImage] so far, oldest first. */
    val deletedIds = mutableListOf<String>()

    override suspend fun addImage(image: ImageAsset): String {
        require(image.isSmallEnough()) { "Image is too big to save" }
        if (failAdds) throw IOException("Upload failed")
        val id = image.id.ifEmpty { "image-${nextId++}" }
        images[id] = image.copy(id = id)
        return id
    }

    override suspend fun getImage(id: String): ImageAsset? = images[id]

    override suspend fun getAllImages(): List<ImageAsset> =
        images.values.sortedByDescending { it.createdAt }

    override suspend fun deleteImage(id: String) {
        deletedIds.add(id)
        images.remove(id)
    }

    companion object {
        // Shared by every fake, like Firestore's random IDs, so two tests never save different
        // photos under the same ID (the photo cache in Photo.kt would show the old one).
        private var nextId = 1
    }
}
