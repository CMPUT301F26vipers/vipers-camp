/*
 * ImageMappingTest.kt
 * Unit tests for ImageAsset.toMap(), the fields saved in an image's Firestore document
 * (US 02.01.02b).
 * Known issues: reading a document back (toImageAsset) needs a real DocumentSnapshot, so it's
 * covered by running the app against Firestore, not here.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.ImageAsset
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMappingTest {

    private val photo = ImageAsset(
        id = "image-1",
        data = "/9j/4AAQSkZJRg==",
        uploaderId = "priya-phone",
        ownerType = ImageAsset.OWNER_CAMPSITE,
        ownerId = "hinton",
        label = "Hinton Ridge Campground",
        createdAt = 1_809_000_000_000,
    )

    @Test
    fun toMap_keepsEveryField() {
        val map = photo.toMap()

        assertEquals(
            setOf("id", "data", "uploaderId", "ownerType", "ownerId", "label", "createdAt"),
            map.keys,
        )
        assertEquals("/9j/4AAQSkZJRg==", map["data"])
        assertEquals("priya-phone", map["uploaderId"])
        assertEquals("campsite", map["ownerType"])
        assertEquals("hinton", map["ownerId"])
        assertEquals("Hinton Ridge Campground", map["label"])
        assertEquals(1_809_000_000_000, map["createdAt"])
    }
}
