/*
 * ReservationTest.kt
 * Unit tests for the Active / Past rule on reservations, for the completed-stay rule the owner's
 * Past tab uses (US 02.07.02), for which ones can be cancelled (US 01.07.01), for cancellation
 * confirmation and notification (US 01.07.02), and for which ones can be rejected and with what
 * reason (US 02.08.01).
 * Known issues: none.
 */
package com.example.vipers.domain.model

import com.example.vipers.ui.reservations.formatCancelledDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationTest {

    private val today = LocalDate.parse("2027-05-09")
    private val mayEightToTen = Reservation(
        campsiteName = "Bow River Sites",
        checkIn = "2027-05-08",
        checkOut = "2027-05-10",
        groupSize = 4,
        status = ReservationStatus.CONFIRMED,
    )

    private val samsStay = mayEightToTen.copy(userId = "sam", ownerId = "priya")

    @Test
    fun canMessageOwner_whenEntrantsStayIsConfirmed() {
        assertTrue(samsStay.canMessageOwner("sam"))
    }

    @Test
    fun canMessageOwner_whenEntrantsStayIsRejected() {
        assertTrue(samsStay.copy(status = ReservationStatus.REJECTED).canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_whenStayIsCancelled() {
        assertFalse(samsStay.copy(status = ReservationStatus.CANCELLED).canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_asAnotherEntrant() {
        assertFalse(samsStay.canMessageOwner("another-entrant"))
    }

    @Test
    fun cannotMessageOwner_asTheOwner() {
        assertFalse(samsStay.canMessageOwner("priya"))
    }

    @Test
    fun cannotMessageOwner_whenEntrantBookedTheirOwnCampsite() {
        assertFalse(samsStay.copy(ownerId = "sam").canMessageOwner("sam"))
    }

    @Test
    fun cannotMessageOwner_whenOwnerIdIsBlank() {
        assertFalse(samsStay.copy(ownerId = "").canMessageOwner("sam"))
    }

    @Test
    fun isActive_whenConfirmedAndNotCheckedOut() {
        assertTrue(mayEightToTen.isActive(today))
    }

    @Test
    fun isActive_onCheckOutDay() {
        assertTrue(mayEightToTen.isActive(LocalDate.parse("2027-05-10")))
    }

    @Test
    fun isPast_afterCheckOut() {
        assertFalse(mayEightToTen.isActive(LocalDate.parse("2027-05-11")))
    }

    @Test
    fun isPast_whenCancelledOrRejected() {
        assertFalse(mayEightToTen.copy(status = ReservationStatus.CANCELLED).isActive(today))
        assertFalse(mayEightToTen.copy(status = ReservationStatus.REJECTED).isActive(today))
    }

    @Test
    fun isCompleted_afterCheckOut() {
        assertTrue(mayEightToTen.isCompleted(LocalDate.parse("2027-05-11")))
    }

    @Test
    fun isNotCompleted_onCheckOutDay() {
        assertFalse(mayEightToTen.isCompleted(LocalDate.parse("2027-05-10")))
    }

    @Test
    fun isNotCompleted_duringTheStayOrBeforeCheckIn() {
        assertFalse(mayEightToTen.isCompleted(today))
        assertFalse(mayEightToTen.isCompleted(LocalDate.parse("2027-05-01")))
    }

    @Test
    fun isNotCompleted_whenCancelledOrRejected() {
        val afterCheckOut = LocalDate.parse("2027-05-11")
        val cancelled = mayEightToTen.copy(status = ReservationStatus.CANCELLED)
        val rejected = mayEightToTen.copy(status = ReservationStatus.REJECTED)
        assertFalse(cancelled.isCompleted(afterCheckOut))
        assertFalse(rejected.isCompleted(afterCheckOut))
    }

    @Test
    fun isCompletedAndIsActive_areNeverBothTrue() {
        for (status in ReservationStatus.entries) {
            val reservation = mayEightToTen.copy(status = status)
            var day = LocalDate.parse("2027-05-01")
            while (!day.isAfter(LocalDate.parse("2027-05-20"))) {
                assertFalse(
                    "$status on $day",
                    reservation.isCompleted(day) && reservation.isActive(day),
                )
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun canCancel_beforeCheckIn() {
        assertTrue(mayEightToTen.canCancel(LocalDate.parse("2027-05-01")))
    }

    @Test
    fun canCancel_onCheckInDay() {
        assertTrue(mayEightToTen.canCancel(LocalDate.parse("2027-05-08")))
    }

    @Test
    fun cannotCancel_afterCheckInHasPassed() {
        assertFalse(mayEightToTen.canCancel(today))
    }

    @Test
    fun cannotCancel_whenAlreadyCancelledOrRejected() {
        val early = LocalDate.parse("2027-05-01")
        assertFalse(mayEightToTen.copy(status = ReservationStatus.CANCELLED).canCancel(early))
        assertFalse(mayEightToTen.copy(status = ReservationStatus.REJECTED).canCancel(early))
    }

    @Test
    fun cannotCancel_whenCheckInCantBeRead() {
        assertFalse(mayEightToTen.copy(checkIn = "").canCancel(today))
    }

    @Test
    fun canReject_whenConfirmedAndCheckOutIsAhead() {
        assertTrue(mayEightToTen.canReject(today))
    }

    @Test
    fun canReject_onCheckOutDay() {
        assertTrue(mayEightToTen.canReject(LocalDate.parse("2027-05-10")))
    }

    @Test
    fun cannotReject_afterCheckOut() {
        assertFalse(mayEightToTen.canReject(LocalDate.parse("2027-05-11")))
    }

    @Test
    fun cannotReject_whenCancelled() {
        assertFalse(mayEightToTen.copy(status = ReservationStatus.CANCELLED).canReject(today))
    }

    @Test
    fun cannotReject_whenAlreadyRejected() {
        assertFalse(mayEightToTen.copy(status = ReservationStatus.REJECTED).canReject(today))
    }

    @Test
    fun cannotReject_whenCheckOutCantBeRead() {
        assertFalse(mayEightToTen.copy(checkOut = "").canReject(today))
    }

    @Test
    fun rejectionReason_isValidWithText() {
        assertTrue(Reservation.isValidRejectionReason("Forest fire warning"))
    }

    @Test
    fun rejectionReason_isValidWithSpacesAround() {
        assertTrue(Reservation.isValidRejectionReason("  Forest fire warning  "))
    }

    @Test
    fun rejectionReason_isInvalidWhenEmptyOrBlank() {
        assertFalse(Reservation.isValidRejectionReason(""))
        assertFalse(Reservation.isValidRejectionReason("   "))
    }

    @Test
    fun nights_runFromCheckInToTheNightBeforeCheckOut() {
        assertEquals(listOf("2027-05-08", "2027-05-09"), mayEightToTen.nights())
    }

    @Test
    fun statusNotice_forCancelledReservation_createsCancellationNotification() {
        val cancelled = mayEightToTen.copy(
            id = "res-101",
            userId = "sam",
            campsiteId = "site-bow",
            campsiteName = "Bow River Sites",
            checkIn = "2027-05-08",
            checkOut = "2027-05-10",
            status = ReservationStatus.CANCELLED,
        )
        val notice = cancelled.statusNotice(1_700_000_000_000L)

        assertEquals("cancelled_res-101", notice.id)
        assertEquals("sam", notice.recipientId)
        assertEquals("system", notice.senderId)
        assertEquals("site-bow", notice.campsiteId)
        assertEquals("res-101", notice.reservationId)
        assertEquals(NotificationType.RESERVATION_CANCELLED, notice.type)
        assertEquals("Reservation cancelled", notice.title)
        assertEquals("Bow River Sites: 2027-05-08 to 2027-05-10", notice.message)
        assertEquals(1_700_000_000_000L, notice.createdAt)
    }

    @Test
    fun formatCancelledDate_formatsEpochMillisToDateString() {
        val zone = ZoneId.of("UTC")
        // 1700000000000 ms is 2023-11-14T22:13:20Z
        val formatted = formatCancelledDate(1_700_000_000_000L, zone)
        assertEquals("Nov 14, 2023", formatted)
    }

    @Test
    fun formatCancelledDate_fallsBackToTodayWhenZeroOrNegative() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val expected = today.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        assertEquals(expected, formatCancelledDate(0L, zone))
        assertEquals(expected, formatCancelledDate(-1L, zone))
    }
}
