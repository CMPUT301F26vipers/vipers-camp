/*
 * LotteryValidation.kt
 * Validation logic for creating or updating a Lottery.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private fun isValidIsoDate(text: String): Boolean {
    if (text.length != 10) return false
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val pos = ParsePosition(0)
    val date = sdf.parse(text, pos)
    return date != null && pos.index == text.length
}

/**
 * Returns the distinct valid ISO nights from [candidateNights] that are open and unheld
 * ([AvailabilityCalendar.isFree]) at every campsite in [campsiteIds], in date order (US 02.04.02).
 */
fun sharedAvailableNights(
    campsiteIds: Collection<String>,
    calendarsByCampsiteId: Map<String, AvailabilityCalendar>,
    candidateNights: Collection<String>,
): List<String> {
    val distinctIds = campsiteIds.filter { it.isNotBlank() }.distinct()
    if (distinctIds.isEmpty()) return emptyList()
    return candidateNights
        .filter { isValidIsoDate(it) }
        .distinct()
        .filter { night -> distinctIds.all { id -> calendarsByCampsiteId[id]?.isFree(night) == true } }
        .sorted()
}

/**
 * Returns the sorted offered nights on [lottery] that are not free ([AvailabilityCalendar.isFree])
 * for one or more required `(campsiteId, night)` pairs (US 02.04.02).
 *
 * Uses [lottery.campsiteIds] and [lottery.nightsOffered] directly as validation input:
 * - For creation ([storedLottery] == null): validates every `(campsiteId, night)` pair.
 * - For edits ([storedLottery] != null): validates only newly introduced pairs—every proposed
 *   night at added campsites and every added night at retained campsites—so existing pairs stay
 *   valid during unrelated edits even if a night has since become unavailable.
 */
fun conflictingOfferedNights(
    lottery: Lottery,
    calendarsByCampsiteId: Map<String, AvailabilityCalendar>,
    storedLottery: Lottery? = null,
): List<String> {
    val proposedIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
    if (proposedIds.isEmpty()) return emptyList()

    val storedIds = storedLottery?.campsiteIds?.filter { it.isNotBlank() }?.toSet().orEmpty()
    val storedNights = storedLottery?.nightsOffered?.toSet().orEmpty()

    return lottery.nightsOffered
        .distinct()
        .filter { night ->
            proposedIds.any { cid ->
                val requiresCheck = storedLottery == null || cid !in storedIds || night !in storedNights
                requiresCheck && calendarsByCampsiteId[cid]?.isFree(night) != true
            }
        }
        .sorted()
}

/**
 * Validates a proposed [lottery] configuration against business logic rules.
 *
 * For new lotteries, [storedLottery] should be null.
 * When editing an existing lottery, pass the authoritative [storedLottery] currently saved in the database
 * and the [currentWaitingCount]. When [calendarsByCampsiteId] is provided, availability is also
 * validated via [conflictingOfferedNights] reusing [AvailabilityCalendar.isFree].
 *
 * @param lottery The proposed lottery configuration to validate.
 * @param now Current timestamp in milliseconds (defaults to system time).
 * @param storedLottery The existing lottery before edits, or null if creating a new lottery.
 * @param currentWaitingCount Current number of entrants on the waiting list (used when editing).
 * @param zone Timezone used to determine the calendar date of [lottery.bookingStart].
 * @param calendarsByCampsiteId Optional calendars keyed by campsite ID for availability checks.
 * @return A list of validation error message strings. Empty if valid.
 */
