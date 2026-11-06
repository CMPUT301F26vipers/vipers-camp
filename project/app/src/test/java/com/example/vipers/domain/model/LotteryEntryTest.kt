/*
 * LotteryEntryTest.kt
 * Unit tests for a lottery entry's eligibility to reserve and whether its win has expired.
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
}
