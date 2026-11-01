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
    fun seed_twiceDoesNotAddDuplicates() = runBlocking {
        SeedLoader(container, today).seed()
        SeedLoader(container, today).seed()

        assertEquals(4, container.campsiteRepository.getAllCampsites().size)
        assertEquals(3, container.reservationRepository.getReservationsForUser("my-phone").size)
    }
}
