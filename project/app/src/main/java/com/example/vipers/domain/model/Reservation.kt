/*
 * Reservation.kt
 * Nights a lottery winner booked at a campsite.
 * Stored in the Firestore "reservations" collection.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

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

    /**
     * True if it is CONFIRMED and checked out before [today]: a completed stay, shown on the
     * owner's Past tab (US 02.07.02). On check-out day itself it is still active. Cancelled and
     * rejected reservations are never completed; they go in the Cancelled tab (US 02.07.03).
     * A check-out that can't be read counts as not completed.
     */
    fun isCompleted(today: LocalDate): Boolean {
        if (status != ReservationStatus.CONFIRMED) return false
        return try {
            LocalDate.parse(checkOut).isBefore(today)
        } catch (e: DateTimeParseException) {
            false
        }
    }

    /**
     * True if the entrant can still cancel: it is CONFIRMED and check-in is [today] or later
     * (US 01.07.01). A check-in that can't be read counts as not cancellable.
     */
    fun canCancel(today: LocalDate): Boolean {
        if (status != ReservationStatus.CONFIRMED) return false
        return try {
            !LocalDate.parse(checkIn).isBefore(today)
        } catch (e: DateTimeParseException) {
            false
        }
    }

    /**
     * The nights this reservation holds, from [checkIn] up to the night before [checkOut], as
     * "yyyy-MM-dd" strings. These are the nights a cancel or reject gives back (US 01.07.01).
     */
    fun nights(): List<String> {
        val nights = mutableListOf<String>()
        var night = LocalDate.parse(checkIn)
        val end = LocalDate.parse(checkOut)
        while (night.isBefore(end)) {
            nights.add(night.toString())
            night = night.plusDays(1)
        }
        return nights
    }

    /**
     * The inbox notice telling the entrant this reservation was cancelled or rejected, for
     * ReservationRepository.changeStatus to save (US 01.07.01, 02.08.01). Call it on the
     * reservation after its status changed. The ID is `cancelled_<id>` or `rejected_<id>`, so
     * the notice is only ever saved once, and the message starts with the campsite name like
     * the other inbox items.
     */
    fun statusNotice(now: Long): Notification {
        val rejected = status == ReservationStatus.REJECTED
        val dates = "$campsiteName: $checkIn to $checkOut"
        return Notification(
            id = (if (rejected) "rejected_" else "cancelled_") + id,
            recipientId = userId,
            senderId = if (rejected) ownerId else "system",
            campsiteId = campsiteId,
            reservationId = id,
            type = if (rejected) {
                NotificationType.RESERVATION_REJECTED
            } else {
                NotificationType.RESERVATION_CANCELLED
            },
            title = if (rejected) "Reservation rejected" else "Reservation cancelled",
            message = if (rejected && !rejectionReason.isNullOrBlank()) {
                "$dates. Reason: $rejectionReason"
            } else {
                dates
            },
            createdAt = now,
        )
    }

    /** The total price as text for the UI, e.g. "$100" or "$65.50" (T-03, US 01.05.01). */
    val totalPriceText: String
        get() = "$${Campsite.dollarsText(totalPriceCents)}"

    /**
     * True if the user with this [userId] can read and send messages about this reservation: its
     * entrant or the owner of its campsite (US 02.12.01, 01.14.01). Anyone else, including
     * another owner, can't.
     */
    fun canMessage(userId: String): Boolean =
        userId.isNotBlank() && (userId == this.userId || userId == ownerId)

    /**
     * Checks whether an entrant can message the owner about this reservation (US 01.14.01).
     *
     * @param entrantId ID of the entrant requesting the conversation.
     * @return True if [entrantId] identifies this reservation's entrant, the reservation has
     * not been cancelled, and it has a different owner with a nonblank ID.
     */
    fun canMessageOwner(entrantId: String): Boolean =
        status != ReservationStatus.CANCELLED && entrantId == userId &&
            !otherSide(entrantId).isNullOrBlank()

    /**
     * The other person in this reservation's conversation (US 02.12.01): the entrant for the
     * owner, and the owner for the entrant. Null if [userId] can't message about it
     * ([canMessage]), or if the owner booked their own campsite, so there is nobody else.
     */
    fun otherSide(userId: String): String? = when {
        !canMessage(userId) -> null
        this.userId == ownerId -> null
        userId == ownerId -> this.userId
        else -> ownerId
    }
}
