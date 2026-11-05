/*
 * LotteryValidation.kt
 * Validation logic for creating or updating a Lottery.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private fun isValidIsoDate(text: String): Boolean {
    if (text.length != 10) return false
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val pos = ParsePosition(0)
    val date = sdf.parse(text, pos)
    return date != null && pos.index == text.length
}

/**
 * Validates a [lottery] against business logic rules.
 *
 * @param lottery The lottery to validate.
 * @param now Current timestamp in milliseconds (defaults to system time).
 * @return A list of validation error message strings. Empty if valid.
 */
fun validateLottery(lottery: Lottery, now: Long = System.currentTimeMillis()): List<String> {
    val errors = mutableListOf<String>()

    if (lottery.ownerId.isBlank()) errors.add("Owner is required")
    if (lottery.campsiteIds.isEmpty()) errors.add("Select a campsite")
    if (lottery.nightsOffered.isEmpty() || lottery.nightsOffered.any { !isValidIsoDate(it) }) {
        errors.add("Select at least one night")
    }

    // Registration period validation: required timestamps
    if (lottery.registrationStart <= 0L || lottery.registrationEnd <= 0L) {
        errors.add("Both registration start and end times must be set")
    } else {
        if (lottery.registrationStart < now) errors.add("Registration cannot start in the past")
        if (lottery.registrationEnd <= lottery.registrationStart) errors.add("Invalid registration period")
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

    if (lottery.waitlistCapacity != null && lottery.waitlistCapacity < 1) {
        errors.add("Waitlist limit must be at least 1")
    }

    return errors
}

/**
 * Validates whether an entrant can join a [lottery] at time [now].
 *
 * @param lottery The lottery to join.
 * @param now Current timestamp in milliseconds (defaults to system time).
 * @return An error message string if ineligible to join, or null if eligible.
 */
fun validateEntrantJoinEligibility(lottery: Lottery, now: Long = System.currentTimeMillis()): String? {
    if (lottery.registrationStart <= 0L || lottery.registrationEnd <= 0L) {
        return "Registration period is not configured"
    }
    if (lottery.registrationEnd <= lottery.registrationStart) {
        return "Invalid registration period"
    }
    if (now < lottery.registrationStart) {
        return "Registration has not started yet"
    }
    if (now > lottery.registrationEnd) {
        return "Registration has ended"
    }
    return null
}
