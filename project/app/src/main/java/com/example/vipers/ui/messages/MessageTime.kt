/*
 * MessageTime.kt
 * The time shown above each chat message, e.g. "Mar 2 · 9:14 AM", as in mockup E17
 * (US 02.12.01). The admin's conversation view (03.12.01) can use it too.
 * Known issues: none. The year isn't shown, like the mockup.
 */
package com.example.vipers.ui.messages

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val messageTimeFormat = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

/**
 * The day and time of [createdAt] (milliseconds since 1970) in [zone], e.g. "Mar 2 · 9:14 AM"
 * (mockup E17, US 02.12.01).
 */
fun messageTime(createdAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(createdAt).atZone(zone).format(messageTimeFormat)
