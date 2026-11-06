/*
 * FakeRepositoryTest.kt
 * Checks that the fake repositories behave like the Firestore ones, so other tests can trust them.
 * Known issues: none.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.JoinWaitingListResult
import com.example.vipers.domain.repository.ReserveResult
import com.example.vipers.ui.lottery.LotteryDrawer
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
