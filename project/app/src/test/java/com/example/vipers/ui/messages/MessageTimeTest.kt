/*
 * MessageTimeTest.kt
 * Unit tests for messageTime, the "Mar 2 · 9:14 AM" text above each chat message (mockup E17,
 * US 02.12.01). Uses fixed UTC offsets, not real places like America/Edmonton, so the tests don't
 * depend on the computer's time zone or on how up to date its time zone rules are.
 * Known issues: none.
 */
package com.example.vipers.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MessageTimeTest {

    // Mountain Standard Time, seven hours behind UTC
    private val mountain = ZoneOffset.ofHours(-7)

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(mountain).toInstant().toEpochMilli()

    @Test
    fun messageTime_morning() {
        assertEquals("Mar 2 · 9:14 AM", messageTime(at("2027-03-02T09:14"), mountain))
    }

    @Test
    fun messageTime_afternoonUsesTwelveHourClock() {
        assertEquals("May 8 · 2:05 PM", messageTime(at("2027-05-08T14:05"), mountain))
    }

    @Test
    fun messageTime_midnightAndNoon() {
        assertEquals("May 9 · 12:00 AM", messageTime(at("2027-05-09T00:00"), mountain))
        assertEquals("May 9 · 12:00 PM", messageTime(at("2027-05-09T12:00"), mountain))
    }

    @Test
    fun messageTime_usesTheGivenTimeZone() {
        // 9:14 AM seven hours behind UTC is 4:14 PM in UTC
        assertEquals("Mar 2 · 4:14 PM", messageTime(at("2027-03-02T09:14"), ZoneOffset.UTC))
    }
}
