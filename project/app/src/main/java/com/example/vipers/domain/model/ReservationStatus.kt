/*
 * ReservationStatus.kt
 * The states a reservation can be in.
 * Known issues: "Active", "Past" and "Completed" are not statuses; they are worked out from
 * the dates of a CONFIRMED reservation.
 */
package com.example.vipers.domain.model

/** The state of a [Reservation]. */
enum class ReservationStatus {
    /** Booked and holding its nights. */
    CONFIRMED,

    /** Cancelled by the entrant; its nights were released. */
    CANCELLED,

    /** Rejected by the owner, with a reason; its nights were released. */
    REJECTED
}
