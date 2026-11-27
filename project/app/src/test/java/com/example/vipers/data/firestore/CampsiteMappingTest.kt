/*
 * CampsiteMappingTest.kt
 * Unit tests for Campsite.toMap(), the fields saved in a campsite's Firestore document
 * (US 02.01.02a: the details the owner enters are saved correctly).
 * Known issues: reading a document back (toCampsite) needs a real DocumentSnapshot, so it's
 * covered by running the app against Firestore, not here.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteMappingTest {

    private val campsite = Campsite(
        id = "c1",
        ownerId = "my-phone",
        name = "Hinton Ridge Campground",
        address = "Hinton, AB",
        region = Region.HINTON,
        description = "Outdoor cookhouse",
        maxOccupancy = 6,
        tags = listOf("Cookhouse", "Tennis court"),
    )

    @Test
    fun toMap_keepsEveryDetailTheOwnerEntered() {
        val map = campsite.toMap()
        assertEquals("my-phone", map["ownerId"])
        assertEquals("Hinton Ridge Campground", map["name"])
        assertEquals("Hinton, AB", map["address"])
        assertEquals("Outdoor cookhouse", map["description"])
        assertEquals(6, map["maxOccupancy"])
        assertEquals(listOf("Cookhouse", "Tennis court"), map["tags"])
    }

    @Test
    fun toMap_savesTheRegionByItsName() {
        // stored as "HINTON" so toCampsite can turn it back with Region.valueOf
        assertEquals("HINTON", campsite.toMap()["region"])
    }

    @Test
    fun toEditableFields_always_holdsOnlyTheFieldsAnOwnerEdits() {
        // updateCampsite writes exactly these, so nothing else can be overwritten (US 02.01.03);
        // lat and lng are the map location from Find location (US 02.01.02c), imageId the photo
        // (US 02.01.02b)
        assertEquals(
            setOf(
                "name", "address", "region", "description", "maxOccupancy", "tags",
                "pricePerNightCents", "rules", "cancellationPolicy", "lat", "lng", "imageId",
            ),
            campsite.toEditableFields().keys,
        )
    }

    @Test
    fun toEditableFields_region_isSavedByItsName() {
        assertEquals("HINTON", campsite.toEditableFields()["region"])
    }

    @Test
    fun toMap_newCampsiteIsNotRemoved() {
        assertEquals(false, campsite.toMap()["removed"])
    }

    @Test
    fun toEditableFields_mapLocation_isSaved() {
        val fields = campsite.copy(lat = 53.405, lng = -117.585).toEditableFields()

        assertEquals(53.405, fields["lat"])
        assertEquals(-117.585, fields["lng"])
    }

    @Test
    fun toEditableFields_noMapLocation_clearsIt() {
        // null, so an edit that removes the location also removes it from Firestore
        val fields = campsite.toEditableFields()

        assertTrue(fields.containsKey("lat") && fields["lat"] == null)
        assertTrue(fields.containsKey("lng") && fields["lng"] == null)
    }

    @Test
    fun toEditableFields_photo_isSaved() {
        assertEquals("photo-1", campsite.copy(imageId = "photo-1").toEditableFields()["imageId"])
    }

    @Test
    fun toEditableFields_noPhoto_clearsIt() {
        // null, so an edit that removes the photo also removes it from Firestore
        val fields = campsite.toEditableFields()

        assertTrue(fields.containsKey("imageId") && fields["imageId"] == null)
    }
}
