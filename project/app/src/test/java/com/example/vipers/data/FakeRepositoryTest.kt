/*
 * FakeRepositoryTest.kt
 * Checks that the fake repositories behave like the Firestore ones, so other tests can trust them.
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeRepositoryTest {

    @Test
    fun addCampsite_givesAnIdAndCanBeLoaded() = runBlocking {
        val repo = FakeCampsiteRepository()
        val id = repo.addCampsite(Campsite(name = "Hinton Ridge Campground", ownerId = "owner-1"))
        assertEquals("Hinton Ridge Campground", repo.getCampsite(id)?.name)
        assertEquals(id, repo.getCampsite(id)?.id)
    }

    @Test
    fun getCampsite_isNullWhenMissing() = runBlocking {
        assertNull(FakeCampsiteRepository().getCampsite("nope"))
    }

    @Test
    fun updateCampsite_existingCampsite_changesEditedFieldsAndKeepsTheRest() = runBlocking {
        val saved = Campsite(
            id = "hinton",
            ownerId = "priya-phone",
            name = "Hinton Ridge Campground",
            address = "Hinton, AB",
            region = Region.HINTON,
            description = "Outdoor cookhouse",
            maxOccupancy = 8,
            tags = listOf("Cookhouse"),
            pricePerNightCents = 6500,
            rules = "Quiet hours after 10 PM",
            cancellationPolicy = "Free up to 7 days before",
            imageId = "photo-1",
            lat = 53.405,
            lng = -117.585,
            ratingSum = 46,
            ratingCount = 10,
        )
        val repo = FakeCampsiteRepository(listOf(saved))

        // only the ID matches, so the fields the owner can't edit are all different here
        repo.updateCampsite(
            Campsite(
                id = "hinton",
                ownerId = "someone-else",
                name = "Hinton Ridge Sites",
                address = "Jasper, AB",
                region = Region.JASPER,
                description = "Tennis court",
                maxOccupancy = 6,
                tags = listOf("Tennis court"),
                pricePerNightCents = 7000,
                rules = "No fires",
                cancellationPolicy = "No refunds",
                removed = true,
            )
        )

        val expected = saved.copy(
            name = "Hinton Ridge Sites",
            address = "Jasper, AB",
            region = Region.JASPER,
            description = "Tennis court",
            maxOccupancy = 6,
            tags = listOf("Tennis court"),
            pricePerNightCents = 7000,
            rules = "No fires",
            cancellationPolicy = "No refunds",
        )
        assertEquals(expected, repo.getCampsite("hinton"))
    }

    @Test
    fun updateCampsite_missingId_throws() {
        val repo = FakeCampsiteRepository()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking { repo.updateCampsite(Campsite(id = "nope")) }
        }
    }

    @Test
    fun getCampsitesByOwner_onlyReturnsTheirCampsitesThatArentRemoved() = runBlocking {
        val repo = FakeCampsiteRepository(
            listOf(
                Campsite(id = "1", name = "Mine", ownerId = "me"),
                Campsite(id = "2", name = "Someone else's", ownerId = "other"),
                Campsite(id = "3", name = "Removed", ownerId = "me", removed = true),
            )
        )
        assertEquals(listOf("Mine"), repo.getCampsitesByOwner("me").map { it.name })
    }

    @Test
    fun getDisplayName_showsTheCurrentName() = runBlocking {
        val repo = FakeProfileRepository()
        repo.saveProfile(UserProfile(id = "me", name = "Sam"))
        repo.saveProfile(UserProfile(id = "me", name = "Sam Patel"))
        assertEquals("Sam Patel", repo.getDisplayName("me"))
    }

    @Test
    fun getDisplayName_isDeletedUserWithNoProfile() = runBlocking {
        assertEquals("Deleted user", FakeProfileRepository().getDisplayName("gone"))
    }

    @Test
    fun saveEntry_twiceForSameUserKeepsOneEntry() = runBlocking {
        val repo = FakeLotteryRepository()
        repo.saveEntry(LotteryEntry(lotteryId = "lottery-1", userId = "me", status = EntryStatus.WAITING))
        repo.saveEntry(LotteryEntry(lotteryId = "lottery-1", userId = "me", status = EntryStatus.WON))
        val entries = repo.getEntries("lottery-1")
        assertEquals(1, entries.size)
        assertEquals(EntryStatus.WON, entries[0].status)
    }
}
