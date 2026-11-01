/*
 * Reservation.kt
 * Nights a lottery winner booked at a campsite.
 * Stored in the Firestore "reservations" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.time.LocalDate

/**
 * A booking of nights at a campsite. Dates are "yyyy-MM-dd" strings.
 *
 * @property id Firestore document ID.
 * @property reservationNumber Number shown to users, e.g. "R-20417".
 * @property userId Device ID of the entrant who booked.
 * @property ownerId Device ID of the campsite's owner.
 * @property campsiteId The campsite booked.
 * @property campsiteName Campsite name, copied so lists don't need another lookup.
 * @property lotteryEntryId The winning entry this booking came from.
 * @property checkIn First night.
 * @property checkOut Day the stay ends.
 * @property groupSize Number of guests.
 * @property totalPriceCents Total price in cents, fixed when booked.
 * @property rulesAcceptedAt When the entrant accepted the campsite rules, in milliseconds since 1970.
 * @property status Confirmed, cancelled or rejected.
 * @property statusChangedAt When the status last changed, in milliseconds since 1970.
 * @property rejectionReason The owner's reason; only set when [status] is REJECTED.
 */
data class Reservation(
    val id: String = "",
    val reservationNumber: String = "",
    val userId: String = "",
    val ownerId: String = "",
    val campsiteId: String = "",
    val campsiteName: String = "",
    val lotteryEntryId: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val groupSize: Int = 1,
    val totalPriceCents: Long = 0,
    val rulesAcceptedAt: Long = 0,
    val status: ReservationStatus = ReservationStatus.CONFIRMED,
    val statusChangedAt: Long = 0,
    val rejectionReason: String? = null,
) {
    /** True if it is CONFIRMED and checks out on or after [today]; everything else is past. */
    fun isActive(today: LocalDate): Boolean =
        status == ReservationStatus.CONFIRMED && !LocalDate.parse(checkOut).isBefore(today)
}
