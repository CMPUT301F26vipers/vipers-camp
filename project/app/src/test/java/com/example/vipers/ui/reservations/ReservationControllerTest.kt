/*
 * ReservationControllerTest.kt
 * Unit tests for ReservationController (US 02.07.01), using the fake repositories.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReservationControllerTest {

    private val today = LocalDate.of(2027, 5, 10)
    private val profiles = FakeProfileRepository(listOf(UserProfile(id = "sam", name = "Sam Patel")))

    @Before
    fun setUp() {
        // run viewModelScope work straight away in tests
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        ownerId = "owner-1",
        today = { today },
    )

    private fun ReservationController.loadedIds(): List<String> {
        loadOwnerActive()
        return activeRows.map { it.reservationId }
    }

    @Test
    fun loadOwnerActive_withValidReservation_fillsTheRowAndFinishesLoading() {
        val controller = controllerWith(reservation("r1"))
        controller.loadOwnerActive()

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
    fun loadOwnerActive_withCheckoutYesterdayAndToday_keepsOnlyToday() {
        val controller = controllerWith(
            reservation("yesterday").copy(checkIn = "2027-05-07", checkOut = "2027-05-09"),
            reservation("today").copy(checkIn = "2027-05-08", checkOut = "2027-05-10"),
        )

        assertEquals(listOf("today"), controller.loadedIds())
    }

    @Test
    fun loadOwnerActive_withCancelledAndRejected_excludesThem() {
        val controller = controllerWith(
            reservation("confirmed"),
            reservation("cancelled").copy(status = ReservationStatus.CANCELLED),
            reservation("rejected").copy(status = ReservationStatus.REJECTED),
        )

        assertEquals(listOf("confirmed"), controller.loadedIds())
    }

    @Test
    fun loadOwnerActive_withAnotherOwnersReservation_excludesIt() {
        val controller = controllerWith(
            reservation("mine"),
            reservation("theirs").copy(ownerId = "owner-2"),
        )

        assertEquals(listOf("mine"), controller.loadedIds())
    }

    @Test
    fun loadOwnerActive_afterEntrantIsRenamed_showsTheNewName() = runBlocking {
        val controller = controllerWith(reservation("r1"))
        controller.loadOwnerActive()
        assertEquals("Sam Patel", controller.activeRows.single().entrantName)

        profiles.saveProfile(UserProfile(id = "sam", name = "Samira Patel"))
        controller.loadOwnerActive()

        assertEquals("Samira Patel", controller.activeRows.single().entrantName)
    }

    @Test
    fun loadOwnerActive_withMissingProfile_showsDeletedUser() {
        val controller = controllerWith(reservation("r1").copy(userId = "gone"))
        controller.loadOwnerActive()

        assertEquals("Deleted user", controller.activeRows.single().entrantName)
    }

    @Test
    fun loadOwnerActive_withUnsortedReservations_sortsByCheckInSoonestFirst() {
        val controller = controllerWith(
            reservation("june").copy(checkIn = "2027-06-01", checkOut = "2027-06-03"),
            reservation("may").copy(checkIn = "2027-05-15", checkOut = "2027-05-17"),
            reservation("july").copy(checkIn = "2027-07-01", checkOut = "2027-07-03"),
        )

        assertEquals(listOf("may", "june", "july"), controller.loadedIds())
    }

    @Test
    fun loadOwnerActive_whenTheRepositoryFails_setsLoadFailed() {
        // a repository that always fails, e.g. Firestore being unreachable
        val failing = object : ReservationRepository by FakeReservationRepository() {
            override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
                throw Exception("offline")
        }
        val controller = ReservationController(failing, profiles, "owner-1") { today }
        controller.loadOwnerActive()

        assertTrue(controller.loadFailed)
        assertFalse(controller.isLoading)
        assertTrue(controller.activeRows.isEmpty())
    }
}
