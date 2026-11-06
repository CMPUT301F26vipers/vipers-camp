/*
 * AvailabilityControllerTest.kt
 * Unit tests for AvailabilityController (T-10): loading a campsite's calendar, opening and closing
 * nights one at a time or as a date range, and saving, using the fake campsite repository.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.repository.CampsiteRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AvailabilityControllerTest {

    // the tests run as if today is April 28, 2027
    private val today = LocalDate.parse("2027-04-28")

    // the first May weekend is open, and a reservation holds the Saturday
    private val saved = AvailabilityCalendar(
        campsiteId = "hinton",
        openNights = listOf("2027-05-01", "2027-05-02"),
        heldNights = listOf("2027-05-01"),
    )

    private fun loadedController(
        repository: CampsiteRepository = repositoryWith(saved),
    ): AvailabilityController = runBlocking {
        val controller = AvailabilityController(repository, "hinton")
        controller.fetchData(today)
        controller
    }

    private fun repositoryWith(calendar: AvailabilityCalendar) = runBlocking {
        FakeCampsiteRepository().also { it.saveAvailability(calendar) }
    }

    @Test
    fun fetchData_loadsTheCalendarAndShowsThisMonth() {
        val controller = loadedController()
        assertEquals(saved, controller.calendar)
        assertEquals(YearMonth.of(2027, 4), controller.month)
        assertFalse(controller.loading)
        assertNull(controller.error)
    }

    @Test
    fun fetchData_givesAnEmptyCalendarForANewCampsite() {
        val controller = runBlocking {
            AvailabilityController(FakeCampsiteRepository(), "new").also { it.fetchData(today) }
        }
        assertEquals(emptyList<String>(), controller.calendar?.openNights)
    }

    @Test
    fun showNextMonth_andShowPreviousMonth_moveOneMonth() {
        val controller = loadedController()
        controller.showNextMonth()
        assertEquals(YearMonth.of(2027, 5), controller.month)
        controller.showPreviousMonth()
        controller.showPreviousMonth()
        assertEquals(YearMonth.of(2027, 3), controller.month)
    }

    @Test
    fun onNightTap_opensAndClosesANight() {
        val controller = loadedController()
        controller.onNightTap("2027-05-08", today)
        assertTrue(controller.calendar!!.isOpen("2027-05-08"))
        controller.onNightTap("2027-05-08", today)
        assertFalse(controller.calendar!!.isOpen("2027-05-08"))
    }

    @Test
    fun onNightTap_explainsThatABookedNightCantBeClosed() {
        val controller = loadedController()
        controller.onNightTap("2027-05-01", today)
        assertTrue(controller.calendar!!.isOpen("2027-05-01"))
        assertEquals(
            "This night is booked. Reject the reservation first to close it.",
            controller.message,
        )
    }

    @Test
    fun onNightTap_explainsThatPastNightsCantChange() {
        val controller = loadedController()
        controller.onNightTap("2027-04-20", today)
        assertFalse(controller.calendar!!.isOpen("2027-04-20"))
        assertEquals("Past nights can't be changed.", controller.message)
    }

    @Test
    fun dateRange_opensEveryNightBetweenTheTwoTaps() {
        val controller = loadedController()
        controller.startRange()
        assertTrue(controller.pickingRange)
        controller.onNightTap("2027-05-08", today)
        assertEquals("2027-05-08", controller.rangeStart)
        controller.onNightTap("2027-05-10", today)
        assertTrue(controller.calendar!!.isOpen("2027-05-08"))
        assertTrue(controller.calendar!!.isOpen("2027-05-09"))
        assertTrue(controller.calendar!!.isOpen("2027-05-10"))
        // the range is done, so the next tap toggles one night again
        assertFalse(controller.pickingRange)
        assertNull(controller.rangeStart)
    }

    @Test
    fun cancelRange_changesNothing() {
        val controller = loadedController()
        controller.startRange()
        controller.onNightTap("2027-05-08", today)
        controller.cancelRange()
        assertFalse(controller.pickingRange)
        assertEquals(saved, controller.calendar)
    }

    @Test
    fun saveCalendar_savesTheOpenNights() = runBlocking {
        val repository = repositoryWith(saved)
        val controller = loadedController(repository)
        controller.onNightTap("2027-05-08", today)
        controller.saveCalendar()
        assertEquals(
            listOf("2027-05-01", "2027-05-02", "2027-05-08"),
            repository.getAvailability("hinton").openNights,
        )
        assertEquals("Availability saved", controller.message)
        assertFalse(controller.saving)
    }

    @Test
    fun saveCalendar_keepsABookingMadeWhileEditing() = runBlocking {
        val repository = repositoryWith(saved)
        val controller = loadedController(repository)
        controller.onNightTap("2027-05-08", today)
        // someone reserves Sunday the 2nd before the owner taps Save
        repository.saveAvailability(saved.copy(heldNights = listOf("2027-05-01", "2027-05-02")))
        controller.saveCalendar()
        val result = repository.getAvailability("hinton")
        assertEquals(listOf("2027-05-01", "2027-05-02"), result.heldNights)
        assertEquals(listOf("2027-05-01", "2027-05-02", "2027-05-08"), result.openNights)
    }

    @Test
    fun saveCalendar_saysSoWhenSavingFails() = runBlocking {
        // a repository that can't save, like Firestore when the phone goes offline
        val failing = object : CampsiteRepository by repositoryWith(saved) {
            override suspend fun saveAvailability(calendar: AvailabilityCalendar) =
                throw Exception("offline")
        }
        val controller = loadedController(failing)
        controller.saveCalendar()
        assertEquals("Couldn't save. Check your connection and try again.", controller.message)
        assertFalse(controller.saving)
    }

    @Test
    fun fetchData_setsErrorWhenTheRepositoryFails() = runBlocking {
        val failing = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                throw Exception("offline")
        }
        val controller = AvailabilityController(failing, "hinton")
        controller.fetchData(today)
        assertEquals("Unable to load availability.", controller.error)
        assertFalse(controller.loading)
    }

    @Test
    fun fetchData_doesNotSetErrorWhenCancelled() = runBlocking {
        // a repository that never answers, so the load is still running when it's cancelled
        val slow = object : CampsiteRepository by FakeCampsiteRepository() {
            override suspend fun getAvailability(campsiteId: String): AvailabilityCalendar =
                awaitCancellation()
        }
        val controller = AvailabilityController(slow, "hinton")
        val load = launch { controller.fetchData(today) }
        yield() // let the load start
        load.cancelAndJoin()
        assertNull(controller.error)
        assertFalse(controller.loading)
    }
}
