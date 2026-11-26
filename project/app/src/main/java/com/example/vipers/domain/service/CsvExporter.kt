/*
 * CsvExporter.kt
 * Builds RFC 4180 compliant CSV representations of reservations for external download
 * (US 01.05.02, US 01.07.02).
 * Known issues: none.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Reservation

/**
 * Exports reservation data to CSV format conforming to RFC 4180.
 *
 * Provides a standardized header and handles escaping of commas, quotes, and newlines so the
 * resulting file can be opened reliably by external spreadsheet applications (US 01.05.02).
 */
object CsvExporter {

    /** The standard CSV column headers. */
    const val HEADER =
        "Reservation Number,Campsite Name,Location,Check-in,Check-out,Group Size,Total Price,Status"

    /**
     * Converts a [reservation] and its corresponding [campsite] into a two-line CSV string
     * with the column headers and the reservation's details.
     *
     * @param reservation The reservation details to export.
     * @param campsite The campsite associated with the reservation.
     * @return A valid CSV string ending with a newline.
     */
    fun toCsv(reservation: Reservation, campsite: Campsite): String {
        val campsiteName = campsite.name.ifBlank { reservation.campsiteName }
        val fields = listOf(
            escapeCsvField(reservation.reservationNumber),
            escapeCsvField(campsiteName),
            escapeCsvField(campsite.address),
            escapeCsvField(reservation.checkIn),
            escapeCsvField(reservation.checkOut),
            escapeCsvField(reservation.groupSize.toString()),
            escapeCsvField(reservation.totalPriceText),
            escapeCsvField(reservation.status.name),
        )
        val row = fields.joinToString(",")
        return "$HEADER\n$row\n"
    }

    /**
     * Escapes a single CSV field according to RFC 4180.
     *
     * If the field contains commas, double quotes, carriage returns, or line feeds,
     * internal double quotes are doubled and the entire value is wrapped in quotes.
     */
    fun escapeCsvField(value: String): String {
        val needsQuotes = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
