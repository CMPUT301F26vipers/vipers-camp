/*
 * ShortDate.kt
 * Short date text for the UI, like the mockups' "Reserve by Apr 9" (US 01.04.01).
 * Known issues: none.
 */
package com.example.vipers.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** The day of [timestamp] (milliseconds since 1970) on this phone's clock, e.g. "May 31". */
fun shortDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(shortFormat)
