/*
 * LotteryValidation.kt
 * Validation logic for creating or updating a Lottery.
 * Known issues: none.
 */
package com.example.vipers.domain.model

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
    if (lottery.nightsOffered.isEmpty()) errors.add("Select at least one night")

    // Registration period validation: if partially set, error. If set, validate timestamps.
    if (lottery.registrationStart > 0 || lottery.registrationEnd > 0) {
        if (lottery.registrationStart <= 0 || lottery.registrationEnd <= 0) {
            errors.add("Both registration start and end times must be set")
        } else {
            if (lottery.registrationStart < now) errors.add("Registration cannot start in the past")
            if (lottery.registrationEnd <= lottery.registrationStart) errors.add("Invalid registration period")
        }
    }

    // Booking period validation: if partially set, error. If set, validate timestamps.
    if (lottery.bookingStart > 0 || lottery.bookingEnd > 0) {
        if (lottery.bookingStart <= 0 || lottery.bookingEnd <= 0) {
            errors.add("Both booking start and end times must be set")
        } else {
            if (lottery.registrationEnd > 0 && lottery.bookingStart < lottery.registrationEnd) {
                errors.add("Booking must start after registration")
            } else if (lottery.bookingStart < now) {
                errors.add("Booking cannot start in the past")
            }
            if (lottery.bookingEnd <= lottery.bookingStart) errors.add("Invalid booking period")
        }
    }

    if (lottery.winnersCount < 1) errors.add("At least one winner is required")

    return errors
}
