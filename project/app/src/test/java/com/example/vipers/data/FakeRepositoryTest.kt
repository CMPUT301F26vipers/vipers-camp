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
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.JoinWaitingListResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    fun seedEntry_twiceForSameUserKeepsOneEntry() = runBlocking {
        val repo = FakeLotteryRepository()
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "me", status = EntryStatus.WAITING))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "me", status = EntryStatus.WON))
        val entries = repo.getEntries("lottery-1")
        assertEquals(1, entries.size)
        assertEquals(EntryStatus.WON, entries[0].status)
    }

    @Test
    fun joinWaitingList_enforcesCapacityBoundary() = runBlocking {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-1",
            registrationStart = now - 1000L,
            registrationEnd = now + 10_000L,
            waitlistCapacity = 2,
        )
        val repo = FakeLotteryRepository(listOf(lottery))

        val result1 = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"))
        val result2 = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))
        val result3 = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-3"))

        assertEquals(JoinWaitingListResult.Success, result1)
        assertEquals(JoinWaitingListResult.Success, result2)
        assertEquals(JoinWaitingListResult.WaitlistFull, result3)

        assertEquals(2, repo.getEntries("lottery-1").size)
        assertEquals(2, repo.getLottery("lottery-1")?.waitingUserIds?.size)
    }

    @Test
    fun joinWaitingList_whenCapacityIsNull_allowsUnlimitedJoins() = runBlocking {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-1",
            registrationStart = now - 1000L,
            registrationEnd = now + 10_000L,
            waitlistCapacity = null,
        )
        val repo = FakeLotteryRepository(listOf(lottery))

        for (i in 1..10) {
            val result = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-$i"))
            assertEquals(JoinWaitingListResult.Success, result)
        }

        assertEquals(10, repo.getEntries("lottery-1").size)
        assertEquals(10, repo.getLottery("lottery-1")?.waitingUserIds?.size)
    }

    @Test
    fun joinWaitingList_duplicateEntrant_returnsAlreadyJoined() = runBlocking {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-1",
            registrationStart = now - 1000L,
            registrationEnd = now + 10_000L,
            waitlistCapacity = 5,
        )
        val repo = FakeLotteryRepository(listOf(lottery))

        val first = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"))
        val duplicate = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"))

        assertEquals(JoinWaitingListResult.Success, first)
        assertEquals(JoinWaitingListResult.AlreadyJoined, duplicate)
        assertEquals(1, repo.getEntries("lottery-1").size)
    }

    @Test
    fun joinWaitingList_concurrentJoins_strictlyEnforcesCapacity() = runBlocking {
        // Simulates concurrent joining coroutines. Note that in unit tests, FakeLotteryRepository
        // uses Mutex synchronization to guarantee thread-safe atomic checks and writes, mimicking
        // the Firestore transaction behavior implemented by FirestoreLotteryRepository in production.
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-1",
            registrationStart = now - 1000L,
            registrationEnd = now + 10_000L,
            waitlistCapacity = 3,
        )
        val repo = FakeLotteryRepository(listOf(lottery))

        val jobs = (1..10).map { i ->
            async(Dispatchers.Default) {
                repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "entrant-$i"))
            }
        }
        val results = jobs.awaitAll()

        val successes = results.count { it is JoinWaitingListResult.Success }
        val fulls = results.count { it is JoinWaitingListResult.WaitlistFull }

        assertEquals(3, successes)
        assertEquals(7, fulls)
        assertEquals(3, repo.getEntries("lottery-1").size)
        assertEquals(3, repo.getLottery("lottery-1")?.waitingUserIds?.size)
    }

    @Test
    fun saveEntry_whenEntryLeavesWaitingOrIsRemoved_updatesWaitingCountAccurately() = runBlocking {
        val now = System.currentTimeMillis()
        val lottery = Lottery(
            id = "lottery-1",
            registrationStart = now - 1000L,
            registrationEnd = now + 10_000L,
            waitlistCapacity = 1,
        )
        val repo = FakeLotteryRepository(listOf(lottery))

        val entry1 = LotteryEntry(lotteryId = "lottery-1", userId = "user-1", status = EntryStatus.WAITING)
        val res1 = repo.joinWaitingList(entry1)
        assertEquals(JoinWaitingListResult.Success, res1)
        assertEquals(1, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // Waitlist is full for user-2
        val res2Blocked = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))
        assertEquals(JoinWaitingListResult.WaitlistFull, res2Blocked)

        // user-1 wins the draw (status changes from WAITING to WON)
        repo.saveEntry(entry1.copy(status = EntryStatus.WON))
        assertEquals(0, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // Capacity is freed; user-2 can now successfully join
        val res2Allowed = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))
        assertEquals(JoinWaitingListResult.Success, res2Allowed)
        assertEquals(1, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // user-2 is removed by an admin or leaves
        repo.saveEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-2", status = EntryStatus.REMOVED))
        assertEquals(0, repo.getLottery("lottery-1")?.waitingUserIds?.size)
    }
}
