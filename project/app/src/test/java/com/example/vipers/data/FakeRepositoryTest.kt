/*
 * FakeRepositoryTest.kt
 * Checks that the fake repositories behave like the Firestore ones, so other tests can trust them.
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.DeclineTransactionResult
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ReserveResult
import com.example.vipers.domain.repository.UpdateLotteryResult
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.lottery.LotteryDrawer
import java.io.IOException
import java.util.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
                lat = 52.873,
                lng = -118.081,
                imageId = "photo-2",
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
            lat = 52.873,
            lng = -118.081,
            imageId = "photo-2",
        )
        assertEquals(expected, repo.getCampsite("hinton"))
    }

    @Test
    fun updateCampsite_noPhoto_removesTheSavedPhoto() = runBlocking {
        // null imageId is how the form saves a removed photo (US 02.01.02b)
        val repo = FakeCampsiteRepository(listOf(Campsite(id = "hinton", imageId = "photo-1")))

        repo.updateCampsite(Campsite(id = "hinton", imageId = null))

        assertNull(repo.getCampsite("hinton")?.imageId)
    }

    @Test
    fun newCampsiteId_givesADifferentIdEachTimeAndSavesNothing() = runBlocking {
        val repo = FakeCampsiteRepository()

        val first = repo.newCampsiteId()
        val second = repo.newCampsiteId()

        assertTrue(first != second)
        assertTrue(repo.getAllCampsites().isEmpty())
    }

    @Test
    fun addCampsite_withAnId_keepsIt() = runBlocking {
        val repo = FakeCampsiteRepository()
        val id = repo.newCampsiteId()

        val savedId = repo.addCampsite(Campsite(id = id, name = "Hinton Ridge Campground"))

        assertEquals(id, savedId)
        assertEquals("Hinton Ridge Campground", repo.getCampsite(id)?.name)
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
    fun getAllProfiles_returnsSeededAndSavedProfilesWithoutDuplicates() = runBlocking {
        val seeded1 = UserProfile(id = "user-1", name = "Alice")
        val seeded2 = UserProfile(id = "user-2", name = "Bob")
        val repo = FakeProfileRepository(listOf(seeded1, seeded2))

        assertEquals(
            setOf("user-1", "user-2"),
            repo.getAllProfiles().map { it.id }.toSet(),
        )

        val newProfile = UserProfile(id = "user-3", name = "Charlie")
        repo.saveProfile(newProfile)
        assertEquals(
            setOf("user-1", "user-2", "user-3"),
            repo.getAllProfiles().map { it.id }.toSet(),
        )

        val updatedProfile = UserProfile(id = "user-1", name = "Alice Updated")
        repo.saveProfile(updatedProfile)
        val allProfiles = repo.getAllProfiles()
        assertEquals(
            setOf("user-1", "user-2", "user-3"),
            allProfiles.map { it.id }.toSet(),
        )
        assertEquals(3, allProfiles.size)
        assertEquals("Alice Updated", allProfiles.first { it.id == "user-1" }.name)
    }

    @Test
    fun setNotificationsEnabled_changesOnlyThatField() = runBlocking {
        val sam = UserProfile(id = "user-1", name = "Sam", imageId = "photo-1")
        val repo = FakeProfileRepository(listOf(sam))

        repo.setNotificationsEnabled("user-1", false)

        assertEquals(sam.copy(notificationsEnabled = false), repo.getProfile("user-1"))
    }

    private val samsPhoto = ImageAsset.fromJpeg(
        FakePhotoReader.SAMPLE_JPEG, "user-1", ImageAsset.OWNER_PROFILE, "user-1", "Sam", 0,
    )

    @Test
    fun setPhoto_savesTheImageChangesOnlyThePhotoAndDeletesTheOldImage() = runBlocking {
        val images = FakeImageRepository()
        val sam = UserProfile(id = "user-1", name = "Sam", imageId = "old-photo")
        val repo = FakeProfileRepository(listOf(sam), images)

        val newId = repo.setPhoto("user-1", samsPhoto, "old-photo")

        assertNotNull(newId)
        assertEquals(sam.copy(imageId = newId), repo.getProfile("user-1"))
        assertEquals(samsPhoto.copy(id = newId!!), images.getImage(newId))
        assertEquals(listOf("old-photo"), images.deletedIds)
    }

    @Test
    fun setPhoto_toNoPhoto_clearsItAndReturnsNull() = runBlocking {
        val images = FakeImageRepository()
        val sam = UserProfile(id = "user-1", name = "Sam", imageId = "old")
        val repo = FakeProfileRepository(listOf(sam), images)

        assertNull(repo.setPhoto("user-1", null, "old"))

        assertNull(repo.getProfile("user-1")?.imageId)
        assertEquals(listOf("old"), images.deletedIds)
        assertTrue(images.getAllImages().isEmpty())
    }

    @Test
    fun setPhoto_whenTheUploadFails_changesNothing() {
        val images = FakeImageRepository(failAdds = true)
        val sam = UserProfile(id = "user-1", name = "Sam", imageId = "old-photo")
        val repo = FakeProfileRepository(listOf(sam), images)

        assertThrows(IOException::class.java) {
            runBlocking { repo.setPhoto("user-1", samsPhoto, "old-photo") }
        }

        assertEquals(sam, runBlocking { repo.getProfile("user-1") })
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun setPhotoAndSetNotificationsEnabled_withNoProfile_throwAndChangeNothing() {
        val images = FakeImageRepository()
        val repo = FakeProfileRepository(images = images)

        assertThrows(NoSuchElementException::class.java) {
            runBlocking { repo.setPhoto("gone", samsPhoto, "old-photo") }
        }
        assertTrue(runBlocking { images.getAllImages() }.isEmpty())
        assertThrows(NoSuchElementException::class.java) {
            runBlocking { repo.setNotificationsEnabled("gone", false) }
        }
        assertTrue(images.deletedIds.isEmpty())
        assertTrue(runBlocking { repo.getAllProfiles() }.isEmpty())
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
        repo.seedEntry(entry1.copy(status = EntryStatus.WON))
        assertEquals(0, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // Capacity is freed; user-2 can now successfully join
        val res2Allowed = repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))
        assertEquals(JoinWaitingListResult.Success, res2Allowed)
        assertEquals(1, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // user-2 is removed by an admin or leaves
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-2", status = EntryStatus.REMOVED))
        assertEquals(0, repo.getLottery("lottery-1")?.waitingUserIds?.size)
    }

    // US 01.04.03 / T-12: declining a win and promoting a replacement

    private val declineNow = 3_500L

    /** A fake holding Sam's entry in lottery-1 (open booking 3_000..4_000) with [status]. */
    private fun repoWithSamsEntry(
        status: EntryStatus,
        removed: Boolean = false,
        extraEntries: List<LotteryEntry> = emptyList(),
        notifications: NotificationRepository? = FakeNotificationRepository(),
    ) = FakeLotteryRepository(
        initialLotteries = listOf(
            Lottery(
                id = "lottery-1",
                ownerId = "owner-1",
                campsiteIds = listOf("camp-1"),
                registrationStart = 1_000L,
                registrationEnd = 2_000L,
                bookingStart = 3_000L,
                bookingEnd = 4_000L,
                winnersCount = 1,
                drawnAt = 2_500L,
                removed = removed,
            ),
        ),
        initialEntries = listOf(
            LotteryEntry(
                id = LotteryEntry.idFor("lottery-1", "sam"),
                lotteryId = "lottery-1",
                userId = "sam",
                groupSize = 4,
                joinedAt = 100L,
                status = status,
            ),
        ) + extraEntries,
        notificationRepository = notifications,
    )

    private fun entryFor(userId: String, status: EntryStatus, lotteryId: String = "lottery-1") =
        LotteryEntry(
            id = LotteryEntry.idFor(lotteryId, userId),
            lotteryId = lotteryId,
            userId = userId,
            groupSize = 2,
            joinedAt = 200L,
            status = status,
        )

    private fun replacementNoticeFor(
        recipientId: String,
        lotteryId: String = "lottery-1",
        promotedAt: Long = declineNow,
        read: Boolean = false,
        shownOnDevice: Boolean = false,
    ) = Notification(
        id = NotificationService.replacementNoticeId(lotteryId, recipientId),
        recipientId = recipientId,
        campsiteId = "camp-1",
        lotteryId = lotteryId,
        type = NotificationType.WON,
        title = "You won the lottery!",
        message = "Won!",
        read = read,
        shownOnDevice = shownOnDevice,
        createdAt = promotedAt,
    )

    @Test
    fun declineTransaction_wonEntryWithEmptyPool_setsDeclinedAndReturnsDeclinedNull() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(EntryStatus.WON, notifications = notifications)

        val result = repo.declineTransaction("lottery-1", "sam", null, null, declineNow)
        assertEquals(DeclineTransactionResult.Declined(null), result)
        val entry = repo.getEntry("lottery-1", "sam")
        assertEquals(EntryStatus.DECLINED, entry?.status)
        assertEquals(4, entry?.groupSize) // only the status changes
        assertEquals(100L, entry?.joinedAt)
    }

    @Test
    fun declineTransaction_wonEntryWithLostCandidateAndNotice_atomicallyDeclinesPromotesAndPreservesNoticeFlags() = runBlocking {
        val notifications = FakeNotificationRepository(
            listOf(replacementNoticeFor("kim", promotedAt = 1_000L, read = true, shownOnDevice = true)),
        )
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(
                entryFor("jo", EntryStatus.LOST),
                entryFor("kim", EntryStatus.LOST),
            ),
            notifications = notifications,
        )
        val inboxUpdates = mutableListOf<List<Notification>>()
        val sub = notifications.listenToInbox(
            "kim",
            object : InboxListener {
                override fun onInboxChanged(items: List<Notification>) {
                    inboxUpdates.add(items)
                }

                override fun onInboxError() = Unit
            },
        )

        val notice = replacementNoticeFor("kim")
        val result = repo.declineTransaction("lottery-1", "sam", "kim", notice, declineNow)
        sub.cancel()

        assertEquals(DeclineTransactionResult.Declined("kim"), result)

        val sam = repo.getEntry("lottery-1", "sam")!!
        assertEquals(EntryStatus.DECLINED, sam.status)
        assertEquals(4, sam.groupSize)
        assertEquals(100L, sam.joinedAt)

        val kim = repo.getEntry("lottery-1", "kim")!!
        assertEquals(EntryStatus.WON, kim.status)
        assertEquals(2, kim.groupSize)
        assertEquals(200L, kim.joinedAt)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)

        val kimNotices = notifications.getNotificationsFor("kim")
        assertEquals(1, kimNotices.size)
        assertEquals(notice.id, kimNotices[0].id)
        assertEquals(declineNow, kimNotices[0].createdAt)
        assertTrue(kimNotices[0].read)
        assertTrue(kimNotices[0].shownOnDevice)
        assertEquals(1, inboxUpdates.last().size)
        assertTrue(inboxUpdates.last()[0].read)
        assertTrue(inboxUpdates.last()[0].shownOnDevice)
    }

    @Test
    fun declineTransaction_optedOutCandidateWithNullNotice_promotesWithoutNotice() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("kim", EntryStatus.LOST)),
            notifications = notifications,
        )

        val result = repo.declineTransaction("lottery-1", "sam", "kim", null, declineNow)

        assertEquals(DeclineTransactionResult.Declined("kim"), result)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "kim")?.status)
        assertTrue(notifications.getNotificationsFor("kim").isEmpty())
    }

    @Test
    fun declineTransaction_calledTwice_secondReturnsNotDeclinedAndPromotesNobodyElse() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(
                entryFor("jo", EntryStatus.LOST),
                entryFor("kim", EntryStatus.LOST),
            ),
            notifications = notifications,
        )

        val first = repo.declineTransaction("lottery-1", "sam", "jo", replacementNoticeFor("jo"), declineNow)
        val second = repo.declineTransaction("lottery-1", "sam", "kim", replacementNoticeFor("kim"), declineNow)

        assertEquals(DeclineTransactionResult.Declined("jo"), first)
        assertEquals(DeclineTransactionResult.NotDeclined, second)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "jo")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "kim")?.status)
        assertEquals(1, notifications.getNotificationsFor("jo").size)
        assertTrue(notifications.getNotificationsFor("kim").isEmpty())
    }

    @Test
    fun declineTransaction_twoWinnersCompetingForSameCandidate_secondReturnsCandidateStaleWithoutWritingOrNotifying() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(
                entryFor("alex", EntryStatus.WON),
                entryFor("cand-1", EntryStatus.LOST),
                entryFor("cand-2", EntryStatus.LOST),
            ),
            notifications = notifications,
        )

        val firstAttempt = listOf(
            async(Dispatchers.Default) {
                repo.declineTransaction("lottery-1", "sam", "cand-1", replacementNoticeFor("cand-1"), declineNow)
            },
            async(Dispatchers.Default) {
                repo.declineTransaction("lottery-1", "alex", "cand-1", replacementNoticeFor("cand-1"), declineNow + 1)
            },
        ).awaitAll()

        assertEquals(
            setOf(DeclineTransactionResult.Declined("cand-1"), DeclineTransactionResult.CandidateStale),
            firstAttempt.toSet(),
        )
        assertEquals(1, notifications.getNotificationsFor("cand-1").size)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "cand-1")?.status)

        val remainingWinner = if (repo.getEntry("lottery-1", "sam")?.status == EntryStatus.WON) "sam" else "alex"
        val fallbackResult = repo.declineTransaction(
            "lottery-1",
            remainingWinner,
            "cand-2",
            replacementNoticeFor("cand-2", promotedAt = declineNow + 2),
            declineNow + 2,
        )
        assertEquals(DeclineTransactionResult.Declined("cand-2"), fallbackResult)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "alex")?.status)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "cand-2")?.status)
        assertEquals(1, notifications.getNotificationsFor("cand-2").size)
    }

    @Test
    fun declineTransaction_staleCandidate_returnsCandidateStaleWithoutWritesOrNotice() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(
                entryFor("already-won", EntryStatus.WON),
                entryFor("already-reserved", EntryStatus.RESERVED),
                entryFor("other-lottery", EntryStatus.LOST, lotteryId = "lottery-2"),
            ),
            notifications = notifications,
        )
        val emissions = mutableListOf<List<LotteryEntry>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeEntries("lottery-1").collect { emissions.add(it) }
        }
        yield()

        for (candidate in listOf("missing", "already-won", "already-reserved", "other-lottery")) {
            val res = repo.declineTransaction(
                "lottery-1",
                "sam",
                candidate,
                replacementNoticeFor(candidate),
                declineNow,
            )
            assertEquals(DeclineTransactionResult.CandidateStale, res)
            assertTrue(notifications.getNotificationsFor(candidate).isEmpty())
        }
        yield()
        collector.cancel()

        assertEquals(1, emissions.size)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.RESERVED, repo.getEntry("lottery-1", "already-reserved")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-2", "other-lottery")?.status)
    }

    @Test
    fun declineTransaction_mismatchedOrInvalidNotice_throwsAndLeavesEntriesUnchanged() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = notifications,
        )
        val valid = replacementNoticeFor("jo")
        val invalidCases = listOf(
            null to valid,
            "jo" to valid.copy(lotteryId = "lottery-2"),
            "jo" to valid.copy(recipientId = "other"),
            "jo" to valid.copy(id = "draw_lottery-1_jo"),
            "jo" to valid.copy(type = NotificationType.NOT_SELECTED),
        )

        for ((candidate, notice) in invalidCases) {
            val error = runCatching {
                repo.declineTransaction("lottery-1", "sam", candidate, notice, declineNow)
            }.exceptionOrNull()
            assertTrue("Expected IllegalArgumentException but got $error", error is IllegalArgumentException)
        }
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)
        assertTrue(notifications.getNotificationsFor("jo").isEmpty())
    }

    @Test
    fun declineTransaction_whenNoticePresentAndNotificationRepositoryMissing_throwsAndLeavesEntriesUnchanged() = runBlocking {
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = null,
        )
        val notice = replacementNoticeFor("jo")
        val error = runCatching {
            repo.declineTransaction("lottery-1", "sam", "jo", notice, declineNow)
        }.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException but got $error", error is IllegalArgumentException)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)
    }

    @Test
    fun declineTransaction_noNoticePathValidWithoutNotificationRepository_forOptOutAndEmptyPool() = runBlocking {
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = null,
        )
        // Opt-out path: candidateUserId present, replacementNotice null
        val optOutResult = repo.declineTransaction("lottery-1", "sam", "jo", null, declineNow)
        assertEquals(DeclineTransactionResult.Declined("jo"), optOutResult)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "jo")?.status)

        // Empty pool path: candidateUserId null, replacementNotice null
        val freshEmptyPoolRepo = repoWithSamsEntry(
            EntryStatus.WON,
            notifications = null,
        )
        val emptyPoolResult = freshEmptyPoolRepo.declineTransaction("lottery-1", "sam", null, null, declineNow)
        assertEquals(DeclineTransactionResult.Declined(null), emptyPoolResult)
        assertEquals(EntryStatus.DECLINED, freshEmptyPoolRepo.getEntry("lottery-1", "sam")?.status)
    }

    @Test
    fun declineTransaction_whenNoticePersistenceFails_leavesWinnerWonCandidateLostAndRetrySucceedsOnce() = runBlocking {
        val realNotifications = FakeNotificationRepository()
        var failWrite = true
        val flakyNotifications = object : NotificationRepository by realNotifications {
            override suspend fun addAll(notifications: List<Notification>) {
                if (failWrite) throw IllegalStateException("offline")
                realNotifications.addAll(notifications)
            }
        }
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = flakyNotifications,
        )
        val emissions = mutableListOf<List<LotteryEntry>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeEntries("lottery-1").collect { emissions.add(it) }
        }
        yield()

        val notice = replacementNoticeFor("jo")
        val error = runCatching {
            repo.declineTransaction("lottery-1", "sam", "jo", notice, declineNow)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        yield()
        assertEquals(1, emissions.size)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)
        assertTrue(realNotifications.getNotificationsFor("jo").isEmpty())

        failWrite = false
        val retry = repo.declineTransaction("lottery-1", "sam", "jo", notice, declineNow)
        yield()
        collector.cancel()

        assertEquals(DeclineTransactionResult.Declined("jo"), retry)
        assertEquals(2, emissions.size)
        assertEquals(EntryStatus.DECLINED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "jo")?.status)
        assertEquals(1, realNotifications.getNotificationsFor("jo").size)
    }

    @Test
    fun declineTransaction_reservedEntry_staysReservedAndReturnsNotDeclined() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.RESERVED,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = notifications,
        )

        val result = repo.declineTransaction("lottery-1", "sam", "jo", replacementNoticeFor("jo"), declineNow)
        assertEquals(DeclineTransactionResult.NotDeclined, result)
        assertEquals(EntryStatus.RESERVED, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)
        assertTrue(notifications.getNotificationsFor("jo").isEmpty())
    }

    @Test
    fun declineTransaction_removedLottery_returnsNotDeclinedAndLeavesEntriesUnchanged() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = repoWithSamsEntry(
            EntryStatus.WON,
            removed = true,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
            notifications = notifications,
        )

        val result = repo.declineTransaction("lottery-1", "sam", "jo", replacementNoticeFor("jo"), declineNow)
        assertEquals(DeclineTransactionResult.NotDeclined, result)
        assertEquals(EntryStatus.WON, repo.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, repo.getEntry("lottery-1", "jo")?.status)
        assertTrue(notifications.getNotificationsFor("jo").isEmpty())
    }

    @Test
    fun declineTransaction_bookingBoundaries_areInclusiveAndRejectOutside() = runBlocking {
        val beforeBooking = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
        )
        assertEquals(
            DeclineTransactionResult.NotDeclined,
            beforeBooking.declineTransaction("lottery-1", "sam", "jo", null, 2_999L),
        )
        assertEquals(EntryStatus.WON, beforeBooking.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, beforeBooking.getEntry("lottery-1", "jo")?.status)

        val afterBooking = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
        )
        assertEquals(
            DeclineTransactionResult.NotDeclined,
            afterBooking.declineTransaction("lottery-1", "sam", "jo", null, 4_001L),
        )
        assertEquals(EntryStatus.WON, afterBooking.getEntry("lottery-1", "sam")?.status)
        assertEquals(EntryStatus.LOST, afterBooking.getEntry("lottery-1", "jo")?.status)

        val atStart = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
        )
        assertEquals(
            DeclineTransactionResult.Declined("jo"),
            atStart.declineTransaction("lottery-1", "sam", "jo", null, 3_000L),
        )
        assertEquals(EntryStatus.WON, atStart.getEntry("lottery-1", "jo")?.status)

        val atEnd = repoWithSamsEntry(
            EntryStatus.WON,
            extraEntries = listOf(entryFor("jo", EntryStatus.LOST)),
        )
        assertEquals(
            DeclineTransactionResult.Declined("jo"),
            atEnd.declineTransaction("lottery-1", "sam", "jo", null, 4_000L),
        )
        assertEquals(EntryStatus.WON, atEnd.getEntry("lottery-1", "jo")?.status)
    }

    @Test
    fun declineTransaction_noEntry_returnsNotDeclinedAndAddsNothing() = runBlocking {
        val repo = repoWithSamsEntry(EntryStatus.WON)

        assertEquals(DeclineTransactionResult.NotDeclined, repo.declineTransaction("lottery-1", "alex", null, null, declineNow))
        assertNull(repo.getEntry("lottery-1", "alex"))
    }
    
    /** A lottery whose registration is open at [openNow], for the observeEntries tests. */
    private val openNow = 1_500L
    private val openLottery = Lottery(
        id = "lottery-1",
        ownerId = "owner-1",
        campsiteIds = listOf("camp-1"),
        registrationStart = 1_000L,
        registrationEnd = 2_000L,
        bookingStart = 3_000L,
        bookingEnd = 4_000L,
        winnersCount = 1,
    )

    @Test
    fun observeEntries_sendsTheListAgainWhenSomeoneJoins() = runBlocking {
        val repo = FakeLotteryRepository(listOf(openLottery))
        val updates = repo.observeEntries("lottery-1")
        assertEquals(emptyList<LotteryEntry>(), updates.first())

        repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"), now = openNow)
        // an entry in another lottery doesn't show up
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-2", userId = "user-2"))

        assertEquals(listOf("user-1"), updates.first().map { it.userId })
    }

    @Test
    fun observeEntries_sendsTheListAgainWhenSomeoneLeaves() = runBlocking {
        val repo = FakeLotteryRepository(listOf(openLottery))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))

        repo.leaveWaitingList("lottery-1", "user-2")

        assertEquals(listOf("user-1"), repo.observeEntries("lottery-1").first().map { it.userId })
    }

    @Test
    fun observeEntries_sendsTheDrawResults() = runBlocking {
        val repo = FakeLotteryRepository(listOf(openLottery))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-1"))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"))
        val drawer = LotteryDrawer(
            lotteryRepository = repo,
            notificationRepository = FakeNotificationRepository(),
            campsiteRepository = FakeCampsiteRepository(listOf(Campsite(id = "camp-1", name = "Pine Ridge"))),
            profileRepository = FakeProfileRepository(),
            random = Random(42),
        )

        drawer.drawLottery("lottery-1", now = 5_000L)

        val statuses = repo.observeEntries("lottery-1").first().map { it.status }
        assertEquals(setOf(EntryStatus.WON, EntryStatus.LOST), statuses.toSet())
    }

    @Test
    fun observeEntries_sendsTheListAgainWhenAWinIsUpdated() = runBlocking {
        val repo = FakeLotteryRepository(listOf(openLottery))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-1", status = EntryStatus.WON))

        repo.updateEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-1", status = EntryStatus.RESERVED))

        assertEquals(listOf(EntryStatus.RESERVED), repo.observeEntries("lottery-1").first().map { it.status })
    }

    @Test
    fun observeEntries_sendsTheCompletedAtomicDeclineAndPromotionToLiveObserver() = runBlocking {
        val notifications = FakeNotificationRepository()
        val repo = FakeLotteryRepository(listOf(openLottery), notificationRepository = notifications)
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-1", status = EntryStatus.WON))
        repo.seedEntry(LotteryEntry(lotteryId = "lottery-1", userId = "user-2", status = EntryStatus.LOST))

        val emissions = mutableListOf<Map<String, EntryStatus>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeEntries("lottery-1").collect { entries ->
                emissions.add(entries.associate { it.userId to it.status })
            }
        }
        val inboxUpdates = mutableListOf<List<Notification>>()
        val inboxSub = notifications.listenToInbox(
            "user-2",
            object : InboxListener {
                override fun onInboxChanged(items: List<Notification>) {
                    inboxUpdates.add(items)
                }

                override fun onInboxError() = Unit
            },
        )
        yield()

        val notice = replacementNoticeFor("user-2")
        val result = repo.declineTransaction("lottery-1", "user-1", "user-2", notice, declineNow)
        yield()
        collector.cancel()
        inboxSub.cancel()

        // The owner's live Waiting List (02.09.01) sees decline + replacement in one atomic emission,
        // and the promoted entrant's live inbox receives the prepared notice on commit.
        assertEquals(DeclineTransactionResult.Declined("user-2"), result)
        assertEquals(2, emissions.size)
        assertEquals(mapOf("user-1" to EntryStatus.WON, "user-2" to EntryStatus.LOST), emissions[0])
        assertEquals(mapOf("user-1" to EntryStatus.DECLINED, "user-2" to EntryStatus.WON), emissions[1])
        assertEquals(listOf(notice), inboxUpdates.last())
    }

    @Test
    fun updateLottery_successfulUpdate_updatesEditableFieldsAndPreservesUneditedFields() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1", "camp-2"),
            nightsOffered = listOf("2027-06-01"),
            nightsDescription = "Original description",
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            winnersCount = 2,
            waitlistCapacity = 10,
            drawnAt = null,
            removed = false,
            waitingUserIds = listOf("entrant-1", "entrant-2"),
            entriesVersion = 5L,
            committedDrawId = null,
            manifestChunkCount = 0,
            participantCount = 0,
            winnerCount = 0,
        )
        val repo = FakeLotteryRepository(listOf(original))

        // Proposed updates editable fields (including deduplicated campsiteIds), and maliciously attempts
        // to modify uneditable fields
        val proposed = original.copy(
            ownerId = "intruder",
            campsiteIds = listOf("camp-2", "camp-3", "camp-2"),
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            nightsDescription = "Updated description",
            registrationEnd = 2_500L,
            bookingStart = 3_500L,
            bookingEnd = 4_500L,
            winnersCount = 4,
            waitlistCapacity = 15,
            drawnAt = 9999L,
            removed = true,
            waitingUserIds = emptyList(),
            entriesVersion = 99L,
        )

        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)
        assertEquals(UpdateLotteryResult.Success, result)

        val updated = repo.getLottery("lottery-1")
        assertNotNull(updated)
        // Verified updated fields
        assertEquals(listOf("camp-2", "camp-3"), updated!!.campsiteIds)
        assertEquals(listOf("2027-06-01", "2027-06-02"), updated.nightsOffered)
        assertEquals("Updated description", updated.nightsDescription)
        assertEquals(2_500L, updated.registrationEnd)
        assertEquals(3_500L, updated.bookingStart)
        assertEquals(4_500L, updated.bookingEnd)
        assertEquals(4, updated.winnersCount)
        assertEquals(15, updated.waitlistCapacity)

        // Verified preserved fields (tamper attempts ignored)
        assertEquals("owner-1", updated.ownerId)
        assertEquals(listOf("entrant-1", "entrant-2"), updated.waitingUserIds)
        assertEquals(5L, updated.entriesVersion)
        assertNull(updated.drawnAt)
        assertFalse(updated.removed)
    }

    @Test
    fun updateLottery_changingCampsiteIds_updatesCampsiteLookupAndPreservesEntries() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1", "camp-2"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            waitingUserIds = listOf("entrant-1"),
        )
        val entry = LotteryEntry(lotteryId = "lottery-1", userId = "entrant-1", status = EntryStatus.WAITING)
        val repo = FakeLotteryRepository(listOf(original), listOf(entry))

        val updatedResult = repo.updateLottery(
            original.copy(campsiteIds = listOf("camp-2", "camp-3")),
            callerId = "owner-1",
            now = now,
        )
        assertEquals(UpdateLotteryResult.Success, updatedResult)

        assertTrue(repo.getLotteriesForCampsite("camp-1").isEmpty())
        assertEquals(listOf("lottery-1"), repo.getLotteriesForCampsite("camp-2").map { it.id })
        assertEquals(listOf("lottery-1"), repo.getLotteriesForCampsite("camp-3").map { it.id })
        assertEquals(listOf("entrant-1"), repo.getLottery("lottery-1")?.waitingUserIds)
        assertEquals(listOf("entrant-1"), repo.getEntries("lottery-1").map { it.userId })
    }

    @Test
    fun updateLottery_joinBetweenFormLoadAndSave_capacityBelowLatestCountRejected() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            winnersCount = 2,
            waitlistCapacity = 10,
            waitingUserIds = listOf("user-1"),
        )
        val repo = FakeLotteryRepository(listOf(original))

        // Entrants join the waiting list between form loading and submission
        repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"), now = now)
        repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-3"), now = now)
        assertEquals(3, repo.getLottery("lottery-1")?.waitingUserIds?.size)

        // Owner attempts to reduce capacity to 2 (below the new count of 3)
        val proposed = original.copy(waitlistCapacity = 2)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertTrue(result is UpdateLotteryResult.Error)
        assertEquals("Waitlist limit cannot be less than current entrant count (3)", (result as UpdateLotteryResult.Error).message)

        // Stored lottery is unchanged
        val stored = repo.getLottery("lottery-1")
        assertEquals(10, stored?.waitlistCapacity)
        assertEquals(3, stored?.waitingUserIds?.size)
    }

    @Test
    fun updateLottery_joinBetweenFormLoadAndSave_sufficientCapacityPreservesNewEntrants() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            winnersCount = 2,
            waitlistCapacity = 5,
            waitingUserIds = listOf("user-1"),
        )
        val repo = FakeLotteryRepository(listOf(original))

        // Concurrent entrant joins before save
        repo.joinWaitingList(LotteryEntry(lotteryId = "lottery-1", userId = "user-2"), now = now)

        // Owner updates capacity to 8 with form snapshot that had only user-1
        val proposed = original.copy(waitlistCapacity = 8, winnersCount = 3)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertEquals(UpdateLotteryResult.Success, result)
        val stored = repo.getLottery("lottery-1")
        assertEquals(8, stored?.waitlistCapacity)
        assertEquals(3, stored?.winnersCount)
        // New entrant user-2 is completely preserved!
        assertEquals(listOf("user-1", "user-2"), stored?.waitingUserIds)
    }

    @Test
    fun updateLottery_closedLottery_rejected() = runBlocking {
        val now = 2_500L // After registrationEnd (2000L)
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
        )
        val repo = FakeLotteryRepository(listOf(original))

        val proposed = original.copy(registrationEnd = 3_000L)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertTrue(result is UpdateLotteryResult.ReadOnly)
        assertEquals("Registration has closed; this lottery can no longer be edited.", (result as UpdateLotteryResult.ReadOnly).reason)
    }

    @Test
    fun updateLottery_drawnLottery_rejected() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            drawnAt = 2_005L,
        )
        val repo = FakeLotteryRepository(listOf(original))

        val proposed = original.copy(winnersCount = 10)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertTrue(result is UpdateLotteryResult.ReadOnly)
        assertEquals("Cannot edit a lottery that has already been drawn", (result as UpdateLotteryResult.ReadOnly).reason)
    }

    @Test
    fun updateLottery_removedLottery_rejected() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            removed = true,
        )
        val repo = FakeLotteryRepository(listOf(original))

        val proposed = original.copy(winnersCount = 10)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertTrue(result is UpdateLotteryResult.ReadOnly)
        assertEquals("Cannot edit a removed lottery", (result as UpdateLotteryResult.ReadOnly).reason)
    }

    @Test
    fun updateLottery_missingLottery_rejected() = runBlocking {
        val repo = FakeLotteryRepository()
        val lottery = Lottery(
            id = "non-existent",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
        )
        val result = repo.updateLottery(lottery, callerId = "owner-1", now = 1_500L)

        assertTrue(result is UpdateLotteryResult.Error)
        assertEquals("Lottery not found", (result as UpdateLotteryResult.Error).message)
    }

    @Test
    fun updateLottery_nonOwnerCaller_rejected() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
        )
        val repo = FakeLotteryRepository(listOf(original))

        // Intruder calls updateLottery with their own callerId
        val proposed = original.copy(winnersCount = 10)
        val result = repo.updateLottery(proposed, callerId = "intruder-device", now = now)

        assertTrue(result is UpdateLotteryResult.Error)
        assertEquals("You do not own this lottery", (result as UpdateLotteryResult.Error).message)

        // Stored lottery unchanged
        assertEquals(1, repo.getLottery("lottery-1")?.winnersCount)
    }

    @Test
    fun updateLottery_validationFailureLeavesStoredLotteryUnchanged() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            winnersCount = 2,
        )
        val repo = FakeLotteryRepository(listOf(original))

        // Invalid: zero winners count
        val proposed = original.copy(winnersCount = 0)
        val result = repo.updateLottery(proposed, callerId = "owner-1", now = now)

        assertTrue(result is UpdateLotteryResult.Error)
        assertEquals("At least one winner is required", (result as UpdateLotteryResult.Error).message)

        val stored = repo.getLottery("lottery-1")
        assertEquals(2, stored?.winnersCount)
    }

    @Test
    fun updateLottery_preservesObserveEntriesLiveStream() = runBlocking {
        val now = 1_500L
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            waitlistCapacity = 10,
        )
        val repo = FakeLotteryRepository(listOf(original))
        val entry1 = LotteryEntry(lotteryId = "lottery-1", userId = "user-1")
        repo.joinWaitingList(entry1, now = now)

        val entriesFlow = repo.observeEntries("lottery-1")
        assertEquals(listOf("user-1"), entriesFlow.first().map { it.userId })

        // Update lottery details
        val updateRes = repo.updateLottery(original.copy(winnersCount = 5), callerId = "owner-1", now = now)
        assertEquals(UpdateLotteryResult.Success, updateRes)

        // Entries stream remains undisturbed and accurate
        assertEquals(listOf("user-1"), entriesFlow.first().map { it.userId })
    }

    @Test
    fun updateLottery_withForeignOrRemovedCampsite_rejectedAndPreservesMembershipAndEntrants() = runBlocking {
        val now = 1_500L
        val campsites = FakeCampsiteRepository(
            listOf(
                Campsite(id = "camp-1", ownerId = "owner-1", name = "Pine Ridge"),
                Campsite(id = "camp-removed", ownerId = "owner-1", name = "Removed Site", removed = true),
                Campsite(id = "camp-foreign", ownerId = "other-owner", name = "Other Owner Site"),
            ),
        )
        val original = Lottery(
            id = "lottery-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 1_000L,
            registrationEnd = 2_000L,
            bookingStart = 3_000L,
            bookingEnd = 4_000L,
            waitingUserIds = listOf("entrant-1"),
        )
        val entry = LotteryEntry(lotteryId = "lottery-1", userId = "entrant-1", status = EntryStatus.WAITING)
        val repo = FakeLotteryRepository(
            initialLotteries = listOf(original),
            initialEntries = listOf(entry),
            campsiteRepository = campsites,
        )

        val foreignResult = repo.updateLottery(
            original.copy(campsiteIds = listOf("camp-1", "camp-foreign")),
            callerId = "owner-1",
            now = now,
        )
        assertEquals(
            UpdateLotteryResult.Error("One or more selected campsites are no longer available or you do not own them."),
            foreignResult,
        )
        assertEquals(listOf("camp-1"), repo.getLottery("lottery-1")?.campsiteIds)
        assertEquals(listOf("entrant-1"), repo.getLottery("lottery-1")?.waitingUserIds)
        assertEquals(listOf("entrant-1"), repo.getEntries("lottery-1").map { it.userId })

        val removedResult = repo.updateLottery(
            original.copy(campsiteIds = listOf("camp-1", "camp-removed")),
            callerId = "owner-1",
            now = now,
        )
        assertEquals(
            UpdateLotteryResult.Error("One or more selected campsites are no longer available or you do not own them."),
            removedResult,
        )
        assertEquals(listOf("camp-1"), repo.getLottery("lottery-1")?.campsiteIds)
        assertEquals(listOf("entrant-1"), repo.getLottery("lottery-1")?.waitingUserIds)
        assertEquals(listOf("entrant-1"), repo.getEntries("lottery-1").map { it.userId })
    }

    @Test
    fun updateLottery_staleDraftReintroducingCampsiteWithUnavailableNight_rejectedAndPreservesMembershipAndEntrants() =
        runBlocking {
            val now = 1_500L
            val campsites = FakeCampsiteRepository(
                listOf(
                    Campsite(id = "camp-1", ownerId = "owner-1", name = "Pine Ridge"),
                    Campsite(id = "camp-2", ownerId = "owner-1", name = "Maple Grove"),
                ),
            )
            campsites.saveAvailability(AvailabilityCalendar("camp-1", openNights = listOf("2027-06-01")))
            campsites.saveAvailability(AvailabilityCalendar("camp-2", openNights = listOf("2027-06-01")))

            val original = Lottery(
                id = "lottery-1",
                ownerId = "owner-1",
                campsiteIds = listOf("camp-1", "camp-2"),
                nightsOffered = listOf("2027-06-01"),
                registrationStart = 1_000L,
                registrationEnd = 2_000L,
                bookingStart = 3_000L,
                bookingEnd = 4_000L,
                winnersCount = 1,
                waitingUserIds = listOf("entrant-1"),
            )
            val entry = LotteryEntry(lotteryId = "lottery-1", userId = "entrant-1", status = EntryStatus.WAITING)
            val repo = FakeLotteryRepository(
                initialLotteries = listOf(original),
                initialEntries = listOf(entry),
                campsiteRepository = campsites,
            )

            // First edit drops camp-2 from the lottery.
            val dropResult = repo.updateLottery(
                original.copy(campsiteIds = listOf("camp-1")),
                callerId = "owner-1",
                now = now,
            )
            assertEquals(UpdateLotteryResult.Success, dropResult)

            // Both calendars later hold 2027-06-01; an unrelated edit on camp-1 still succeeds.
            campsites.saveAvailability(
                AvailabilityCalendar("camp-1", openNights = listOf("2027-06-01"), heldNights = listOf("2027-06-01")),
            )
            campsites.saveAvailability(
                AvailabilityCalendar("camp-2", openNights = listOf("2027-06-01"), heldNights = listOf("2027-06-01")),
            )
            val unrelatedResult = repo.updateLottery(
                original.copy(campsiteIds = listOf("camp-1"), winnersCount = 2),
                callerId = "owner-1",
                now = now,
            )
            assertEquals(UpdateLotteryResult.Success, unrelatedResult)

            // Stale draft captured before drop/hold tries to reintroduce camp-2 with 2027-06-01.
            val staleResult = repo.updateLottery(original, callerId = "owner-1", now = now)
            assertEquals(
                UpdateLotteryResult.Error(
                    "One or more selected nights are no longer available. Please reload the nights.",
                ),
                staleResult,
            )

            val stored = repo.getLottery("lottery-1")!!
            assertEquals(listOf("camp-1"), stored.campsiteIds)
            assertEquals(2, stored.winnersCount)
            assertEquals(listOf("entrant-1"), stored.waitingUserIds)
            assertEquals(listOf("entrant-1"), repo.getEntries("lottery-1").map { it.userId })
        }

    /** Keeps every inbox it is sent, oldest call first (T-13). */
    private class RecordingInboxListener : InboxListener {
        val calls = mutableListOf<List<Notification>>()
        override fun onInboxChanged(items: List<Notification>) {
            calls.add(items)
        }

        // the fake never fails to load
        override fun onInboxError() = throw AssertionError("the fake inbox never fails")
    }

    // Sam's win, sent at time 100
    private val samsWin = Notification(
        id = "win", recipientId = "sam", type = NotificationType.WON, createdAt = 100,
    )

    @Test
    fun listenToInbox_atOnceAndAfterAddAndMarkRead_sendsTheInbox() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val listener = RecordingInboxListener()
        repo.listenToInbox("sam", listener)

        assertEquals(listOf(listOf("win")), listener.calls.map { inbox -> inbox.map { it.id } })

        val id = repo.addNotification(
            Notification(recipientId = "sam", type = NotificationType.NOT_SELECTED, createdAt = 200),
        )
        assertEquals(2, listener.calls.size)
        assertEquals(listOf(id, "win"), listener.calls[1].map { it.id })

        repo.markRead("win")
        assertEquals(3, listener.calls.size)
        assertTrue(listener.calls[2].single { it.id == "win" }.read)
    }

    @Test
    fun listenToInbox_itemsOutOfOrder_sendsNewestFirst() = runBlocking {
        val repo = FakeNotificationRepository(
            listOf(
                samsWin,
                Notification(id = "newest", recipientId = "sam", createdAt = 300),
                Notification(id = "oldest", recipientId = "sam", createdAt = 50),
            )
        )
        val listener = RecordingInboxListener()
        repo.listenToInbox("sam", listener)

        assertEquals(listOf("newest", "win", "oldest"), listener.calls.last().map { it.id })
    }

    @Test
    fun listenToInbox_otherUsersItems_neverAppear() = runBlocking {
        val repo = FakeNotificationRepository(
            listOf(samsWin, Notification(id = "alex-win", recipientId = "alex", createdAt = 200)),
        )
        val listener = RecordingInboxListener()
        repo.listenToInbox("sam", listener)
        repo.addNotification(Notification(recipientId = "alex", createdAt = 300))
        repo.markRead("alex-win")
        repo.addNotification(Notification(recipientId = "sam", createdAt = 400))

        assertTrue(listener.calls.isNotEmpty())
        assertTrue(listener.calls.all { inbox -> inbox.all { it.recipientId == "sam" } })
    }

    @Test
    fun listenToInbox_afterSaveNotification_sendsTheInbox() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val listener = RecordingInboxListener()
        repo.listenToInbox("sam", listener)

        // e.g. the draw (02.05.01a) saving a result with its own ID
        repo.saveNotification(Notification(id = "lost", recipientId = "sam", createdAt = 300))

        assertEquals(listOf("lost", "win"), listener.calls.last().map { it.id })
    }

    @Test
    fun listenToInbox_afterCancel_sendsNothingMore() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val listener = RecordingInboxListener()
        val subscription = repo.listenToInbox("sam", listener)
        subscription.cancel()

        repo.addNotification(Notification(recipientId = "sam", createdAt = 200))
        repo.markRead("win")

        assertEquals(1, listener.calls.size) // only the first inbox, sent straight away
    }

    // Sam's and Alex's results from one draw, each with its own ID (US 01.03.01)
    private val drawResults = listOf(
        Notification(id = "lottery-1_sam", recipientId = "sam", type = NotificationType.WON, createdAt = 200),
        Notification(id = "lottery-1_alex", recipientId = "alex", type = NotificationType.NOT_SELECTED, createdAt = 200),
    )

    @Test
    fun addAll_sameIdsTwice_keepsOneCopyEach() = runBlocking {
        val repo = FakeNotificationRepository()
        repo.addAll(drawResults)
        repo.addAll(drawResults)

        assertEquals(listOf("lottery-1_sam"), repo.getNotificationsFor("sam").map { it.id })
        assertEquals(listOf("lottery-1_alex"), repo.getNotificationsFor("alex").map { it.id })
    }

    @Test
    fun addAll_sameIdsAfterReadAndShown_keepsThemReadAndShown() = runBlocking {
        val repo = FakeNotificationRepository()
        repo.addAll(drawResults)
        repo.markRead("lottery-1_sam")
        // Sam's was also shown on the phone, e.g. by the device-notification worker
        repo.saveNotification(repo.getNotificationsFor("sam").single().copy(shownOnDevice = true))

        repo.addAll(drawResults.map { it.copy(message = "sent again") })

        val sams = repo.getNotificationsFor("sam").single()
        assertTrue(sams.read)
        assertTrue(sams.shownOnDevice)
        assertEquals("sent again", sams.message) // the content is still replaced
        val alexs = repo.getNotificationsFor("alex").single()
        assertFalse(alexs.read) // Alex never opened theirs
        assertFalse(alexs.shownOnDevice)
    }

    @Test
    fun addAll_emptyId_throwsAndSavesNothing() = runBlocking {
        val repo = FakeNotificationRepository()
        val withoutId = Notification(id = "", recipientId = "sam", createdAt = 300)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addAll(drawResults + withoutId) }
        }
        // checked before writing, so the valid ones aren't saved either
        assertTrue(repo.getNotificationsFor("sam").isEmpty())
        assertTrue(repo.getNotificationsFor("alex").isEmpty())
    }

    @Test
    fun addAll_withListener_sendsTheNewItemsOnce() = runBlocking {
        val repo = FakeNotificationRepository(listOf(samsWin))
        val listener = RecordingInboxListener()
        repo.listenToInbox("sam", listener)

        repo.addAll(
            drawResults + Notification(id = "lottery-2_sam", recipientId = "sam", createdAt = 300),
        )

        // the first inbox straight away, then one more for the whole batch
        assertEquals(2, listener.calls.size)
        assertEquals(listOf("lottery-2_sam", "lottery-1_sam", "win"), listener.calls.last().map { it.id })
    }

    @Test
    fun createReservation_successful_holdsNightsUpdatesEntryAndCreatesReservation() = runBlocking {
        val campsite = Campsite(id = "site-1", ownerId = "owner-1", name = "Pine Lake", maxOccupancy = 4, pricePerNightCents = 5000)
        val calendar = AvailabilityCalendar(campsiteId = "site-1", openNights = listOf("2027-06-01", "2027-06-02", "2027-06-03"))
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        campsiteRepo.saveAvailability(calendar)

        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01", "2027-06-02", "2027-06-03"),
            bookingStart = 1000,
            bookingEnd = 5000,
            drawnAt = 900,
        )
        val entry = LotteryEntry(id = "lot-1_user-1", lotteryId = "lot-1", userId = "user-1", status = EntryStatus.WON)
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))

        val reservationRepo = FakeReservationRepository(campsiteRepository = campsiteRepo, lotteryRepository = lotteryRepo)

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-01", "2027-06-02"),
            groupSize = 3,
            now = 2000,
        )

        assertTrue(result is ReserveResult.Success)
        val reservation = (result as ReserveResult.Success).reservation
        assertEquals("reservation-1", reservation.id)
        assertEquals("R-10001", reservation.reservationNumber)
        assertEquals("2027-06-01", reservation.checkIn)
        assertEquals("2027-06-03", reservation.checkOut)
        assertEquals(10000L, reservation.totalPriceCents)
        assertEquals("$100", reservation.totalPriceText)
        assertEquals(3, reservation.groupSize)
        assertEquals(ReservationStatus.CONFIRMED, reservation.status)

        // Campsite calendar now holds those nights
        val updatedCal = campsiteRepo.getAvailability("site-1")
        assertEquals(listOf("2027-06-01", "2027-06-02"), updatedCal.heldNights)

        // Entrant's lottery entry is marked RESERVED
        val updatedEntry = lotteryRepo.getEntry("lot-1", "user-1")
        assertEquals(EntryStatus.RESERVED, updatedEntry?.status)

        // Reservation appears in user's and owner's reservations
        assertEquals(listOf(reservation), reservationRepo.getReservationsForUser("user-1"))
        assertEquals(listOf(reservation), reservationRepo.getReservationsForOwner("owner-1"))
    }

    @Test
    fun createReservation_conflict_failsWhenNightAlreadyHeld() = runBlocking {
        val campsite = Campsite(id = "site-1", ownerId = "owner-1", name = "Pine Lake", maxOccupancy = 4, pricePerNightCents = 5000)
        val calendar = AvailabilityCalendar(
            campsiteId = "site-1",
            openNights = listOf("2027-06-01", "2027-06-02"),
            heldNights = listOf("2027-06-01"),
        )
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        campsiteRepo.saveAvailability(calendar)

        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            bookingStart = 1000,
            bookingEnd = 5000,
            drawnAt = 900,
        )
        val entry = LotteryEntry(id = "lot-1_user-2", lotteryId = "lot-1", userId = "user-2", status = EntryStatus.WON)
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))

        val reservationRepo = FakeReservationRepository(campsiteRepository = campsiteRepo, lotteryRepository = lotteryRepo)

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-2",
            nights = listOf("2027-06-01", "2027-06-02"),
            groupSize = 2,
            now = 2000,
        )

        assertEquals(ReserveResult.Conflict, result)
        assertEquals(EntryStatus.WON, lotteryRepo.getEntry("lot-1", "user-2")?.status)
        assertTrue(reservationRepo.getReservationsForUser("user-2").isEmpty())
    }

    @Test
    fun createReservation_capacityExceeded_whenGroupSizeGreaterThanMaxOccupancy() = runBlocking {
        val campsite = Campsite(id = "site-1", maxOccupancy = 4)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01"),
            bookingStart = 1000,
            bookingEnd = 5000,
        )
        val entry = LotteryEntry(id = "lot-1_user-1", lotteryId = "lot-1", userId = "user-1", status = EntryStatus.WON)
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))

        val reservationRepo = FakeReservationRepository(campsiteRepository = campsiteRepo, lotteryRepository = lotteryRepo)

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-01"),
            groupSize = 6,
            now = 2000,
        )

        assertEquals(ReserveResult.CapacityExceeded, result)
    }

    @Test
    fun createReservation_notEligible_whenBookingPeriodExpiredOrStatusNotWon() = runBlocking {
        val campsite = Campsite(id = "site-1", maxOccupancy = 4)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01"),
            bookingStart = 1000,
            bookingEnd = 5000,
        )
        val entry = LotteryEntry(id = "lot-1_user-1", lotteryId = "lot-1", userId = "user-1", status = EntryStatus.WON)
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))

        val reservationRepo = FakeReservationRepository(campsiteRepository = campsiteRepo, lotteryRepository = lotteryRepo)

        // Expired booking window (now = 6000 > bookingEnd = 5000)
        val resultExpired = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-01"),
            groupSize = 2,
            now = 6000,
        )
        assertEquals(ReserveResult.NotEligible, resultExpired)

        // Status not WON (e.g. WAITING)
        lotteryRepo.seedEntry(entry.copy(status = EntryStatus.WAITING))
        val resultNotWon = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-01"),
            groupSize = 2,
            now = 2000,
        )
        assertEquals(ReserveResult.NotEligible, resultNotWon)
    }

    @Test
    fun createReservation_failsWhenCampsiteNotInLottery() = runBlocking {
        val campsite = Campsite(id = "site-2", maxOccupancy = 4)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01"),
            bookingStart = 1000,
            bookingEnd = 5000,
        )
        val entry = LotteryEntry(
            id = "lot-1_user-1",
            lotteryId = "lot-1",
            userId = "user-1",
            status = EntryStatus.WON,
        )
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))
        val reservationRepo = FakeReservationRepository(
            campsiteRepository = campsiteRepo,
            lotteryRepository = lotteryRepo,
        )

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-2",
            userId = "user-1",
            nights = listOf("2027-06-01"),
            groupSize = 2,
            now = 2000,
        )

        assertTrue(result is ReserveResult.Error)
        assertEquals(
            "Campsite is not offered in this lottery",
            (result as ReserveResult.Error).message,
        )
    }

    @Test
    fun createReservation_failsWhenNightsNotOfferedInLottery() = runBlocking {
        val campsite = Campsite(id = "site-1", maxOccupancy = 4)
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01"),
            bookingStart = 1000,
            bookingEnd = 5000,
        )
        val entry = LotteryEntry(
            id = "lot-1_user-1",
            lotteryId = "lot-1",
            userId = "user-1",
            status = EntryStatus.WON,
        )
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))
        val reservationRepo = FakeReservationRepository(
            campsiteRepository = campsiteRepo,
            lotteryRepository = lotteryRepo,
        )

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-05"),
            groupSize = 2,
            now = 2000,
        )

        assertTrue(result is ReserveResult.Error)
        assertEquals(
            "Selected nights are not offered in this lottery",
            (result as ReserveResult.Error).message,
        )
    }

    @Test
    fun createReservation_sendsReservationConfirmedNotificationToEntrant() = runBlocking {
        val campsite = Campsite(
            id = "site-1",
            ownerId = "owner-1",
            name = "Pine Lake",
            maxOccupancy = 4,
            pricePerNightCents = 5000,
        )
        val calendar = AvailabilityCalendar(
            campsiteId = "site-1",
            openNights = listOf("2027-06-01", "2027-06-02"),
        )
        val campsiteRepo = FakeCampsiteRepository(listOf(campsite))
        campsiteRepo.saveAvailability(calendar)

        val lottery = Lottery(
            id = "lot-1",
            campsiteIds = listOf("site-1"),
            nightsOffered = listOf("2027-06-01", "2027-06-02"),
            bookingStart = 1000,
            bookingEnd = 5000,
        )
        val entry = LotteryEntry(
            id = "lot-1_user-1",
            lotteryId = "lot-1",
            userId = "user-1",
            status = EntryStatus.WON,
        )
        val lotteryRepo = FakeLotteryRepository(listOf(lottery), listOf(entry))
        val notificationRepo = FakeNotificationRepository()
        val reservationRepo = FakeReservationRepository(
            campsiteRepository = campsiteRepo,
            lotteryRepository = lotteryRepo,
            notificationRepository = notificationRepo,
        )

        val result = reservationRepo.createReservation(
            lotteryId = "lot-1",
            campsiteId = "site-1",
            userId = "user-1",
            nights = listOf("2027-06-01"),
            groupSize = 2,
            now = 2000,
        )

        assertTrue(result is ReserveResult.Success)
        val reservation = (result as ReserveResult.Success).reservation

        val notifications = notificationRepo.getNotificationsFor("user-1")
        assertEquals(1, notifications.size)
        val notice = notifications.first()
        assertEquals(NotificationType.RESERVATION_CONFIRMED, notice.type)
        assertEquals("Reservation confirmed", notice.title)
        assertEquals("Pine Lake: 2027-06-01 to 2027-06-02", notice.message)
        assertEquals(reservation.id, notice.reservationId)
        assertEquals("site-1", notice.campsiteId)
        assertEquals("lot-1", notice.lotteryId)
        assertEquals(NotificationTarget.OpenReservation(reservation.id), notice.target)
    }

    // one confirmed stay at Pine Lake for June 1 and 2; June 3 is held by someone else
    private val pineLake =
        FakeCampsiteRepository(listOf(Campsite(id = "site-1", name = "Pine Lake")))
    private val pineLakeInbox = FakeNotificationRepository()

    private suspend fun pineLakeReservations(): FakeReservationRepository {
        pineLake.saveAvailability(
            AvailabilityCalendar(
                campsiteId = "site-1",
                openNights = listOf("2027-06-01", "2027-06-02", "2027-06-03"),
                heldNights = listOf("2027-06-01", "2027-06-02", "2027-06-03"),
            )
        )
        val stay = Reservation(
            id = "res-1",
            userId = "user-1",
            ownerId = "owner-1",
            campsiteId = "site-1",
            campsiteName = "Pine Lake",
            checkIn = "2027-06-01",
            checkOut = "2027-06-03",
        )
        return FakeReservationRepository(
            pineLake,
            FakeLotteryRepository(),
            pineLakeInbox,
            listOf(stay),
        )
    }

    @Test
    fun changeStatus_cancel_releasesOnlyItsNightsAndNotifiesTheEntrant() = runBlocking {
        val reservationRepo = pineLakeReservations()

        assertTrue(reservationRepo.changeStatus("res-1", ReservationStatus.CANCELLED, now = 3000))

        val saved = reservationRepo.getReservation("res-1")!!
        assertEquals(ReservationStatus.CANCELLED, saved.status)
        assertEquals(3000L, saved.statusChangedAt)
        assertNull(saved.rejectionReason)
        // June 3 belongs to someone else's stay, so it stays held
        assertEquals(listOf("2027-06-03"), pineLake.getAvailability("site-1").heldNights)

        val notice = pineLakeInbox.getNotificationsFor("user-1").single()
        assertEquals("cancelled_res-1", notice.id)
        assertEquals(NotificationType.RESERVATION_CANCELLED, notice.type)
        assertEquals("Pine Lake: 2027-06-01 to 2027-06-03", notice.message)
        assertEquals(NotificationTarget.OpenReservation("res-1"), notice.target)
    }

    @Test
    fun changeStatus_reject_savesTheReasonInTheReservationAndNotice() = runBlocking {
        val reservationRepo = pineLakeReservations()

        reservationRepo.changeStatus("res-1", ReservationStatus.REJECTED, "Fire warning", 3000)

        assertEquals("Fire warning", reservationRepo.getReservation("res-1")!!.rejectionReason)
        val notice = pineLakeInbox.getNotificationsFor("user-1").single()
        assertEquals(NotificationType.RESERVATION_REJECTED, notice.type)
        assertEquals("owner-1", notice.senderId)
        assertEquals(
            "Pine Lake: 2027-06-01 to 2027-06-03. Reason: Fire warning",
            notice.message,
        )
    }

    @Test
    fun changeStatus_withoutNotice_stillReleasesTheNights() = runBlocking {
        val reservationRepo = pineLakeReservations()

        val changed = reservationRepo.changeStatus(
            "res-1", ReservationStatus.REJECTED, "Fire warning", 3000, sendNotice = false,
        )

        assertTrue(changed)
        assertEquals(ReservationStatus.REJECTED, reservationRepo.getReservation("res-1")!!.status)
        assertEquals(listOf("2027-06-03"), pineLake.getAvailability("site-1").heldNights)
        assertTrue(pineLakeInbox.getNotificationsFor("user-1").isEmpty())
    }

    @Test
    fun changeStatus_secondTime_changesNothing() = runBlocking {
        val reservationRepo = pineLakeReservations()
        reservationRepo.changeStatus("res-1", ReservationStatus.CANCELLED, now = 3000)

        val again = reservationRepo.changeStatus("res-1", ReservationStatus.REJECTED, "Late", 4000)

        assertFalse(again)
        assertEquals(ReservationStatus.CANCELLED, reservationRepo.getReservation("res-1")!!.status)
        assertEquals(1, pineLakeInbox.getNotificationsFor("user-1").size)
    }

    @Test
    fun changeStatus_missingReservation_returnsFalse() = runBlocking {
        val reservationRepo = pineLakeReservations()
        assertFalse(reservationRepo.changeStatus("nope", ReservationStatus.CANCELLED))
    }

    @Test
    fun changeStatus_toConfirmed_throws() {
        val reservationRepo = FakeReservationRepository()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { reservationRepo.changeStatus("res-1", ReservationStatus.CONFIRMED) }
        }
    }
}
