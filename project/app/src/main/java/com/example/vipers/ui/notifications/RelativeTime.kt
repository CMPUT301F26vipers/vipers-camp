/*
 * RelativeTime.kt
 * Turns a notification's send time into the short text the inbox shows, e.g. "2m", "1d" or
 * "Mar 2", as in mockup E10 (T-13).
 * Known issues: none. Times in the future (a phone clock that's behind) show as "Just now".
 */
package com.example.vipers.ui.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

private val thisYearFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val otherYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * How long before [now] the time [createdAt] was, both in milliseconds since 1970, in the style of
 * mockup E10 (T-13): "Just now" under a minute, "5m" under an hour, "3h" under a day, "1d" to "6d"
 * under a week, and otherwise the date, e.g. "Mar 2", with the year added ("Dec 31, 2026") if it
 * isn't [now]'s year. The date and year are taken in [zone].
 */
fun relativeTime(createdAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = now - createdAt
    if (elapsed < MINUTE) return "Just now"
    if (elapsed < HOUR) return "${elapsed / MINUTE}m"
    if (elapsed < DAY) return "${elapsed / HOUR}h"
    if (elapsed < WEEK) return "${elapsed / DAY}d"

    val created = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (created.year == today.year) {
        created.format(thisYearFormat)
    } else {
        created.format(otherYearFormat)
    }
}
