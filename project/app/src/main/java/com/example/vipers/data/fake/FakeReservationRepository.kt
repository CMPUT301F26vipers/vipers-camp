/*
 * FakeReservationRepository.kt
 * An in-memory ReservationRepository for unit and intent tests (test double for Repository).
 * Supports atomic reservation transactions with conflict checks (T-03).
 * Known issues: none.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.repository.ReserveResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Keeps reservations in a map instead of Firestore. Pass [initial] for seed data. */
class FakeReservationRepository(
    var campsiteRepository: CampsiteRepository? = null,
    var lotteryRepository: LotteryRepository? = null,
) : ReservationRepository {

    private val reservations = mutableMapOf<String, Reservation>()
    private var nextId = 1
    private val mutex = Mutex()

    constructor(initial: List<Reservation>) : this() {
        reservations.putAll(initial.associateBy { it.id })
    }

    constructor(
        campsiteRepository: CampsiteRepository,
        lotteryRepository: LotteryRepository,
        initial: List<Reservation>,
    ) : this(campsiteRepository, lotteryRepository) {
        reservations.putAll(initial.associateBy { it.id })
    }

    fun attachRepositories(campsiteRepo: CampsiteRepository, lotteryRepo: LotteryRepository) {
        campsiteRepository = campsiteRepo
        lotteryRepository = lotteryRepo
    }

    override suspend fun addReservation(reservation: Reservation): String {
        val id = "reservation-${nextId++}"
        reservations[id] = reservation.copy(id = id)
        return id
    }

    override suspend fun getReservation(id: String): Reservation? = reservations[id]

    override suspend fun getReservationsForUser(userId: String): List<Reservation> =
        reservations.values.filter { it.userId == userId }

    override suspend fun getReservationsForOwner(ownerId: String): List<Reservation> =
        reservations.values.filter { it.ownerId == ownerId }

    override suspend fun createReservation(
        lotteryId: String,
        campsiteId: String,
        userId: String,
        nights: List<String>,
        groupSize: Int,
        now: Long,
    ): ReserveResult = mutex.withLock {
        if (nights.isEmpty()) return ReserveResult.Error("No nights selected")
        val sortedNights = nights.distinct().sorted()

        val campsiteRepo = campsiteRepository
            ?: return ReserveResult.Error("Campsite repository not attached")
        val lotteryRepo = lotteryRepository
            ?: return ReserveResult.Error("Lottery repository not attached")

        val campsite = campsiteRepo.getCampsite(campsiteId)
            ?: return ReserveResult.Error("Campsite not found")
        if (campsite.removed) return ReserveResult.Error("Campsite is no longer available")

        if (groupSize > campsite.maxOccupancy) {
            return ReserveResult.CapacityExceeded
        }

        val lottery = lotteryRepo.getLottery(lotteryId)
            ?: return ReserveResult.Error("Lottery not found")
        if (lottery.removed) return ReserveResult.Error("Lottery is no longer available")

        if (campsiteId !in lottery.campsiteIds) {
            return ReserveResult.Error("Campsite is not offered in this lottery")
        }
        if (sortedNights.any { it !in lottery.nightsOffered }) {
            return ReserveResult.Error("Selected nights are not offered in this lottery")
        }

        val entry = lotteryRepo.getEntry(lotteryId, userId)
            ?: return ReserveResult.NotEligible

        if (!entry.canReserve(lottery, now)) {
            return ReserveResult.NotEligible
        }

        val calendar = campsiteRepo.getAvailability(campsiteId)

        // Two reservations can never hold the same night (AC 4)
        if (sortedNights.any { it in calendar.heldNights || it !in calendar.openNights }) {
            return ReserveResult.Conflict
        }

        // Hold nights in campsite's availability calendar
        val updatedCalendar = calendar.copy(
            heldNights = (calendar.heldNights + sortedNights).distinct().sorted()
        )
        campsiteRepo.saveAvailability(updatedCalendar)

        // Update entrant's lottery status to RESERVED
        lotteryRepo.updateEntry(entry.copy(status = EntryStatus.RESERVED))

        val checkIn = sortedNights.first()
        val checkOut = LocalDate.parse(sortedNights.last()).plusDays(1).toString()
        val currentId = nextId++
        val id = "reservation-$currentId"
        val reservationNumber = "R-${10000 + currentId}"
        val totalPriceCents = campsite.totalPriceCents(sortedNights.size)

        val reservation = Reservation(
            id = id,
            reservationNumber = reservationNumber,
            userId = userId,
            ownerId = campsite.ownerId,
            campsiteId = campsite.id,
            campsiteName = campsite.name,
            lotteryEntryId = entry.id,
            checkIn = checkIn,
            checkOut = checkOut,
            groupSize = groupSize,
            totalPriceCents = totalPriceCents,
            rulesAcceptedAt = now,
            status = ReservationStatus.CONFIRMED,
            statusChangedAt = now,
        )
        reservations[id] = reservation
        ReserveResult.Success(reservation)
    }
}
