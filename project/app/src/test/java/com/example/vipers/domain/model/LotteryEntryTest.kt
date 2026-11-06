/*
 * LotteryEntryTest.kt
 * Unit tests for a lottery entry's eligibility to reserve or decline (US 01.04.03) and whether its
 * win has expired.
 * Known issues: none.
 */
package com.example.vipers.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LotteryEntryTest {

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(ZoneId.of("America/Edmonton")).toInstant().toEpochMilli()

    private val lottery = Lottery(
        id = "lottery-1",
        registrationStart = at("2027-05-01T09:00"),
        registrationEnd = at("2027-05-10T15:00"),
        bookingStart = at("2027-05-12T00:00"),
        bookingEnd = at("2027-05-31T23:59"),
    )

    private val wonEntry = LotteryEntry(
        id = "lottery-1_user-1",
        lotteryId = "lottery-1",
        userId = "user-1",
        status = EntryStatus.WON,
    )

    @Test
    fun canReserve_returnsFalseBeforeBookingStarts() {
        assertFalse(wonEntry.canReserve(lottery, at("2027-05-11T23:59")))
    }

    @Test
    fun canReserve_returnsTrueExactlyAtBookingStart() {
        assertTrue(wonEntry.canReserve(lottery, at("2027-05-12T00:00")))
    }

    @Test
    fun canReserve_returnsTrueDuringBooking() {
        assertTrue(wonEntry.canReserve(lottery, at("2027-05-20T12:00")))
    }

    @Test
    fun canReserve_returnsTrueExactlyAtBookingEnd() {
        assertTrue(wonEntry.canReserve(lottery, at("2027-05-31T23:59")))
    }

    @Test
    fun canReserve_returnsFalseAfterBookingEnds() {
        assertFalse(wonEntry.canReserve(lottery, at("2027-06-01T00:00")))
    }

    @Test
    fun canReserve_returnsFalseForNonWinningStatuses() {
        val duringBooking = at("2027-05-20T12:00")
        val nonWinningStatuses = listOf(
            EntryStatus.WAITING,
            EntryStatus.LOST,
            EntryStatus.RESERVED,
            EntryStatus.DECLINED,
            EntryStatus.EXPIRED,
            EntryStatus.REMOVED,
        )
        for (status in nonWinningStatuses) {
            val entry = wonEntry.copy(status = status)
            assertFalse(
                "Entry with status $status should not be allowed to reserve",
                entry.canReserve(lottery, duringBooking)
            )
        }
    }

    @Test
    fun isExpired_returnsFalseBeforeBookingStarts() {
        assertFalse(wonEntry.isExpired(lottery, at("2027-05-11T23:59")))
    }

    @Test
    fun isExpired_returnsFalseExactlyAtBookingStart() {
        assertFalse(wonEntry.isExpired(lottery, at("2027-05-12T00:00")))
    }

    @Test
    fun isExpired_returnsFalseDuringBooking() {
        assertFalse(wonEntry.isExpired(lottery, at("2027-05-20T12:00")))
    }

    @Test
    fun isExpired_returnsFalseExactlyAtBookingEnd() {
        assertFalse(wonEntry.isExpired(lottery, at("2027-05-31T23:59")))
    }

    @Test
    fun isExpired_returnsTrueAfterBookingEnds() {
        assertTrue(wonEntry.isExpired(lottery, at("2027-06-01T00:00")))
    }

    @Test
    fun isExpired_returnsFalseForNonWinningStatuses() {
        val afterBooking = at("2027-06-01T00:00")
        val nonWinningStatuses = listOf(
            EntryStatus.WAITING,
            EntryStatus.LOST,
            EntryStatus.RESERVED,
            EntryStatus.DECLINED,
            EntryStatus.EXPIRED,
            EntryStatus.REMOVED,
        )
        for (status in nonWinningStatuses) {
            val entry = wonEntry.copy(status = status)
            assertFalse(
                "Entry with status $status should not evaluate isExpired as true",
                entry.isExpired(lottery, afterBooking)
            )
        }
    }

    // US 01.04.03: declining a win

    @Test
    fun canDecline_wonDuringBooking_returnsTrue() {
        assertTrue(wonEntry.canDecline(lottery, at("2027-05-20T12:00")))
    }

    @Test
    fun canDecline_wonAtBookingStartAndEnd_returnsTrue() {
        assertTrue(wonEntry.canDecline(lottery, at("2027-05-12T00:00")))
        assertTrue(wonEntry.canDecline(lottery, at("2027-05-31T23:59")))
    }

    @Test
    fun canDecline_beforeBookingStart_returnsFalse() {
        assertFalse(wonEntry.canDecline(lottery, at("2027-05-11T23:59")))
    }

    @Test
    fun canDecline_afterBookingEnd_returnsFalse() {
        assertFalse(wonEntry.canDecline(lottery, at("2027-06-01T00:00")))
    }

    @Test
    fun canDecline_reservedDeclinedLostOrExpired_returnsFalse() {
        val duringBooking = at("2027-05-20T12:00")
        val statuses = listOf(
            EntryStatus.RESERVED,
            EntryStatus.DECLINED,
            EntryStatus.LOST,
            EntryStatus.EXPIRED,
        )
        for (status in statuses) {
            assertFalse(
                "Entry with status $status should not be allowed to decline",
                wonEntry.copy(status = status).canDecline(lottery, duringBooking),
            )
        }
    }

    @Test
    fun canLeave_whenWaitingAndNotDrawn_returnsTrue() {
        val waitingEntry = wonEntry.copy(status = EntryStatus.WAITING)
        val undrawnLottery = lottery.copy(drawnAt = null)
        assertTrue(waitingEntry.canLeave(undrawnLottery))
    }

    @Test
    fun canLeave_whenLotteryAlreadyDrawn_returnsFalse() {
        val waitingEntry = wonEntry.copy(status = EntryStatus.WAITING)
        val drawnLottery = lottery.copy(drawnAt = at("2027-05-11T10:00"))
        assertFalse(waitingEntry.canLeave(drawnLottery))
    }

    @Test
    fun canLeave_whenStatusNotWaiting_returnsFalse() {
        val undrawnLottery = lottery.copy(drawnAt = null)
        val nonWaitingStatuses = listOf(
            EntryStatus.WON,
            EntryStatus.LOST,
            EntryStatus.RESERVED,
            EntryStatus.DECLINED,
            EntryStatus.EXPIRED,
            EntryStatus.REMOVED,
        )
        for (status in nonWaitingStatuses) {
            val entry = wonEntry.copy(status = status)
            assertFalse(
                "Entry with status $status should not be allowed to leave",
                entry.canLeave(undrawnLottery)
            )
        }
    }
}
