/*
 * SeedLoaderTest.kt
 * Checks the seed loader adds the demo data, using the fake repositories (no Firestore).
 * Known issues: none.
 */
package com.example.vipers.data.seed

import com.example.vipers.AppContainer
import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.data.fake.FakeNotificationRepository
import com.example.vipers.data.fake.FakeProfileRepository
import com.example.vipers.data.fake.FakeReservationRepository
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Region
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeedLoaderTest {

    private val today = LocalDate.parse("2027-05-09")

    private val container = AppContainer(
        deviceId = "my-phone",
        profileRepository = FakeProfileRepository(),
        campsiteRepository = FakeCampsiteRepository(),
        lotteryRepository = FakeLotteryRepository(),
        reservationRepository = FakeReservationRepository(),
        notificationRepository = FakeNotificationRepository(),
    )

    @Test
    fun seed_addsFourCampsitesAndThreeReservations() = runBlocking {
        SeedLoader(container, today).seed()

        assertEquals(4, container.campsiteRepository.getAllCampsites().size)
        val reservations = container.reservationRepository.getReservationsForUser("my-phone")
        assertEquals(3, reservations.size)
        assertEquals(2, reservations.count { it.isActive(today) })
    }

    @Test
    fun seed_givesTheUserAWinAndHoldsReservedNights() = runBlocking {
        SeedLoader(container, today).seed()

        val reservation = container.reservationRepository.getReservationsForUser("my-phone").first()
        val calendar = container.campsiteRepository.getAvailability(reservation.campsiteId)
        assertTrue(reservation.checkIn in calendar.heldNights)

        val jasper = container.campsiteRepository.getAllCampsites().first { it.region == Region.JASPER }
        val lottery = container.lotteryRepository.getLotteriesForCampsite(jasper.id).first()
        assertEquals(EntryStatus.WON, container.lotteryRepository.getEntry(lottery.id, "my-phone")?.status)
        assertEquals(EntryStatus.LOST, container.lotteryRepository.getEntry(lottery.id, "seed-entrant-sam")?.status)

        assertEquals(3, container.notificationRepository.getNotificationsFor("my-phone").size)
    }

    @Test
    fun seed_worksOnAnotherPhoneDaysLater() = runBlocking {
        SeedLoader(container, today).seed()

        // Other phones share the same database but seed 4 and 11 days later.
        for ((phone, daysLater) in listOf("phone-2" to 4L, "phone-3" to 11L)) {
            val otherPhone = AppContainer(
                deviceId = phone,
                profileRepository = container.profileRepository,
                campsiteRepository = container.campsiteRepository,
                lotteryRepository = container.lotteryRepository,
                reservationRepository = container.reservationRepository,
                notificationRepository = container.notificationRepository,
            )
            SeedLoader(otherPhone, today.plusDays(daysLater)).seed()
            assertEquals(3, container.reservationRepository.getReservationsForUser(phone).size)
        }
        assertEquals(4, container.campsiteRepository.getAllCampsites().size)
    }

    @Test
    fun seed_drawnLotteriesHaveOneWinner() = runBlocking {
        SeedLoader(container, today).seed()

        val banff = container.campsiteRepository.getAllCampsites().first { it.region == Region.BANFF }
        val lottery = container.lotteryRepository.getLotteriesForCampsite(banff.id).first()
        assertEquals(1, lottery.winnersCount)
        val winners = container.lotteryRepository.getEntries(lottery.id)
            .count { it.status == EntryStatus.WON || it.status == EntryStatus.RESERVED }
        assertEquals(1, winners)
    }

    @Test
    fun seed_twiceDoesNotAddDuplicates() = runBlocking {
        SeedLoader(container, today).seed()
        SeedLoader(container, today).seed()

        assertEquals(4, container.campsiteRepository.getAllCampsites().size)
        assertEquals(3, container.reservationRepository.getReservationsForUser("my-phone").size)
    }
}
