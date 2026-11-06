/*
 * LotteryTest.kt
 * Unit tests for working out a lottery's status from its registration and booking periods.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LotteryTest {

    /** A date and time in Edmonton, in milliseconds. */
    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.of("America/Edmonton")).toInstant().toEpochMilli()

    // Registration May 1 9:00 to May 10 15:00, booking May 12 to May 31 23:59.
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
        assertEquals(LotteryStatus.DRAWN, drawn.status(at("2027-05-31T23:59")))
    }

    @Test
    fun status_isClosedAfterBookingEnds() {
        assertEquals(LotteryStatus.CLOSED, drawn.status(at("2027-06-01T00:00")))
        assertEquals(LotteryStatus.CLOSED, lottery.status(at("2027-06-01T00:00")))
    }

    @Test
    fun status_drawnPrecedenceOverridesRegistrationPeriodWhenDrawnAtIsSet() {
        // When drawnAt is not null and now <= bookingEnd, status evaluates to DRAWN
        assertEquals(LotteryStatus.DRAWN, drawn.status(at("2027-05-01T08:00")))
    }

    @Test
    fun status_doesNotCrashWhenTimesAreNotSet() {
        assertEquals(LotteryStatus.CLOSED, Lottery().status(at("2027-05-01T09:00")))
    }

    @Test
    fun canEntrantJoin_returnsFalseBeforeRegistrationStarts() {
        assertFalse(lottery.canEntrantJoin(at("2027-05-01T08:59")))
    }

    @Test
    fun canEntrantJoin_returnsTrueAtAndDuringRegistrationPeriod() {
        assertTrue(lottery.canEntrantJoin(at("2027-05-01T09:00")))
        assertTrue(lottery.canEntrantJoin(at("2027-05-05T12:00")))
        assertTrue(lottery.canEntrantJoin(at("2027-05-10T15:00")))
    }

    @Test
    fun canEntrantJoin_returnsFalseAfterRegistrationEnds() {
        assertFalse(lottery.canEntrantJoin(at("2027-05-10T15:01")))
    }

    @Test
    fun canEntrantJoin_returnsFalseWhenTimesAreNotSet() {
        assertFalse(Lottery().canEntrantJoin(at("2027-05-01T09:00")))
    }

    @Test
    fun canEntrantJoin_returnsFalseWhenPeriodIsReversed() {
        val reversed = Lottery(registrationStart = 2000L, registrationEnd = 1000L)
        assertFalse(reversed.canEntrantJoin(1500L))
    }

    @Test
    fun isBookingOpen_returnsFalseBeforeBookingPeriod() {
        assertFalse(lottery.isBookingOpen(at("2027-05-11T23:59")))
    }

    @Test
    fun isBookingOpen_returnsTrueExactlyAtBookingStart() {
        assertTrue(lottery.isBookingOpen(at("2027-05-12T00:00")))
    }

    @Test
    fun isBookingOpen_returnsTrueDuringBookingPeriod() {
        assertTrue(lottery.isBookingOpen(at("2027-05-20T12:00")))
    }

    @Test
    fun isBookingOpen_returnsTrueExactlyAtBookingEnd() {
        assertTrue(lottery.isBookingOpen(at("2027-05-31T23:59")))
    }

    @Test
    fun isBookingOpen_returnsFalseAfterBookingEnd() {
        assertFalse(lottery.isBookingOpen(at("2027-06-01T00:00")))
    }

    @Test
    fun isBookingOpen_returnsFalseWhenBookingTimesNotSet() {
        assertFalse(Lottery().isBookingOpen(at("2027-05-20T12:00")))
    }

    @Test
    fun isBookingOpen_returnsFalseWhenPeriodIsReversed() {
        val reversed = Lottery(bookingStart = 2000L, bookingEnd = 1000L)
        assertFalse(reversed.isBookingOpen(1500L))
    }

    @Test
    fun isBookingOpen_returnsFalseWhenBookingStartAndEndAreEqual() {
        val equalTimes = Lottery(bookingStart = 2000L, bookingEnd = 2000L)
        assertFalse(equalTimes.isBookingOpen(2000L))
    }

    @Test
    fun isBookingOpen_returnsFalseWhenRemoved() {
        val removedLottery = lottery.copy(removed = true)
        assertFalse(removedLottery.isBookingOpen(at("2027-05-20T12:00")))
    }

    @Test
    fun isBookingExpired_returnsFalseBeforeAndDuringBooking() {
        assertFalse(lottery.isBookingExpired(at("2027-05-11T23:59")))
        assertFalse(lottery.isBookingExpired(at("2027-05-12T00:00")))
        assertFalse(lottery.isBookingExpired(at("2027-05-20T12:00")))
    }

    @Test
    fun isBookingExpired_returnsFalseExactlyAtBookingEnd() {
        assertFalse(lottery.isBookingExpired(at("2027-05-31T23:59")))
    }

    @Test
    fun isBookingExpired_returnsTrueStrictlyAfterBookingEnd() {
        assertTrue(lottery.isBookingExpired(at("2027-06-01T00:00")))
    }

    @Test
    fun isBookingExpired_returnsFalseWhenTimesNotSet() {
        assertFalse(Lottery().isBookingExpired(at("2027-06-01T00:00")))
    }

    @Test
    fun isBookingExpired_returnsFalseWhenPeriodIsReversed() {
        val reversed = Lottery(bookingStart = 2000L, bookingEnd = 1000L)
        assertFalse(reversed.isBookingExpired(2500L))
    }

    @Test
    fun isBookingExpired_returnsFalseWhenBookingStartAndEndAreEqual() {
        val equalTimes = Lottery(bookingStart = 2000L, bookingEnd = 2000L)
        assertFalse(equalTimes.isBookingExpired(2500L))
    }

    @Test
    fun isBookingExpired_returnsFalseWhenBookingStartNotSet() {
        val noStart = Lottery(bookingStart = 0L, bookingEnd = 1000L)
        assertFalse(noStart.isBookingExpired(1500L))
    }
}
