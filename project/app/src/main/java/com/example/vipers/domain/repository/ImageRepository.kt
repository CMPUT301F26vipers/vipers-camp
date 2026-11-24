/*
 * ImageRepository.kt
 * What the app can do with uploaded images, without saying how they are stored (Repository
 * pattern). FirestoreImageRepository keeps each image as Base64 text in the Firestore "images"
 * collection; FakeImageRepository keeps them in memory for tests (US 02.01.02b).
 * Known issues: none.
 */
package com.example.vipers.domain.repository

import com.example.vipers.domain.model.ImageAsset

/** Saves, loads, lists and deletes [ImageAsset]s. */
interface ImageRepository {

    /**
     * Saves a new [image] and returns the ID it was given. Offline, the image is saved on the
     * phone first and uploaded once it reconnects, like a new campsite.
     *
     * @throws IllegalArgumentException if the image is too big ([ImageAsset.isSmallEnough]).
     */
    suspend fun addImage(image: ImageAsset): String

    /** Loads the image with this [id], or returns null if there is none (e.g. it was removed). */
    suspend fun getImage(id: String): ImageAsset?

    /** Loads every image, newest first, for the admin image browser (US 03.06.01). */
    suspend fun getAllImages(): List<ImageAsset>

    /** Deletes the image with this [id]. Does nothing if there is none. */
    suspend fun deleteImage(id: String)
}
