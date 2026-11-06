/*
 * LotteryValidationTest.kt
 * Unit tests for validateLottery logic.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryValidationTest {

    private val now = 1000L

    @Test
    fun validateLottery_returnsNoErrorsForValidLottery() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2000L,
            registrationEnd = 3000L,
            bookingStart = 4000L,
            bookingEnd = 5000L,
            winnersCount = 2,
            waitlistCapacity = 10,
        )
        val errors = validateLottery(lottery, now = now)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_permitsEqualBookingStartAndRegistrationEnd() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2000L,
            registrationEnd = 3000L,
            bookingStart = 3000L, // Equal to registrationEnd
            bookingEnd = 5000L,
            winnersCount = 1,
            waitlistCapacity = null,
        )
        val errors = validateLottery(lottery, now = now)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_rejectsMalformedOrNonIsoDatesInNightsOffered() {
        // Descriptive string
        val descLottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("May 2027 weekends"),
            registrationStart = 2000L,
            registrationEnd = 3000L,
            bookingStart = 4000L,
            bookingEnd = 5000L,
        )
        val descErrors = validateLottery(descLottery, now = now)
        assertTrue(descErrors.contains("Select at least one night"))

        // Lenient invalid date
        val invalidDateLottery = descLottery.copy(nightsOffered = listOf("2027-02-30"))
        val dateErrors = validateLottery(invalidDateLottery, now = now)
        assertTrue(dateErrors.contains("Select at least one night"))
    }

    @Test
    fun validateLottery_reportsErrorForZeroTimestamps() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 0L,
            registrationEnd = 0L,
            bookingStart = 0L,
            bookingEnd = 0L,
            winnersCount = 2,
        )
        val errors = validateLottery(lottery, now = now)
        assertEquals(2, errors.size)
        assertTrue(errors.contains("Both registration start and end times must be set"))
        assertTrue(errors.contains("Both booking start and end times must be set"))
    }

    @Test
    fun validateLottery_reportsErrorForPartiallyConfiguredPeriods() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2000L,
            registrationEnd = 0L, // missing end
            bookingStart = 4000L,
            bookingEnd = 5000L,
            winnersCount = 1,
        )
        val errors = validateLottery(lottery, now = now)
        assertEquals(1, errors.size)
        assertTrue(errors.contains("Both registration start and end times must be set"))
    }

    @Test
    fun validateLottery_reportsMissingFieldsAndInvalidPeriods() {
        val lottery = Lottery(
            ownerId = "",
            campsiteIds = emptyList(),
            nightsOffered = emptyList(),
            registrationStart = 500L,
            registrationEnd = 400L,
            bookingStart = 300L,
            bookingEnd = 200L,
            winnersCount = 0,
            waitlistCapacity = 0,
        )
        val errors = validateLottery(lottery, now = now)
        assertEquals(9, errors.size)
        assertTrue(errors.contains("Owner is required"))
        assertTrue(errors.contains("Select a campsite"))
        assertTrue(errors.contains("Select at least one night"))
        assertTrue(errors.contains("Registration cannot start in the past"))
        assertTrue(errors.contains("Invalid registration period"))
        assertTrue(errors.contains("Booking must start after registration"))
        assertTrue(errors.contains("Invalid booking period"))
        assertTrue(errors.contains("At least one winner is required"))
        assertTrue(errors.contains("Waitlist limit must be at least 1"))
    }

    @Test
    fun validateLottery_allowsRegistrationStartAtCurrentTime() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = now,
            registrationEnd = now + 1000L,
            bookingStart = now + 1000L,
            bookingEnd = now + 2000L,
            winnersCount = 1,
        )
        val errors = validateLottery(lottery, now = now)
        assertTrue(errors.isEmpty())
    }
}