fun validateLottery(
    lottery: Lottery,
    now: Long = System.currentTimeMillis(),
    storedLottery: Lottery? = null,
    currentWaitingCount: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
    calendarsByCampsiteId: Map<String, AvailabilityCalendar>? = null,
): List<String> {
    val errors = mutableListOf<String>()

    if (storedLottery != null) {
        val blockedReason = storedLottery.editBlockedReason(now)
        if (blockedReason != null) {
            errors.add(blockedReason)
            return errors
        }
    }

    val distinctCampsiteIds = lottery.campsiteIds.filter { it.isNotBlank() }.distinct()
    if (lottery.ownerId.isBlank()) errors.add("Owner is required")
    if (distinctCampsiteIds.isEmpty()) errors.add("Select a campsite")
    if (lottery.nightsOffered.isEmpty() || lottery.nightsOffered.any { !isValidIsoDate(it) }) {
        errors.add("Select at least one night")
    }

    // Registration period validation: required timestamps
    if (lottery.registrationStart <= 0L || lottery.registrationEnd <= 0L) {
        errors.add("Both registration start and end times must be set")
    } else {
        // When editing an already open lottery, registrationStart may already be in the past.
        // For new lotteries or upcoming edits, registration cannot start in the past.
        val allowPastRegStart = storedLottery != null && storedLottery.status(now) == LotteryStatus.OPEN
        if (lottery.registrationStart < now && !allowPastRegStart) {
            errors.add("Registration cannot start in the past")
        }
        if (allowPastRegStart && lottery.registrationEnd < now) {
            errors.add("Registration cannot end in the past")
        }
        if (lottery.registrationEnd <= lottery.registrationStart) {
            errors.add("Invalid registration period")
        }
    }

    // Booking period validation: required timestamps
    if (lottery.bookingStart <= 0L || lottery.bookingEnd <= 0L) {
        errors.add("Both booking start and end times must be set")
    } else {
        if (lottery.registrationEnd > 0L && lottery.bookingStart < lottery.registrationEnd) {
            errors.add("Booking must start after registration")
        } else if (lottery.bookingStart < now) {
            errors.add("Booking cannot start in the past")
        }
        if (lottery.bookingEnd <= lottery.bookingStart) errors.add("Invalid booking period")
    }

    if (lottery.winnersCount < 1) errors.add("At least one winner is required")

    if (lottery.waitlistCapacity != null) {
        if (lottery.waitlistCapacity < 1) {
            errors.add("Waitlist limit must be at least 1")
        } else if (storedLottery != null && lottery.waitlistCapacity < currentWaitingCount) {
            errors.add("Waitlist limit cannot be less than current entrant count ($currentWaitingCount)")
        }
    }

    // Offered nights validation: nights offered must be on or after the booking start date
    if (lottery.bookingStart > 0L && lottery.nightsOffered.isNotEmpty()) {
        val bookingStartDate = Instant.ofEpochMilli(lottery.bookingStart).atZone(zone).toLocalDate()
        val hasNightsBeforeBooking = lottery.nightsOffered.any { night ->
            if (isValidIsoDate(night)) {
                LocalDate.parse(night).isBefore(bookingStartDate)
            } else {
                false
            }
        }
        if (hasNightsBeforeBooking) {
            errors.add("Offered nights must be on or after booking start")
        }
    }

    if (calendarsByCampsiteId != null && distinctCampsiteIds.isNotEmpty() && lottery.nightsOffered.isNotEmpty()) {
        val normalized = lottery.copy(campsiteIds = distinctCampsiteIds)
        if (conflictingOfferedNights(normalized, calendarsByCampsiteId, storedLottery).isNotEmpty()) {
            errors.add("One or more selected nights are no longer available. Please reload the nights.")
        }
    }

    return errors
}

/**
 * Validates whether an entrant can join the waiting list for a [lottery].
 *
 * @param lottery The lottery to join.
 * @param existingEntry The entrant's existing entry in this lottery, if any.
 * @param waitingCount Current number of entrants on the waiting list.
 * @param groupSize The number of people in the entrant's group (must be at least 1).
 * @param maxOccupancy The maximum occupancy allowed for the campsite(s) in this lottery, or null if unknown.
 * @param now Current timestamp in milliseconds (defaults to system time).
 * @return A list of validation error message strings. Empty if valid.
 */
fun validateJoinLottery(
    lottery: Lottery,
    existingEntry: LotteryEntry? = null,
    waitingCount: Int = 0,
    groupSize: Int = 1,
    maxOccupancy: Int? = null,
    now: Long = System.currentTimeMillis(),
): List<String> {
    val errors = mutableListOf<String>()

    when (lottery.status(now)) {
        LotteryStatus.UPCOMING -> errors.add("Registration has not started")
        LotteryStatus.CLOSED, LotteryStatus.DRAWN -> errors.add("Registration is closed")
        LotteryStatus.OPEN -> {}
    }

    if (lottery.waitlistCapacity != null && waitingCount >= lottery.waitlistCapacity) {
        errors.add("Waiting list is full")
    }

    if (existingEntry != null) {
        if (existingEntry.status == EntryStatus.WAITING) {
            errors.add("Already on the waiting list")
        } else {
            errors.add("Cannot join with existing ${existingEntry.status} entry")
        }
    }

    if (groupSize < 1) {
        errors.add("Group size must be at least 1")
    }

    if (maxOccupancy == null) {
        errors.add("Campsite occupancy information is unavailable")
    } else if (groupSize > maxOccupancy) {
        errors.add("Group size cannot exceed maximum occupancy of $maxOccupancy")
    }

    return errors
}
