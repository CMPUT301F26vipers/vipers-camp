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

    private fun openLottery(
        waitlistCapacity: Int? = 10,
        removed: Boolean = false,
    ) = Lottery(
        id = "lottery-1",
        ownerId = "owner-1",
        campsiteIds = listOf("camp-1"),
        nightsOffered = listOf("2027-06-01"),
        registrationStart = 1000L,
        registrationEnd = 3000L,
        bookingStart = 4000L,
        bookingEnd = 5000L,
        winnersCount = 2,
        waitlistCapacity = waitlistCapacity,
        removed = removed,
    )

    @Test
    fun validateJoinLottery_returnsNoErrorsForValidJoin() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            existingEntry = null,
            waitingCount = 5,
            groupSize = 2,
            now = 2000L
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateJoinLottery_rejectsWhenRegistrationNotStarted() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            now = 500L // before registrationStart (1000L)
        )
        assertTrue(errors.contains("Registration has not started"))
    }

    @Test
    fun validateJoinLottery_rejectsWhenRegistrationClosed() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            now = 3500L // after registrationEnd (3000L)
        )
        assertTrue(errors.contains("Registration is closed"))
    }

    @Test
    fun validateJoinLottery_rejectsWhenLotteryRemoved() {
        val lottery = openLottery(removed = true)
        val errors = validateJoinLottery(
            lottery = lottery,
            now = 2000L
        )
        assertTrue(errors.contains("Lottery has been removed"))
    }

    @Test
    fun validateJoinLottery_allowsJoinWithUnlimitedCapacity() {
        val lottery = openLottery(waitlistCapacity = null)
        val errors = validateJoinLottery(
            lottery = lottery,
            waitingCount = 500,
            now = 2000L
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateJoinLottery_allowsJoinWhenCapacityAvailable() {
        val lottery = openLottery(waitlistCapacity = 10)
        val errors = validateJoinLottery(
            lottery = lottery,
            waitingCount = 9,
            now = 2000L
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateJoinLottery_rejectsWhenWaitingListAtCapacity() {
        val lottery = openLottery(waitlistCapacity = 10)

        val atCapacityErrors = validateJoinLottery(
            lottery = lottery,
            waitingCount = 10,
            now = 2000L
        )
        assertTrue(atCapacityErrors.contains("Waiting list is full"))

        val overCapacityErrors = validateJoinLottery(
            lottery = lottery,
            waitingCount = 11,
            now = 2000L
        )
        assertTrue(overCapacityErrors.contains("Waiting list is full"))
    }

    @Test
    fun validateJoinLottery_rejectsDuplicateWhenAlreadyOnWaitingList() {
        val lottery = openLottery()
        val existingEntry = LotteryEntry(
            id = "lottery-1_user-1",
            lotteryId = "lottery-1",
            userId = "user-1",
            status = EntryStatus.WAITING
        )
        val errors = validateJoinLottery(
            lottery = lottery,
            existingEntry = existingEntry,
            now = 2000L
        )
        assertTrue(errors.contains("Already on the waiting list"))
    }

    @Test
    fun validateJoinLottery_rejectsWhenExistingEntryHasNonWaitingStatus() {
        val lottery = openLottery()
        val existingEntry = LotteryEntry(
            id = "lottery-1_user-1",
            lotteryId = "lottery-1",
            userId = "user-1",
            status = EntryStatus.REMOVED
        )
        val errors = validateJoinLottery(
            lottery = lottery,
            existingEntry = existingEntry,
            now = 2000L
        )
        assertTrue(errors.contains("Cannot join with existing REMOVED entry"))
    }

    @Test
    fun validateJoinLottery_rejectsInvalidGroupSize() {
        val lottery = openLottery()

        val zeroErrors = validateJoinLottery(
            lottery = lottery,
            groupSize = 0,
            now = 2000L
        )
        assertTrue(zeroErrors.contains("Group size must be at least 1"))

        val negativeErrors = validateJoinLottery(
            lottery = lottery,
            groupSize = -3,
            now = 2000L
        )
        assertTrue(negativeErrors.contains("Group size must be at least 1"))
    }
}
