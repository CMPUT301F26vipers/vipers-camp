/*
 * LotteriesToDrawTest.kt
 * Unit tests for the fake LotteryRepository's draw helpers (US 02.05.01b): finding the lotteries
 * the app should draw, and closing registration early when the owner draws before it ends.
 * Known issues: the Firestore versions need a real database, so they aren't tested here.
 */
package com.example.vipers.data

import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.JoinWaitingListResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteriesToDrawTest {

    // registration runs from 1000 to 2000, and booking from 3000 to 4000
    private val may = Lottery(
        id = "may",
        ownerId = "owner-priya",
        registrationStart = 1000,
        registrationEnd = 2000,
        bookingStart = 3000,
        bookingEnd = 4000,
    )
    private val june = may.copy(id = "june", registrationEnd = 2600)
    private val drawn = may.copy(id = "drawn", drawnAt = 2100)
    private val removed = may.copy(id = "removed", removed = true)

    @Test
    fun getLotteriesToDraw_findsOnlyLotteriesWhoseRegistrationEnded() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may, june, drawn, removed))

        val due = repository.getLotteriesToDraw(now = 2500)

        assertEquals(listOf("may"), due.map { it.id })
    }

    @Test
    fun getLotteriesToDraw_beforeAnyRegistrationEnds_isEmpty() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may, june))

        assertTrue(repository.getLotteriesToDraw(now = 1500).isEmpty())
    }

    @Test
    fun closeRegistration_whileOpen_movesTheEndToNow() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may))

        assertTrue(repository.closeRegistration("may", now = 1500))

        assertEquals(1500L, repository.getLottery("may")?.registrationEnd)
    }

    @Test
    fun closeRegistration_stopsNewEntrantsJoining() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may))
        repository.closeRegistration("may", now = 1500)

        val result = repository.joinWaitingList(
            LotteryEntry(id = LotteryEntry.idFor("may", "sam"), lotteryId = "may", userId = "sam"),
            now = 1600,
        )

        assertTrue(result is JoinWaitingListResult.Error)
    }

    @Test
    fun closeRegistration_afterItEnded_keepsTheOriginalEnd() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may))

        assertTrue(repository.closeRegistration("may", now = 2500))

        assertEquals(2000L, repository.getLottery("may")?.registrationEnd)
    }

    @Test
    fun closeRegistration_beforeRegistrationStarts_changesNothing() = runBlocking {
        val repository = FakeLotteryRepository(listOf(may))

        assertFalse(repository.closeRegistration("may", now = 500))

        assertEquals(2000L, repository.getLottery("may")?.registrationEnd)
    }

    @Test
    fun closeRegistration_onceDrawn_isFalse() = runBlocking {
        val repository = FakeLotteryRepository(listOf(drawn))

        assertFalse(repository.closeRegistration("drawn", now = 1500))
    }

    @Test
    fun closeRegistration_missingLottery_isFalse() = runBlocking {
        assertFalse(FakeLotteryRepository().closeRegistration("missing", now = 1500))
    }
}
