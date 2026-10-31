/*
 * LotteryTest.kt
 * Unit tests for working out a lottery's status from its dates.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LotteryTest {

    // Registration May 1–10, booking May 12–31.
    private val lottery = Lottery(
        registrationStart = "2027-05-01",
        registrationEnd = "2027-05-10",
        bookingStart = "2027-05-12",
        bookingEnd = "2027-05-31",
    )
    private val drawn = lottery.copy(drawnAt = 1_000L)

    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun status_isUpcomingBeforeRegistration() {
        assertEquals(LotteryStatus.UPCOMING, lottery.status(day("2027-04-28")))
    }

    @Test
    fun status_isOpenOnFirstAndLastRegistrationDay() {
        assertEquals(LotteryStatus.OPEN, lottery.status(day("2027-05-01")))
        assertEquals(LotteryStatus.OPEN, lottery.status(day("2027-05-10")))
    }

    @Test
    fun status_isClosedAfterRegistrationUntilDrawn() {
        assertEquals(LotteryStatus.CLOSED, lottery.status(day("2027-05-11")))
    }

    @Test
    fun status_isDrawnOnceDrawnUntilBookingEnds() {
        assertEquals(LotteryStatus.DRAWN, drawn.status(day("2027-05-20")))
    }

    @Test
    fun status_isClosedAfterBookingEnds() {
        assertEquals(LotteryStatus.CLOSED, drawn.status(day("2027-06-01")))
    }
}
