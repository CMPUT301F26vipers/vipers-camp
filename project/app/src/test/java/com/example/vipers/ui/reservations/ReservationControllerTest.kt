/*
 * ReservationControllerTest.kt
 * Unit tests for ReservationController: the entrant's My Reservations (US 01.06.01) and the owner's
 * Active list (US 02.07.01) and Past list (US 02.07.02), using the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationControllerTest {

    private val today = LocalDate.of(2027, 5, 10)
    private val profiles = FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel")))

    /** A confirmed reservation by Sam at owner-1's campsite, staying well past [today]. */
    private fun reservation(id: String) = Reservation(
        id = id,
        userId = "sam",
        ownerId = "owner-1",
        campsiteName = "Hinton Ridge Campground",
        checkIn = "2027-05-20",
        checkOut = "2027-05-22",
        groupSize = 4,
    )

    private fun controllerWith(vararg reservations: Reservation) = ReservationController(
        reservationRepository = FakeReservationRepository(reservations.toList()),
        profileRepository = profiles,
        userId = "owner-1",
    )

    /** Loads on [today] and returns the IDs of the rows, in order. */
    private fun ReservationController.loadedIds(): List<String> {
        runBlocking { fetchOwnerReservations(today) }
        return activeRows.map { it.reservationId }
    }

    private fun ReservationController.pastIds(): List<String> {
        runBlocking { fetchOwnerReservations(today) }
        return pastRows.map { it.reservationId }
    }

    private fun pastReservation(id: String) =
        reservation(id).copy(checkIn = "2027-04-20", checkOut = "2027-04-22")

    /** The controller as Sam's device sees it, for My Reservations (US 01.06.01). */
    private fun entrantControllerWith(vararg reservations: Reservation) = ReservationController(
        reservationRepository = FakeReservationRepository(reservations.toList()),
        profileRepository = profiles,
        userId = "sam",
    )

    /** Loads Sam's reservations on [today] and returns their IDs, in order. */
    private fun ReservationController.myIds(): List<String> {
        runBlocking { fetchMyReservations(today) }
        return myActive.map { it.id }
    }

    @Test
    fun fetchOwnerReservations_withValidReservation_fillsTheRowAndFinishesLoading() = runBlocking {
        val controller = controllerWith(reservation("r1"))
        controller.fetchOwnerReservations(today)

        assertEquals(
            listOf(
                ReservationRow(
                    reservationId = "r1",
                    entrantName = "Sam Patel",
                    campsiteName = "Hinton Ridge Campground",
                    checkIn = "2027-05-20",
                    checkOut = "2027-05-22",
                    groupSize = 4,
                ),
            ),
            controller.activeRows,
        )
        assertFalse(controller.isLoading)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchOwnerReservations_withCheckoutYesterdayAndToday_keepsOnlyToday() {
        val controller = controllerWith(
            reservation("yesterday").copy(checkIn = "2027-05-07", checkOut = "2027-05-09"),
            reservation("today").copy(checkIn = "2027-05-08", checkOut = "2027-05-10"),
        )

        assertEquals(listOf("today"), controller.loadedIds())
    }

    @Test
    fun fetchOwnerReservations_withCancelledAndRejected_excludesThem() {
        val controller = controllerWith(
            reservation("confirmed"),
            reservation("cancelled").copy(status = ReservationStatus.CANCELLED),
            reservation("rejected").copy(status = ReservationStatus.REJECTED),
        )

        assertEquals(listOf("confirmed"), controller.loadedIds())
    }

    @Test
    fun fetchOwnerReservations_withAnotherOwnersReservation_excludesIt() {
        val controller = controllerWith(
            reservation("mine"),
            reservation("theirs").copy(ownerId = "owner-2"),
        )

        assertEquals(listOf("mine"), controller.loadedIds())
    }

    @Test
    fun fetchOwnerReservations_afterEntrantIsRenamed_showsTheNewName() = runBlocking {
        val controller = controllerWith(reservation("r1"))
        controller.fetchOwnerReservations(today)
        assertEquals("Sam Patel", controller.activeRows.single().entrantName)

        profiles.saveProfile(UserProfile(id = "sam", name = "Samira Patel"))
        controller.fetchOwnerReservations(today)

        assertEquals("Samira Patel", controller.activeRows.single().entrantName)
    }

    @Test
    fun fetchOwnerReservations_withMissingProfile_showsDeletedUser() = runBlocking {
        val controller = controllerWith(reservation("r1").copy(userId = "gone"))
        controller.fetchOwnerReservations(today)

        assertEquals("Deleted user", controller.activeRows.single().entrantName)
    }

    @Test
    fun fetchOwnerReservations_withUnsortedReservations_sortsByCheckInSoonestFirst() {
        val controller = controllerWith(
            reservation("june").copy(checkIn = "2027-06-01", checkOut = "2027-06-03"),
            reservation("may").copy(checkIn = "2027-05-15", checkOut = "2027-05-17"),
            reservation("july").copy(checkIn = "2027-07-01", checkOut = "2027-07-03"),
        )

        assertEquals(listOf("may", "june", "july"), controller.loadedIds())
    }

    @Test
    fun fetchOwnerReservations_whenTheRepositoryFails_setsLoadFailed() = runBlocking {
        // a repository that always fails, e.g. Firestore being unreachable
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
                throw Exception("offline")
        }
        val controller = ReservationController(failing, profiles, "owner-1")
        controller.fetchOwnerReservations(today)

        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.activeRows.isEmpty())
    }

    @Test
    fun fetchOwnerReservations_withUnreadableDates_skipsThoseAndShowsTheRest() {
        val controller = controllerWith(
            reservation("good"),
            reservation("badCheckOut").copy(checkOut = "soon"),
            reservation("badCheckIn").copy(checkIn = ""),
        )

        assertEquals(listOf("good"), controller.loadedIds())
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchOwnerReservations_withCompletedStay_fillsThePastRow() = runBlocking {
        val controller = controllerWith(pastReservation("r1"))
        controller.fetchOwnerReservations(today)

        assertEquals(
            listOf(
                ReservationRow(
                    reservationId = "r1",
                    entrantName = "Sam Patel",
                    campsiteName = "Hinton Ridge Campground",
                    checkIn = "2027-04-20",
                    checkOut = "2027-04-22",
                    groupSize = 4,
                ),
            ),
            controller.pastRows,
        )
        assertFalse(controller.isLoading)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchOwnerReservations_withCheckoutYesterdayAndToday_putsOnlyYesterdayInPast() {
        val controller = controllerWith(
            reservation("yesterday").copy(checkIn = "2027-05-07", checkOut = "2027-05-09"),
            reservation("today").copy(checkIn = "2027-05-08", checkOut = "2027-05-10"),
        )

        assertEquals(listOf("yesterday"), controller.pastIds())
        assertEquals(listOf("today"), controller.activeRows.map { it.reservationId })
    }

    @Test
    fun fetchOwnerReservations_withPastCancelledAndRejected_leavesThemOutOfPastAndActive() {
        val controller = controllerWith(
            pastReservation("completed"),
            pastReservation("cancelled").copy(status = ReservationStatus.CANCELLED),
            pastReservation("rejected").copy(status = ReservationStatus.REJECTED),
        )

        assertEquals(listOf("completed"), controller.pastIds())
        assertTrue(controller.activeRows.isEmpty())
    }

    @Test
    fun fetchOwnerReservations_withAnotherOwnersPastReservation_leavesItOutOfPast() {
        val controller = controllerWith(
            pastReservation("mine"),
            pastReservation("theirs").copy(ownerId = "owner-2"),
        )

        assertEquals(listOf("mine"), controller.pastIds())
    }

    @Test
    fun fetchOwnerReservations_withUnsortedPastStays_sortsByCheckOutLatestFirst() {
        val controller = controllerWith(
            reservation("march").copy(checkIn = "2027-03-01", checkOut = "2027-03-03"),
            reservation("april").copy(checkIn = "2027-04-01", checkOut = "2027-04-03"),
            reservation("january").copy(checkIn = "2027-01-01", checkOut = "2027-01-03"),
        )

        assertEquals(listOf("april", "march", "january"), controller.pastIds())
    }

    @Test
    fun fetchOwnerReservations_withSameCheckOut_putsTheLaterCheckInFirst() {
        val controller = controllerWith(
            reservation("longer").copy(checkIn = "2027-04-18", checkOut = "2027-04-22"),
            reservation("shorter").copy(checkIn = "2027-04-20", checkOut = "2027-04-22"),
        )

        assertEquals(listOf("shorter", "longer"), controller.pastIds())
    }

    @Test
    fun fetchOwnerReservations_oneLoadFillsBothListsWithNoReservationInBoth() = runBlocking {
        val controller = controllerWith(
            reservation("upcoming"),
            reservation("checksOutToday").copy(checkIn = "2027-05-08", checkOut = "2027-05-10"),
            reservation("checkedOutYesterday").copy(checkIn = "2027-05-07", checkOut = "2027-05-09"),
            pastReservation("april"),
            pastReservation("cancelled").copy(status = ReservationStatus.CANCELLED),
        )
        controller.fetchOwnerReservations(today)

        val active = controller.activeRows.map { it.reservationId }
        val past = controller.pastRows.map { it.reservationId }
        assertEquals(listOf("checksOutToday", "upcoming"), active)
        assertEquals(listOf("checkedOutYesterday", "april"), past)
        assertTrue(active.intersect(past.toSet()).isEmpty())
    }

    @Test
    fun fetchOwnerReservations_afterEntrantIsRenamed_showsTheNewNameInPast() = runBlocking {
        val controller = controllerWith(pastReservation("r1"))
        controller.fetchOwnerReservations(today)
        assertEquals("Sam Patel", controller.pastRows.single().entrantName)

        profiles.saveProfile(UserProfile(id = "sam", name = "Samira Patel"))
        controller.fetchOwnerReservations(today)

        assertEquals("Samira Patel", controller.pastRows.single().entrantName)
    }

    @Test
    fun fetchOwnerReservations_withMissingProfile_showsDeletedUserInPast() = runBlocking {
        val controller = controllerWith(pastReservation("r1").copy(userId = "gone"))
        controller.fetchOwnerReservations(today)

        assertEquals("Deleted user", controller.pastRows.single().entrantName)
    }

    @Test
    fun fetchOwnerReservations_whenTheRepositoryFails_emptiesBothLists() = runBlocking {
        var offline = false
        val fake = FakeReservationRepository(listOf(reservation("active"), pastReservation("past")))
        val repository = object : ReservationRepository by fake {
            override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
                if (offline) throw Exception("offline") else fake.getReservationsForOwner(ownerId)
        }
        val controller = ReservationController(repository, profiles, "owner-1")
        controller.fetchOwnerReservations(today)
        assertEquals(1, controller.activeRows.size)
        assertEquals(1, controller.pastRows.size)

        offline = true
        controller.fetchOwnerReservations(today)

        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.activeRows.isEmpty())
        assertTrue(controller.pastRows.isEmpty())
    }

    @Test
    fun fetchOwnerReservations_withUnreadableDates_skipsThoseInPastToo() {
        val controller = controllerWith(
            pastReservation("good"),
            pastReservation("badCheckOut").copy(checkOut = "last week"),
            pastReservation("badCheckIn").copy(checkIn = ""),
        )

        assertEquals(listOf("good"), controller.pastIds())
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchMyReservations_withValidReservation_keepsItAndFinishesLoading() = runBlocking {
        val controller = entrantControllerWith(reservation("r1"))
        controller.fetchMyReservations(today)

        assertEquals(listOf(reservation("r1")), controller.myActive)
        assertFalse(controller.isLoading)
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchMyReservations_withCheckoutYesterdayAndToday_keepsOnlyToday() {
        val controller = entrantControllerWith(
            reservation("yesterday").copy(checkIn = "2027-05-07", checkOut = "2027-05-09"),
            reservation("today").copy(checkIn = "2027-05-08", checkOut = "2027-05-10"),
        )

        assertEquals(listOf("today"), controller.myIds())
    }

    @Test
    fun fetchMyReservations_withCancelledAndRejected_excludesThem() {
        val controller = entrantControllerWith(
            reservation("confirmed"),
            reservation("cancelled").copy(status = ReservationStatus.CANCELLED),
            reservation("rejected").copy(status = ReservationStatus.REJECTED),
        )

        assertEquals(listOf("confirmed"), controller.myIds())
    }

    @Test
    fun fetchMyReservations_withAnotherEntrantsReservation_excludesIt() {
        val controller = entrantControllerWith(
            reservation("mine"),
            reservation("theirs").copy(userId = "alex"),
        )

        assertEquals(listOf("mine"), controller.myIds())
    }

    @Test
    fun fetchMyReservations_withUnsortedReservations_sortsByCheckInSoonestFirst() {
        val controller = entrantControllerWith(
            reservation("june").copy(checkIn = "2027-06-01", checkOut = "2027-06-03"),
            reservation("may").copy(checkIn = "2027-05-15", checkOut = "2027-05-17"),
            reservation("july").copy(checkIn = "2027-07-01", checkOut = "2027-07-03"),
        )

        assertEquals(listOf("may", "june", "july"), controller.myIds())
    }

    @Test
    fun fetchMyReservations_withNoReservations_leavesTheListEmpty() {
        val controller = entrantControllerWith()

        assertTrue(controller.myIds().isEmpty())
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchMyReservations_whenTheRepositoryFails_setsLoadFailed() = runBlocking {
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                throw Exception("offline")
        }
        val controller = ReservationController(failing, profiles, "sam")
        controller.fetchMyReservations(today)

        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.myActive.isEmpty())
    }

    @Test
    fun fetchMyReservations_withUnreadableDates_skipsThoseAndShowsTheRest() {
        val controller = entrantControllerWith(
            reservation("good"),
            reservation("badCheckOut").copy(checkOut = "soon"),
            reservation("badCheckIn").copy(checkIn = ""),
        )

        assertEquals(listOf("good"), controller.myIds())
        assertFalse(controller.loadFailed)
    }

    @Test
    fun fetchMyReservations_afterAFailedLoad_clearsLoadFailedWhenItWorks() = runBlocking {
        var offline = true
        val flaky = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                if (offline) throw Exception("offline") else listOf(reservation("r1"))
        }
        val controller = ReservationController(flaky, profiles, "sam")
        controller.fetchMyReservations(today)
        assertTrue(controller.loadFailed)

        offline = false
        controller.fetchMyReservations(today)

        assertFalse(controller.loadFailed)
        assertEquals(listOf("r1"), controller.myActive.map { it.id })
    }

    @Test
    fun fetchMyReservations_whenAReloadFails_clearsTheOldList() = runBlocking {
        var offline = false
        val flaky = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                if (offline) throw Exception("offline") else listOf(reservation("r1"))
        }
        val controller = ReservationController(flaky, profiles, "sam")
        controller.fetchMyReservations(today)
        assertEquals(listOf("r1"), controller.myActive.map { it.id })

        offline = true
        controller.fetchMyReservations(today)

        assertTrue(controller.loadFailed)
        assertTrue(controller.myActive.isEmpty())
    }

    @Test
    fun fetchMyReservations_whenCancelled_rethrowsWithoutSettingLoadFailed() {
        // the controller being closed while it loads cancels the load
        val cancelled = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForUser(userId: String): List<Reservation> =
                throw CancellationException("screen closed")
        }
        val controller = ReservationController(cancelled, profiles, "sam")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.fetchMyReservations(today) }
        }
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
    }

    @Test
    fun fetchOwnerReservations_whenAReloadFails_clearsTheOldRows() = runBlocking {
        var offline = false
        val flaky = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
                if (offline) throw Exception("offline") else listOf(reservation("r1"))
        }
        val controller = ReservationController(flaky, profiles, "owner-1")
        controller.fetchOwnerReservations(today)
        assertEquals(listOf("r1"), controller.activeRows.map { it.reservationId })

        offline = true
        controller.fetchOwnerReservations(today)

        assertTrue(controller.loadFailed)
        assertTrue(controller.activeRows.isEmpty())
    }

    @Test
    fun fetchOwnerReservations_whenCancelled_rethrowsWithoutSettingLoadFailed() {
        val cancelled = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
                throw CancellationException("screen closed")
        }
        val controller = ReservationController(cancelled, profiles, "owner-1")

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.fetchOwnerReservations(today) }
        }
        assertFalse(controller.loadFailed)
        assertFalse(controller.isLoading)
    }

    @Test
    fun fetchOwnerReservations_withRepeatGuests_looksEachNameUpOnce() = runBlocking {
        val lookups = mutableListOf<String>()
        val countingProfiles = object : ProfileRepository by profiles {
            override suspend fun getDisplayName(id: String): String {
                lookups.add(id)
                return profiles.getDisplayName(id)
            }
        }
        val controller = ReservationController(
            FakeReservationRepository(
                listOf(
                    reservation("active"),
                    pastReservation("april"),
                    pastReservation("march").copy(checkIn = "2027-03-01", checkOut = "2027-03-03"),
                    pastReservation("alexs")
                        .copy(userId = "alex", checkIn = "2027-04-10", checkOut = "2027-04-12"),
                ),
            ),
            countingProfiles,
            "owner-1",
        )
        controller.fetchOwnerReservations(today)

        assertEquals(listOf("alex", "sam"), lookups.sorted())
        assertEquals(listOf("Sam Patel"), controller.activeRows.map { it.entrantName })
        assertEquals(
            listOf("Sam Patel", "Deleted user", "Sam Patel"),
            controller.pastRows.map { it.entrantName },
        )
    }
}
