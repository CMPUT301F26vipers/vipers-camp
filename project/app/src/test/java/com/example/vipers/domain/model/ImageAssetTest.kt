/*
 * ImageAssetTest.kt
 * Unit tests for ImageAsset (US 02.01.02b): turning JPEG bytes into Base64 text and back, and the
 * size check that keeps an image inside one Firestore document.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAssetTest {

    private val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70) // a JPEG's first bytes

    private fun photoOf(bytes: ByteArray) = ImageAsset.fromJpeg(
        bytes,
        uploaderId = "priya-phone",
        ownerType = ImageAsset.OWNER_CAMPSITE,
        ownerId = "hinton",
        label = "Hinton Ridge Campground",
        createdAt = 1_809_000_000_000,
    )

    @Test
    fun fromJpeg_jpegBytes_givesBackTheSameBytes() {
        assertArrayEquals(jpeg, photoOf(jpeg).jpegBytes())
    }

    @Test
    fun fromJpeg_keepsWhoAndWhatItBelongsTo() {
        val photo = photoOf(jpeg)

        assertEquals("priya-phone", photo.uploaderId)
        assertEquals("campsite", photo.ownerType)
        assertEquals("hinton", photo.ownerId)
        assertEquals("Hinton Ridge Campground", photo.label)
        assertEquals(1_809_000_000_000, photo.createdAt)
    }

    @Test
    fun fromJpeg_savesTheBytesAsBase64Text() {
        assertEquals("/9j/4AAQSkZJRg==", photoOf(jpeg).data)
    }

    @Test
    fun isSmallEnough_biggestAllowedJpeg_isTrue() {
        // MAX_JPEG_BYTES is the biggest photo the form saves, so it must fit
        assertTrue(photoOf(ByteArray(ImageAsset.MAX_JPEG_BYTES)).isSmallEnough())
    }

    @Test
    fun isSmallEnough_atTheLimit_isTrue() {
        assertTrue(ImageAsset(data = "a".repeat(ImageAsset.MAX_DATA_LENGTH)).isSmallEnough())
    }

    @Test
    fun isSmallEnough_overTheLimit_isFalse() {
        assertFalse(ImageAsset(data = "a".repeat(ImageAsset.MAX_DATA_LENGTH + 1)).isSmallEnough())
    }
}
