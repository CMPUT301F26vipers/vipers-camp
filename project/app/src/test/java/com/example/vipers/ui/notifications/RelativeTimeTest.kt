/*
 * RelativeTimeTest.kt
 * Unit tests for relativeTime, the send time shown on each inbox item in the style of mockup E10
 * (T-13), with a fixed "now".
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RelativeTimeTest {

    // 3 PM UTC on Monday, May 10, 2027
    private val now = at("2027-05-10T15:00:00Z")

    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour

    private fun at(time: String) = Instant.parse(time).toEpochMilli()

    private fun ago(millis: Long) = relativeTime(now - millis, now, ZoneOffset.UTC)

    @Test
    fun relativeTime_underAMinute_isJustNow() {
        assertEquals("Just now", ago(0))
        assertEquals("Just now", ago(59 * second))
    }

    @Test
    fun relativeTime_inTheFuture_isJustNow() {
        assertEquals("Just now", ago(-5 * minute))
    }

    @Test
    fun relativeTime_underAnHour_isWholeMinutes() {
        assertEquals("1m", ago(minute))
        assertEquals("2m", ago(2 * minute + 30 * second))
        assertEquals("59m", ago(59 * minute + 59 * second))
    }

    @Test
    fun relativeTime_underADay_isWholeHours() {
        assertEquals("1h", ago(hour))
        assertEquals("3h", ago(3 * hour + 20 * minute))
        assertEquals("23h", ago(23 * hour + 59 * minute))
    }

    @Test
    fun relativeTime_underAWeek_isWholeDays() {
        assertEquals("1d", ago(day))
        assertEquals("1d", ago(day + 23 * hour))
        assertEquals("6d", ago(7 * day - minute))
    }

    @Test
    fun relativeTime_aWeekOrMoreThisYear_isMonthAndDay() {
        assertEquals("May 3", ago(7 * day))
        assertEquals("Mar 2", relativeTime(at("2027-03-02T12:00:00Z"), now, ZoneOffset.UTC))
    }

    @Test
    fun relativeTime_anEarlierYear_addsTheYear() {
        assertEquals("Dec 31, 2026", relativeTime(at("2026-12-31T12:00:00Z"), now, ZoneOffset.UTC))
    }

    @Test
    fun relativeTime_inAnotherZone_usesTheDateAndYearThere() {
        // 2 AM UTC on Jan 1 is still 7 PM on Dec 31 in Edmonton
        val sent = at("2027-01-01T02:00:00Z")
        assertEquals("Jan 1", relativeTime(sent, now, ZoneOffset.UTC))
        assertEquals("Dec 31, 2026", relativeTime(sent, now, ZoneId.of("America/Edmonton")))
    }
}
