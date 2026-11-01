/*
 * LotteryTest.kt
 * Unit tests for working out a lottery's status from its registration and booking periods.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LotteryTest {

    /** A date and time in Edmonton, in milliseconds. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.of("America/Edmonton")).toInstant().toEpochMilli()

    // Registration May 1 9:00 to May 10 15:00, booking May 12 to May 31.
    private val lottery = Lottery(
        registrationStart = at("2027-05-01T09:00"),
        registrationEnd = at("2027-05-10T15:00"),
        bookingStart = at("2027-05-12T00:00"),
        bookingEnd = at("2027-05-31T23:59"),
    )
    private val drawn = lottery.copy(drawnAt = at("2027-05-10T15:15"))

    @Test
    fun status_isUpcomingBeforeRegistration() {
        assertEquals(LotteryStatus.UPCOMING, lottery.status(at("2027-05-01T08:59")))
    }

    @Test
    fun status_isOpenDuringRegistration() {
        assertEquals(LotteryStatus.OPEN, lottery.status(at("2027-05-01T09:00")))
        assertEquals(LotteryStatus.OPEN, lottery.status(at("2027-05-10T15:00")))
    }

    @Test
    fun status_closesAtTheRegistrationEndTimeNotMidnight() {
        assertEquals(LotteryStatus.CLOSED, lottery.status(at("2027-05-10T15:01")))
    }

    @Test
    fun status_isDrawnOnceDrawnUntilBookingEnds() {
        assertEquals(LotteryStatus.DRAWN, drawn.status(at("2027-05-20T12:00")))
    }

    @Test
    fun status_isClosedAfterBookingEnds() {
        assertEquals(LotteryStatus.CLOSED, drawn.status(at("2027-06-01T00:00")))
    }

    @Test
    fun status_doesNotCrashWhenTimesAreNotSet() {
        assertEquals(LotteryStatus.CLOSED, Lottery().status(at("2027-05-01T09:00")))
    }
}
