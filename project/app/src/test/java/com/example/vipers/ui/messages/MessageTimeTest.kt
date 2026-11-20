/*
 * MessageTimeTest.kt
 * Unit tests for messageTime, the "Mar 2 · 9:14 AM" text above each chat message (mockup E17,
 * US 02.12.01). Uses a fixed time zone so the tests don't depend on the computer's.
 * Known issues: none.
 */
package com.example.vipers.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MessageTimeTest {

    private val edmonton = ZoneId.of("America/Edmonton")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    @Test
    fun messageTime_morning() {
        assertEquals("Mar 2 · 9:14 AM", messageTime(at("2027-03-02T09:14"), edmonton))
    }

    @Test
    fun messageTime_afternoonUsesTwelveHourClock() {
        assertEquals("May 8 · 2:05 PM", messageTime(at("2027-05-08T14:05"), edmonton))
    }

    @Test
    fun messageTime_midnightAndNoon() {
        assertEquals("May 9 · 12:00 AM", messageTime(at("2027-05-09T00:00"), edmonton))
        assertEquals("May 9 · 12:00 PM", messageTime(at("2027-05-09T12:00"), edmonton))
    }

    @Test
    fun messageTime_usesTheGivenTimeZone() {
        // 9:14 AM in Edmonton is 4:14 PM in UTC
        assertEquals("Mar 2 · 4:14 PM", messageTime(at("2027-03-02T09:14"), ZoneId.of("UTC")))
    }
}
