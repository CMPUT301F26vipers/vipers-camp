/*
 * DeletionServiceTest.kt
 * Unit tests for deleting a profile (US 01.08.01): the profile and photo are deleted, the user's
 * entries are marked Removed, their reservations are cancelled with the nights given back, their
 * campsites are removed with their photos, the reservations there and the owner's lotteries for
 * them, owners see "Deleted user", and a failed step keeps the profile so trying again finishes
 * the job. Uses the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.domain.service

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeImageRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakePhotoReader
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DeletionServiceTest {

    private val now = 1_809_000_000_000 // April 2027

    private val images = FakeImageRepository()
    private val profiles = FakeProfileRepository(
        listOf(
            UserProfile(id = "sam", name = "Sam Patel", imageId = "sam-photo"),
            UserProfile(id = "alex", name = "Alex Kim"),
            UserProfile(id = "kim", name = "Kim Lee"),
            UserProfile(id = "lee", name = "Lee Wong", notificationsEnabled = false),
        ),
        images,
    )
    private val campsites = FakeCampsiteRepository(
        listOf(
            Campsite(id = "sams-site", ownerId = "sam", name = "Sam's Site", imageId = "site-photo"),
            Campsite(id = "sams-lake", ownerId = "sam", name = "Sam's Lake"),
            Campsite(id = "alex-site", ownerId = "alex", name = "Alex's Site"),
        ),
    )
    private val lotteries = FakeLotteryRepository(
        initialLotteries = listOf(
            // Sam's own lottery for Sam's campsite
            Lottery(id = "sams-lottery", ownerId = "sam", campsiteIds = listOf("sams-site"),
                waitingUserIds = listOf("lee")),
            // Sam's lottery for both of Sam's campsites (US 02.04.02)
            Lottery(id = "sams-two-sites", ownerId = "sam",
                campsiteIds = listOf("sams-site", "sams-lake"), waitingUserIds = listOf("kim")),
            // Alex's lottery, which Sam joined
            Lottery(id = "alex-lottery", ownerId = "alex", campsiteIds = listOf("alex-site"),
                waitingUserIds = listOf("sam", "kim")),
            // an older lottery of Alex's that Sam won and then declined
            Lottery(id = "old-lottery", ownerId = "alex", campsiteIds = listOf("alex-site")),
            // Alex's lottery that also lists Sam's campsite
            Lottery(id = "shared-lottery", ownerId = "alex",
                campsiteIds = listOf("alex-site", "sams-site"), waitingUserIds = listOf("lee")),
        ),
        initialEntries = listOf(
            entry("sams-lottery", "lee", EntryStatus.WAITING),
            entry("sams-lottery", "kim", EntryStatus.WON),
            entry("alex-lottery", "sam", EntryStatus.WAITING),
            entry("alex-lottery", "kim", EntryStatus.WAITING),
            entry("old-lottery", "sam", EntryStatus.DECLINED),
            entry("shared-lottery", "lee", EntryStatus.WAITING),
            entry("sams-two-sites", "kim", EntryStatus.WAITING),
        ),
    )
    private val notifications = FakeNotificationRepository()
    private val reservations = FakeReservationRepository(campsites, lotteries, notifications)

    private val service = serviceWith()

    private fun serviceWith(
        profileRepository: ProfileRepository = profiles,
        lotteryRepository: LotteryRepository = lotteries,
    ) = DeletionService(
        profileRepository, campsites, lotteryRepository, reservations, images,
        NotificationService(notifications, campsites, profileRepository),
    )

    private lateinit var samsBooking: String
    private lateinit var kimsBooking: String
    private lateinit var leesBooking: String
    private lateinit var samsOldBooking: String

    private fun entry(lotteryId: String, userId: String, status: EntryStatus) = LotteryEntry(
        id = LotteryEntry.idFor(lotteryId, userId),
        lotteryId = lotteryId,
        userId = userId,
        status = status,
    )

    private fun booking(userId: String, ownerId: String, campsiteId: String) = Reservation(
        userId = userId,
        ownerId = ownerId,
        campsiteId = campsiteId,
        campsiteName = campsiteId,
        checkIn = "2027-06-01",
        checkOut = "2027-06-03",
    )

    @Before
    fun setUp() = runBlocking {
        images.addImage(
            ImageAsset.fromJpeg(
                FakePhotoReader.SAMPLE_JPEG, "sam", ImageAsset.OWNER_PROFILE, "sam", "Sam Patel",
                now,
            ).copy(id = "sam-photo"),
        )
        images.addImage(
            ImageAsset.fromJpeg(
                FakePhotoReader.SAMPLE_JPEG, "sam", ImageAsset.OWNER_CAMPSITE, "sams-site",
                "Sam's Site", now,
            ).copy(id = "site-photo"),
        )
        // Sam booked at Alex's campsite; Kim and Lee booked at Sam's
        samsBooking = reservations.addReservation(booking("sam", "alex", "alex-site"))
        kimsBooking = reservations.addReservation(booking("kim", "sam", "sams-site"))
        leesBooking = reservations.addReservation(booking("lee", "sam", "sams-site"))
        samsOldBooking = reservations.addReservation(
            booking("sam", "alex", "alex-site").copy(status = ReservationStatus.CANCELLED),
        )
        val held = listOf("2027-06-01", "2027-06-02")
        campsites.saveAvailability(AvailabilityCalendar("alex-site", held, held))
        campsites.saveAvailability(AvailabilityCalendar("sams-site", held, held))
    }

    private suspend fun statusOf(lotteryId: String, userId: String) =
        lotteries.getEntry(lotteryId, userId)?.status

    @Test
    fun deleteProfile_deletesTheProfileAndItsPhoto() = runBlocking {
        service.deleteProfile("sam", now)

        assertNull(profiles.getProfile("sam"))
        assertTrue("sam-photo" in images.deletedIds)
        assertNull(images.getImage("sam-photo"))
        // everyone else is still there
        assertNotNull(profiles.getProfile("alex"))
    }

    @Test
    fun deleteProfile_ownersSeeDeletedUserInsteadOfTheName() = runBlocking {
        service.deleteProfile("sam", now)

        assertEquals("Deleted user", profiles.getDisplayName("sam"))
        assertEquals("Alex Kim", profiles.getDisplayName("alex"))
    }

    @Test
    fun deleteProfile_marksTheirEntriesRemovedAndTakesThemOffTheWaitingList() = runBlocking {
        service.deleteProfile("sam", now)

        assertEquals(EntryStatus.REMOVED, statusOf("alex-lottery", "sam"))
        assertEquals(listOf("kim"), lotteries.getLottery("alex-lottery")!!.waitingUserIds)
        // a finished entry is left as it was
        assertEquals(EntryStatus.DECLINED, statusOf("old-lottery", "sam"))
        // other entrants keep their place
        assertEquals(EntryStatus.WAITING, statusOf("alex-lottery", "kim"))
    }

    @Test
    fun deleteProfile_cancelsTheirReservationsAndGivesTheNightsBack() = runBlocking {
        service.deleteProfile("sam", now)

        val cancelled = reservations.getReservation(samsBooking)!!
        assertEquals(ReservationStatus.CANCELLED, cancelled.status)
        assertEquals(now, cancelled.statusChangedAt)
        assertTrue(campsites.getAvailability("alex-site").heldNights.isEmpty())
        // they're gone, so no notice is written for them
        assertTrue(notifications.getNotificationsFor("sam").isEmpty())
        // an old cancelled one isn't touched again
        assertEquals(0, reservations.getReservation(samsOldBooking)!!.statusChangedAt)
    }

    @Test
    fun deleteProfile_removesTheirCampsites() = runBlocking {
        service.deleteProfile("sam", now)

        assertTrue(campsites.getCampsite("sams-site")!!.removed)
        assertTrue(campsites.getCampsite("sams-lake")!!.removed)
        assertTrue(campsites.getCampsitesByOwner("sam").isEmpty())
        assertEquals(listOf("alex-site"), campsites.getAllCampsites().map { it.id })
        assertFalse(campsites.getCampsite("alex-site")!!.removed)
        // and their photos
        assertNull(images.getImage("site-photo"))
    }

    @Test
    fun deleteProfile_cancelsReservationsAtTheirCampsitesAndTellsEntrantsWhoWantNotices() =
        runBlocking {
            service.deleteProfile("sam", now)

            val kims = reservations.getReservation(kimsBooking)!!
            val lees = reservations.getReservation(leesBooking)!!
            assertEquals(ReservationStatus.CANCELLED, kims.status)
            assertEquals(ReservationStatus.CANCELLED, lees.status)
            assertTrue(campsites.getAvailability("sams-site").heldNights.isEmpty())
            val kimsNotices = notifications.getNotificationsFor("kim")
            assertEquals(listOf("Reservation cancelled"), kimsNotices.map { it.title })
            // Lee turned notifications off
            assertTrue(notifications.getNotificationsFor("lee").isEmpty())
        }

    @Test
    fun deleteProfile_takesEveryoneOffTheirLotteriesButKeepsOtherOwnersLotteries() =
        runBlocking {
            service.deleteProfile("sam", now)

            assertEquals(EntryStatus.REMOVED, statusOf("sams-lottery", "lee"))
            assertEquals(EntryStatus.REMOVED, statusOf("sams-lottery", "kim"))
            assertEquals(EntryStatus.REMOVED, statusOf("sams-two-sites", "kim"))
            assertTrue(lotteries.getLottery("sams-lottery")!!.waitingUserIds.isEmpty())
            // removed, so they're never drawn and show as closed
            assertTrue(lotteries.getLottery("sams-lottery")!!.removed)
            assertTrue(lotteries.getLottery("sams-two-sites")!!.removed)
            // Alex's lottery still has Alex's own campsite in it
            assertEquals(EntryStatus.WAITING, statusOf("shared-lottery", "lee"))
            assertFalse(lotteries.getLottery("shared-lottery")!!.removed)
        }

    @Test
    fun deleteProfile_whenAStepFails_keepsTheProfileAndTryingAgainFinishes() = runBlocking {
        var failOnce = true
        val flaky = object : LotteryRepository by lotteries {
            override suspend fun removeEntry(lotteryId: String, userId: String): Boolean {
                if (failOnce) {
                    failOnce = false
                    throw IOException("offline")
                }
                return lotteries.removeEntry(lotteryId, userId)
            }
        }
        val flakyService = serviceWith(lotteryRepository = flaky)

        try {
            flakyService.deleteProfile("sam", now)
            fail("expected the failure to be thrown")
        } catch (e: IOException) {
            // expected
        }
        assertNotNull(profiles.getProfile("sam"))
        assertTrue(images.deletedIds.isEmpty())

        flakyService.deleteProfile("sam", now)

        assertNull(profiles.getProfile("sam"))
        assertEquals(EntryStatus.REMOVED, statusOf("alex-lottery", "sam"))
        assertTrue(campsites.getCampsite("sams-site")!!.removed)
    }

    @Test
    fun deleteProfile_withNoProfile_changesNothing() = runBlocking {
        service.deleteProfile("nobody", now)

        assertEquals(4, profiles.getAllProfiles().size)
        assertTrue(campsites.getCampsitesByOwner("sam").isNotEmpty())
        assertEquals(EntryStatus.WAITING, statusOf("alex-lottery", "sam"))
        assertTrue(images.deletedIds.isEmpty())
    }

    @Test
    fun removeCampsite_onItsOwn_leavesTheOwnersProfile() = runBlocking {
        service.removeCampsite(campsites.getCampsite("sams-site")!!, now)

        assertTrue(campsites.getCampsite("sams-site")!!.removed)
        assertNull(images.getImage("site-photo"))
        assertEquals(EntryStatus.REMOVED, statusOf("sams-lottery", "lee"))
        assertTrue(lotteries.getLottery("sams-lottery")!!.removed)
        assertNotNull(profiles.getProfile("sam"))
        // Sam's own booking at Alex's campsite isn't touched
        assertEquals(ReservationStatus.CONFIRMED, reservations.getReservation(samsBooking)!!.status)
    }

    @Test
    fun removeCampsite_keepsALotteryThatStillHasAnotherCampsite() = runBlocking {
        service.removeCampsite(campsites.getCampsite("sams-site")!!, now)

        // Sam's Lake is still there, so its lottery stays open
        assertFalse(lotteries.getLottery("sams-two-sites")!!.removed)
        assertEquals(EntryStatus.WAITING, statusOf("sams-two-sites", "kim"))

        service.removeCampsite(campsites.getCampsite("sams-lake")!!, now)

        assertTrue(lotteries.getLottery("sams-two-sites")!!.removed)
        assertEquals(EntryStatus.REMOVED, statusOf("sams-two-sites", "kim"))
    }

    @Test
    fun deleteProfile_whenAGuestsProfileCantBeRead_stillFinishesAndTellsThem() = runBlocking {
        val kimUnreadable = object : ProfileRepository by profiles {
            override suspend fun getProfile(id: String): UserProfile? =
                if (id == "kim") throw IOException("offline") else profiles.getProfile(id)
        }

        serviceWith(profileRepository = kimUnreadable).deleteProfile("sam", now)

        assertNull(profiles.getProfile("sam"))
        assertEquals(ReservationStatus.CANCELLED, reservations.getReservation(kimsBooking)!!.status)
        // like every other notice, a profile that can't be read still gets it
        assertEquals(1, notifications.getNotificationsFor("kim").size)
    }
}
