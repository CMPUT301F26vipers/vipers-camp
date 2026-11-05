/*
 * SeedLoader.kt
 * Adds demo data so the app has something to show in every stage of a lottery.
 * Made once and shared: 3 demo users and 4 campsites (one per region) with availability.
 * Reused if one is in the right stage, otherwise added: 5 lotteries (one open, three drawn,
 * one finished) with a demo entrant's entries. Each lottery's winnersCount matches its winners.
 * For the current device: an entry in every lottery (waiting, won, reserved), 3 reservations
 * (2 active, 1 past) with their nights held, and 3 notifications.
 * Known issues: times are based on the day each phone seeds, so seeding works on any day.
 * Phones that seed on different days may each add their own lotteries. To reload, delete the
 * documents in the Firebase console.
 */
package com.example.vipers.data.seed

import com.example.vipers.AppContainer
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import java.time.LocalDate
import java.time.ZoneId

/** Writes demo data using the repositories in [container]. */
class SeedLoader(
    private val container: AppContainer,
    private val today: LocalDate = LocalDate.now(),
) {
    // Noon today, so "now + N days" lands in the middle of a day.
    private val now = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Adds the shared demo data if it isn't there yet, then gives the current user their
     * entries, reservations and notifications if they don't have any reservations.
     */
    suspend fun seed() {
        val userId = container.deviceId
        if (container.reservationRepository.getReservationsForUser(userId).isNotEmpty()) return

        // Reuse the demo campsites if another phone already added them.
        var campsites = container.campsiteRepository.getAllCampsites()
            .filter { it.ownerId.startsWith("seed-owner") }
        if (campsites.isEmpty()) {
            campsites = addSharedData()
        }

        val hinton = campsites.first { it.region == Region.HINTON }
        val banff = campsites.first { it.region == Region.BANFF }
        val jasper = campsites.first { it.region == Region.JASPER }
        val kananaskis = campsites.first { it.region == Region.KANANASKIS }

        // Reuse a lottery in the right stage if there is one; otherwise add one based on today,
        // so seeding still works days after another phone first seeded.
        val hintonOpen = findOrAddLottery(hinton, LotteryStatus.OPEN) {
            addLottery(hinton, registration = -3..10, booking = 12..18, firstNight = 20, nights = 6,
                drawnDaysAgo = null, winnersCount = 3)
                .also { addEntry(it, SAM, EntryStatus.WAITING, groupSize = 2) }
        }
        val banffDrawn = findOrAddLottery(banff, LotteryStatus.DRAWN) {
            addLottery(banff, registration = -30..-10, booking = -8..5, firstNight = 7, nights = 10,
                drawnDaysAgo = 9, winnersCount = 1)
                .also { addEntry(it, SAM, EntryStatus.LOST, groupSize = 2) }
        }
        val jasperDrawn = findOrAddLottery(jasper, LotteryStatus.DRAWN) {
            addLottery(jasper, registration = -20..-5, booking = -4..6, firstNight = 14, nights = 7,
                drawnDaysAgo = 4, winnersCount = 1)
                .also { addEntry(it, SAM, EntryStatus.LOST, groupSize = 5) }
        }
        val kananaskisDrawn = findOrAddLottery(kananaskis, LotteryStatus.DRAWN) {
            addLottery(kananaskis, registration = -40..-20, booking = -18..3, firstNight = 25, nights = 6,
                drawnDaysAgo = 19, winnersCount = 1)
        }
        val hintonFinished = findOrAddLottery(hinton, LotteryStatus.CLOSED) {
            addLottery(hinton, registration = -90..-70, booking = -68..-50, firstNight = -40, nights = 6,
                drawnDaysAgo = 69, winnersCount = 1)
        }

        // On the waiting list for the open lottery, and won the Jasper one (can reserve now).
        addEntry(hintonOpen, userId, EntryStatus.WAITING, groupSize = 4)
        addEntry(jasperDrawn, userId, EntryStatus.WON, groupSize = 3)

        // Won and reserved the others: two active reservations and one past one.
        val banffReservation = addReservation(banff, banffDrawn, "R-20417", checkInDay = 8, nights = 2, groupSize = 2)
        addReservation(kananaskis, kananaskisDrawn, "R-20431", checkInDay = 25, nights = 3, groupSize = 4)
        addReservation(hinton, hintonFinished, "R-19882", checkInDay = -40, nights = 2, groupSize = 3)

        addNotification(
            NotificationType.WON, "You won the lottery!", "${jasper.name}: reserve by ${date(6)}",
            campsiteId = jasper.id, lotteryId = jasperDrawn.id, daysAgo = 4,
        )
        addNotification(
            NotificationType.RESERVATION_CONFIRMED, "Reservation confirmed", "${banff.name}: ${date(8)} to ${date(10)}",
            campsiteId = banff.id, reservationId = banffReservation, daysAgo = 6,
        )
        addNotification(
            NotificationType.OWNER_BROADCAST, "Message from owner", "${hinton.name}: the cookhouse reopens next week.",
            campsiteId = hinton.id, lotteryId = hintonOpen.id, daysAgo = 1, senderId = hinton.ownerId,
        )
    }

    /** Adds the demo users, and the campsites with their availability. Returns the campsites. */
    private suspend fun addSharedData(): List<Campsite> {
        container.profileRepository.saveProfile(UserProfile(id = "seed-owner-priya", name = "Priya Sharma"))
        container.profileRepository.saveProfile(UserProfile(id = "seed-owner-daniel", name = "Daniel Okafor"))
        container.profileRepository.saveProfile(UserProfile(id = SAM, name = "Sam Patel"))

        val campsites = listOf(
            Campsite(
                ownerId = "seed-owner-priya",
                name = "Hinton Ridge Campground",
                address = "Hinton, AB",
                region = Region.HINTON,
                description = "Quiet forest sites with an outdoor cookhouse and a tennis court.",
                maxOccupancy = 6,
                tags = listOf("Cookhouse", "Tennis court"),
                pricePerNightCents = 5000,
                rules = "Quiet hours after 10 PM",
                cancellationPolicy = "Free up to 7 days before",
            ),
            Campsite(
                ownerId = "seed-owner-daniel",
                name = "Bow River Sites",
                address = "Banff, AB",
                region = Region.BANFF,
                description = "Riverside sites near town with fire pits and showers.",
                maxOccupancy = 4,
                tags = listOf("Showers", "Fire pit"),
                pricePerNightCents = 6550,
                rules = "Fires in pits only",
                cancellationPolicy = "Free up to 14 days before",
            ),
            Campsite(
                ownerId = "seed-owner-priya",
                name = "Maligne Lake Camp",
                address = "Maligne Lake Rd, Jasper, AB",
                region = Region.JASPER,
                description = "Lakeside tent sites with canoe rentals nearby.",
                maxOccupancy = 8,
                tags = listOf("Lake access"),
                pricePerNightCents = 4000,
                rules = "Pack out all garbage",
                cancellationPolicy = "Free up to 3 days before",
            ),
            Campsite(
                ownerId = "seed-owner-daniel",
                name = "Kananaskis Lakes Sites",
                address = "Kananaskis Trail, AB",
                region = Region.KANANASKIS,
                description = "Mountain views, hiking trails and a playground.",
                maxOccupancy = 6,
                tags = listOf("Hiking", "Playground"),
                pricePerNightCents = 5500,
                rules = "Dogs on leash",
                cancellationPolicy = "Free up to 7 days before",
            ),
        ).map { campsite ->
            val id = container.campsiteRepository.addCampsite(campsite)
            // Open every night from 45 days ago to 60 days from now.
            container.campsiteRepository.saveAvailability(
                AvailabilityCalendar(campsiteId = id, openNights = dates(-45, 106))
            )
            campsite.copy(id = id)
        }
        return campsites
    }

    /**
     * Returns a lottery for [campsite] that is in [status] right now, or runs [add] to make one.
     * A CLOSED match must also have been drawn, so it is a finished lottery.
     */
    private suspend fun findOrAddLottery(
        campsite: Campsite,
        status: LotteryStatus,
        add: suspend () -> Lottery,
    ): Lottery {
        val existing = container.lotteryRepository.getLotteriesForCampsite(campsite.id).firstOrNull {
            it.status(now) == status && (status != LotteryStatus.CLOSED || it.drawnAt != null)
        }
        return existing ?: add()
    }

    private suspend fun addLottery(
        campsite: Campsite,
        registration: IntRange,
        booking: IntRange,
        firstNight: Int,
        nights: Int,
        drawnDaysAgo: Int?,
        winnersCount: Int,
    ): Lottery {
        val lottery = Lottery(
            ownerId = campsite.ownerId,
            campsiteIds = listOf(campsite.id),
            nightsOffered = dates(firstNight, nights),
            registrationStart = day(registration.first),
            registrationEnd = day(registration.last),
            bookingStart = day(booking.first),
            bookingEnd = day(booking.last),
            winnersCount = winnersCount,
            waitlistCapacity = 20,
            drawnAt = drawnDaysAgo?.let { day(-it) },
        )
        val id = container.lotteryRepository.addLottery(lottery)
        return lottery.copy(id = id)
    }

    private suspend fun addEntry(lottery: Lottery, userId: String, status: EntryStatus, groupSize: Int) {
        container.lotteryRepository.saveEntry(
            LotteryEntry(
                lotteryId = lottery.id,
                userId = userId,
                groupSize = groupSize,
                joinedAt = lottery.registrationStart,
                status = status,
            )
        )
    }

    /** Adds a reserved entry, the reservation and holds its nights. Returns the reservation ID. */
    private suspend fun addReservation(
        campsite: Campsite,
        lottery: Lottery,
        number: String,
        checkInDay: Int,
        nights: Int,
        groupSize: Int,
    ): String {
        val userId = container.deviceId
        addEntry(lottery, userId, EntryStatus.RESERVED, groupSize)

        val calendar = container.campsiteRepository.getAvailability(campsite.id)
        container.campsiteRepository.saveAvailability(
            calendar.copy(heldNights = calendar.heldNights + dates(checkInDay, nights))
        )

        return container.reservationRepository.addReservation(
            Reservation(
                reservationNumber = number,
                userId = userId,
                ownerId = campsite.ownerId,
                campsiteId = campsite.id,
                campsiteName = campsite.name,
                lotteryEntryId = LotteryEntry.idFor(lottery.id, userId),
                checkIn = date(checkInDay),
                checkOut = date(checkInDay + nights),
                groupSize = groupSize,
                totalPriceCents = campsite.pricePerNightCents * nights,
                rulesAcceptedAt = lottery.bookingStart,
                statusChangedAt = lottery.bookingStart,
            )
        )
    }

    private suspend fun addNotification(
        type: NotificationType,
        title: String,
        message: String,
        campsiteId: String,
        daysAgo: Int,
        lotteryId: String? = null,
        reservationId: String? = null,
        senderId: String = "system",
    ) {
        container.notificationRepository.addNotification(
            Notification(
                recipientId = container.deviceId,
                senderId = senderId,
                campsiteId = campsiteId,
                lotteryId = lotteryId,
                reservationId = reservationId,
                type = type,
                title = title,
                message = message,
                createdAt = day(-daysAgo),
            )
        )
    }

    /** Noon, [days] days from today, in milliseconds. */
    private fun day(days: Int): Long = now + days * DAY_MILLIS

    /** The date [days] days from today, as "yyyy-MM-dd". */
    private fun date(days: Int): String = today.plusDays(days.toLong()).toString()

    /** [count] dates in a row, starting [firstDay] days from today. */
    private fun dates(firstDay: Int, count: Int): List<String> = (0 until count).map { date(firstDay + it) }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /** Device ID of the made-up entrant who joins the demo lotteries. */
        private const val SAM = "seed-entrant-sam"
    }
}
