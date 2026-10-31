/*
 * SeedLoader.kt
 * Adds demo data so the app has something to show: 4 campsites (one per region), 2 lotteries,
 * a waiting-list entry and 3 reservations for the current user.
 * Known issues: dates are based on today so there is always an open lottery and active
 * reservations. To reload, delete the documents in the Firebase console.
 */
package com.example.vipers.data.seed

import com.example.vipers.AppContainer
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.model.Region
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import java.time.LocalDate

/** Writes demo data using the repositories in [container]. */
class SeedLoader(
    private val container: AppContainer,
    private val today: LocalDate = LocalDate.now(),
) {
    /**
     * Adds the demo campsites and lotteries if they aren't there yet, then gives the
     * current user a waiting-list entry and 3 reservations if they don't have any.
     */
    suspend fun seed() {
        val userId = container.deviceId
        if (container.reservationRepository.getReservationsForUser(userId).isNotEmpty()) return

        // Reuse the demo campsites if another phone already added them.
        var campsites = container.campsiteRepository.getAllCampsites()
            .filter { it.ownerId.startsWith("seed-owner") }
        if (campsites.isEmpty()) {
            campsites = addCampsitesAndLotteries()
        }

        val hinton = campsites.first { it.region == Region.HINTON }
        val banff = campsites.first { it.region == Region.BANFF }
        val kananaskis = campsites.first { it.region == Region.KANANASKIS }

        // Join the open Hinton lottery.
        val lottery = container.lotteryRepository.getLotteriesForCampsite(hinton.id).firstOrNull()
        if (lottery != null) {
            container.lotteryRepository.saveEntry(
                LotteryEntry(
                    lotteryId = lottery.id,
                    userId = userId,
                    userName = "Demo user",
                    groupSize = 4,
                    joinedAt = System.currentTimeMillis(),
                )
            )
        }

        // Two active reservations and one past one.
        addReservation(banff, "R-20417", checkIn = today.plusDays(8), nights = 2, groupSize = 2)
        addReservation(kananaskis, "R-20431", checkIn = today.plusDays(25), nights = 3, groupSize = 4)
        addReservation(hinton, "R-19882", checkIn = today.minusDays(40), nights = 2, groupSize = 3)
    }

    /** Adds 2 owners, 4 campsites and 2 lotteries. Returns the campsites with their new IDs. */
    private suspend fun addCampsitesAndLotteries(): List<Campsite> {
        container.profileRepository.saveProfile(UserProfile(id = "seed-owner-priya", name = "Priya Sharma"))
        container.profileRepository.saveProfile(UserProfile(id = "seed-owner-daniel", name = "Daniel Okafor"))

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
                pricePerNightCents = 6500,
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
            campsite.copy(id = id)
        }

        // Open now: people can join.
        container.lotteryRepository.addLottery(
            Lottery(
                ownerId = "seed-owner-priya",
                campsiteIds = listOf(campsites[0].id),
                registrationStart = today.minusDays(3).toString(),
                registrationEnd = today.plusDays(10).toString(),
                bookingStart = today.plusDays(12).toString(),
                bookingEnd = today.plusDays(18).toString(),
                winnersCount = 3,
            )
        )
        // Already drawn: winners are booking.
        container.lotteryRepository.addLottery(
            Lottery(
                ownerId = "seed-owner-daniel",
                campsiteIds = listOf(campsites[1].id),
                registrationStart = today.minusDays(30).toString(),
                registrationEnd = today.minusDays(10).toString(),
                bookingStart = today.minusDays(8).toString(),
                bookingEnd = today.plusDays(5).toString(),
                winnersCount = 2,
                drawnAt = System.currentTimeMillis(),
            )
        )
        return campsites
    }

    private suspend fun addReservation(
        campsite: Campsite,
        number: String,
        checkIn: LocalDate,
        nights: Long,
        groupSize: Int,
    ) {
        container.reservationRepository.addReservation(
            Reservation(
                reservationNumber = number,
                userId = container.deviceId,
                ownerId = campsite.ownerId,
                campsiteId = campsite.id,
                campsiteName = campsite.name,
                checkIn = checkIn.toString(),
                checkOut = checkIn.plusDays(nights).toString(),
                groupSize = groupSize,
                totalPriceCents = campsite.pricePerNightCents * nights,
            )
        )
    }
}
