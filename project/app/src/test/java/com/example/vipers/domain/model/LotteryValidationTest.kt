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
        )
        val errors = validateLottery(lottery, now = now)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_returnsNoErrorsForZeroTimestamps() {
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
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_reportsErrorForPartiallyConfiguredPeriods() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2000L,
            registrationEnd = 0L, // missing end
            bookingStart = 0L,
            bookingEnd = 0L,
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
        )
        val errors = validateLottery(lottery, now = now)
        assertEquals(8, errors.size)
        assertTrue(errors.contains("Owner is required"))
        assertTrue(errors.contains("Select a campsite"))
        assertTrue(errors.contains("Select at least one night"))
        assertTrue(errors.contains("Registration cannot start in the past"))
        assertTrue(errors.contains("Invalid registration period"))
        assertTrue(errors.contains("Booking must start after registration"))
        assertTrue(errors.contains("Invalid booking period"))
        assertTrue(errors.contains("At least one winner is required"))
    }
}
