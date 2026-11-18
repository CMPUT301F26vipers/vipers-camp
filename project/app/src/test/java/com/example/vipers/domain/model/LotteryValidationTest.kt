/*
 * LotteryValidationTest.kt
 * Unit tests for validateLottery logic.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            maxOccupancy = 6,
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
        assertTrue(errors.contains("Registration is closed"))
    }

    @Test
    fun validateJoinLottery_allowsJoinWithUnlimitedCapacity() {
        val lottery = openLottery(waitlistCapacity = null)
        val errors = validateJoinLottery(
            lottery = lottery,
            waitingCount = 500,
            maxOccupancy = 6,
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
            maxOccupancy = 6,
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

    @Test
    fun validateJoinLottery_allowsGroupSizeWithinMaxOccupancy() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            groupSize = 6,
            maxOccupancy = 6,
            now = 2000L
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateJoinLottery_rejectsGroupSizeExceedingMaxOccupancy() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            groupSize = 7,
            maxOccupancy = 6,
            now = 2000L
        )
        assertTrue(errors.contains("Group size cannot exceed maximum occupancy of 6"))
    }

    @Test
    fun validateJoinLottery_rejectsWhenMaxOccupancyIsNull() {
        val lottery = openLottery()
        val errors = validateJoinLottery(
            lottery = lottery,
            groupSize = 2,
            maxOccupancy = null,
            now = 2000L
        )
        assertTrue(errors.contains("Campsite occupancy information is unavailable"))
    }

    // --- US 02.04.06 Edit Mode & Offered Nights Tests ---

    private val edmonton = ZoneId.of("America/Edmonton")
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    @Test
    fun validateLottery_creationRejectsOfferedNightsBeforeBookingStartDate() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-05-10", "2027-05-15"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-08T17:00"),
            bookingStart = at("2027-05-12T00:00"), // Booking starts May 12
            bookingEnd = at("2027-05-20T23:59"),
            winnersCount = 1,
        )
        val errors = validateLottery(lottery, now = at("2027-05-01T08:00"), zone = edmonton)
        assertTrue(errors.contains("Offered nights must be on or after booking start"))
    }

    @Test
    fun validateLottery_creationAcceptsOfferedNightsOnOrAfterBookingStartDate() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-05-12", "2027-05-13"), // Exactly on & after May 12
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-08T17:00"),
            bookingStart = at("2027-05-12T14:00"), // Same calendar day (May 12)
            bookingEnd = at("2027-05-20T23:59"),
            winnersCount = 1,
        )
        val errors = validateLottery(lottery, now = at("2027-05-01T08:00"), zone = edmonton)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_editUpcomingAllowsValidUpdates() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-10T09:00"),
            registrationEnd = at("2027-05-15T17:00"),
            bookingStart = at("2027-05-20T09:00"),
            bookingEnd = at("2027-05-30T17:00"),
            winnersCount = 1,
            waitlistCapacity = 10,
        )
        val currentTime = at("2027-05-05T12:00") // Upcoming (before May 10)
        val proposed = stored.copy(
            registrationStart = at("2027-05-08T09:00"), // Still future relative to currentTime
            winnersCount = 3,
            waitlistCapacity = 15,
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = currentTime, zone = edmonton)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_editUpcomingRejectsRegistrationStartInPast() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-10T09:00"),
            registrationEnd = at("2027-05-15T17:00"),
            bookingStart = at("2027-05-20T09:00"),
            bookingEnd = at("2027-05-30T17:00"),
            winnersCount = 1,
        )
        val currentTime = at("2027-05-05T12:00") // Upcoming
        val proposed = stored.copy(
            registrationStart = at("2027-05-01T09:00"), // In the past relative to currentTime
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = currentTime, zone = edmonton)
        assertTrue(errors.contains("Registration cannot start in the past"))
    }

    @Test
    fun validateLottery_editOpenAllowsRegistrationStartInPast() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
            winnersCount = 2,
            waitlistCapacity = 10,
        )
        val currentTime = at("2027-05-05T12:00") // Open!
        val proposed = stored.copy(
            winnersCount = 4,
            bookingEnd = at("2027-05-30T17:00"),
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 3, now = currentTime, zone = edmonton)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_editOpenRejectsInvalidRegistrationEndBeforeStart() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
        )
        val currentTime = at("2027-05-05T12:00") // Open
        val proposed = stored.copy(
            registrationEnd = at("2027-05-01T08:00"), // Before start
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = currentTime, zone = edmonton)
        assertTrue(errors.contains("Invalid registration period"))
    }

    @Test
    fun validateLottery_positionalCallSourceCompatibility() {
        val lottery = Lottery(
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = 2000L,
            registrationEnd = 3000L,
            bookingStart = 4000L,
            bookingEnd = 5000L,
            winnersCount = 1,
        )
        // Positional call with (lottery, now) matching original API
        val errors = validateLottery(lottery, now)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateLottery_editOpenRejectsProposedRegistrationEndInPast() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
        )
        val currentTime = at("2027-05-05T12:00") // Stored lottery is OPEN
        // Proposed registration end is May 4 (after start May 1, but before currentTime May 5)
        val proposed = stored.copy(
            registrationEnd = at("2027-05-04T17:00"),
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = currentTime, zone = edmonton)
        assertTrue(errors.contains("Registration cannot end in the past"))
    }

    @Test
    fun validateLottery_editStoredRegistrationEndBoundaryAndInstantAfter() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
        )
        val boundaryTime = stored.registrationEnd // Exactly at registrationEnd (17:00:00.000)
        val proposed = stored.copy(
            registrationEnd = at("2027-05-12T17:00"), // Valid extension into future
        )

        // At exact registrationEnd boundary: stored lottery is still OPEN (now <= registrationEnd)
        val boundaryErrors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = boundaryTime, zone = edmonton)
        assertTrue(boundaryErrors.isEmpty())

        // At instant after registrationEnd (boundaryTime + 1 ms): stored lottery is now CLOSED
        val afterBoundaryTime = boundaryTime + 1L
        val afterBoundaryErrors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = afterBoundaryTime, zone = edmonton)
        assertEquals(1, afterBoundaryErrors.size)
        assertTrue(afterBoundaryErrors.contains("Registration has closed; lottery can no longer be edited"))
    }

    @Test
    fun validateLottery_editRejectsClosedLotteryEvenIfProposedDatesAreFuture() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-05T17:00"), // Ended in past
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
        )
        val currentTime = at("2027-05-06T12:00") // Registration has closed
        val proposed = stored.copy(
            registrationEnd = at("2027-05-20T17:00"), // Attempt to "re-open" by changing dates
        )
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 5, now = currentTime, zone = edmonton)
        assertEquals(1, errors.size)
        assertTrue(errors.contains("Registration has closed; lottery can no longer be edited"))
    }

    @Test
    fun validateLottery_editRejectsDrawnLottery() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
            drawnAt = at("2027-05-10T17:05"),
        )
        val currentTime = at("2027-05-12T12:00")
        val proposed = stored.copy(winnersCount = 10)
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 5, now = currentTime, zone = edmonton)
        assertEquals(1, errors.size)
        assertTrue(errors.contains("Cannot edit a lottery that has already been drawn"))
    }

    @Test
    fun validateLottery_editRejectsRemovedLottery() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
            removed = true,
        )
        val currentTime = at("2027-05-05T12:00")
        val proposed = stored.copy(removed = false) // Attempting to unremove
        val errors = validateLottery(proposed, storedLottery = stored, currentWaitingCount = 0, now = currentTime, zone = edmonton)
        assertEquals(1, errors.size)
        assertTrue(errors.contains("Cannot edit a removed lottery"))
    }

    @Test
    fun validateLottery_editWaitlistCapacityRules() {
        val stored = Lottery(
            id = "lot-1",
            ownerId = "owner-1",
            campsiteIds = listOf("camp-1"),
            nightsOffered = listOf("2027-06-01"),
            registrationStart = at("2027-05-01T09:00"),
            registrationEnd = at("2027-05-10T17:00"),
            bookingStart = at("2027-05-15T09:00"),
            bookingEnd = at("2027-05-25T17:00"),
            waitlistCapacity = 10,
        )
        val currentTime = at("2027-05-05T12:00")
        val currentWaitingCount = 7

        // 1. Capacity below current waiting count (rejected)
        val lowerThanWaiting = stored.copy(waitlistCapacity = 6)
        val lowerErrors = validateLottery(lowerThanWaiting, storedLottery = stored, currentWaitingCount = currentWaitingCount, now = currentTime, zone = edmonton)
        assertTrue(lowerErrors.contains("Waitlist limit cannot be less than current entrant count (7)"))

        // 2. Capacity equal to current waiting count (allowed)
        val equalToWaiting = stored.copy(waitlistCapacity = 7)
        val equalErrors = validateLottery(equalToWaiting, storedLottery = stored, currentWaitingCount = currentWaitingCount, now = currentTime, zone = edmonton)
        assertTrue(equalErrors.isEmpty())

        // 3. Capacity above current waiting count (allowed)
        val aboveWaiting = stored.copy(waitlistCapacity = 8)
        val aboveErrors = validateLottery(aboveWaiting, storedLottery = stored, currentWaitingCount = currentWaitingCount, now = currentTime, zone = edmonton)
        assertTrue(aboveErrors.isEmpty())

        // 4. Unlimited capacity / null (allowed)
        val unlimited = stored.copy(waitlistCapacity = null)
        val unlimitedErrors = validateLottery(unlimited, storedLottery = stored, currentWaitingCount = currentWaitingCount, now = currentTime, zone = edmonton)
        assertTrue(unlimitedErrors.isEmpty())
    }
}
