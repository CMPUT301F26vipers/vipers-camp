/*
 * CsvExporterTest.kt
 * Unit tests for CsvExporter formatting and RFC 4180 escaping rules (US 01.05.02).
 * Known issues: none.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {

    private val sampleReservation = Reservation(
        id = "res-123",
        reservationNumber = "R-20417",
        userId = "user-1",
        ownerId = "owner-1",
        campsiteId = "camp-1",
        campsiteName = "Two Jack Main",
        checkIn = "2026-07-01",
        checkOut = "2026-07-05",
        groupSize = 4,
        totalPriceCents = 20000,
        status = ReservationStatus.CONFIRMED,
    )

    private val sampleCampsite = Campsite(
        id = "camp-1",
        name = "Two Jack Main",
        address = "Banff National Park",
    )

    @Test
    fun toCsv_headerMatchesSpecification() {
        val csv = CsvExporter.toCsv(sampleReservation, sampleCampsite)
        val header = csv.lines().first()
        val expectedHeader = "Reservation Number,Campsite Name,Location,Check-in,Check-out," +
            "Group Size,Total Price,Status"
        assertEquals(expectedHeader, header)
    }

    @Test
    fun toCsv_standardReservation_formatsCorrectly() {
        val csv = CsvExporter.toCsv(sampleReservation, sampleCampsite)
        val expected = "Reservation Number,Campsite Name,Location,Check-in,Check-out," +
            "Group Size,Total Price,Status\n" +
            "R-20417,Two Jack Main,Banff National Park,2026-07-01,2026-07-05,4,$200,CONFIRMED\n"
        assertEquals(expected, csv)
    }

    @Test
    fun toCsv_escapesCommasInFields() {
        val campsite = sampleCampsite.copy(
            name = "Camp Two, Loop B",
            address = "Hinton, AB",
        )
        val csv = CsvExporter.toCsv(sampleReservation, campsite)
        val lines = csv.lines()
        val dataRow = lines[1]
        val expectedRow =
            "R-20417,\"Camp Two, Loop B\",\"Hinton, AB\",2026-07-01,2026-07-05,4,$200,CONFIRMED"
        assertEquals(expectedRow, dataRow)
    }

    @Test
    fun toCsv_escapesDoubleQuotesInFields() {
        val campsite = sampleCampsite.copy(
            name = "Camp \"Paradise\"",
        )
        val csv = CsvExporter.toCsv(sampleReservation, campsite)
        val dataRow = csv.lines()[1]
        val expectedRow = "R-20417,\"Camp \"\"Paradise\"\"\"," +
            "Banff National Park,2026-07-01,2026-07-05,4,$200,CONFIRMED"
        assertEquals(expectedRow, dataRow)
    }

    @Test
    fun toCsv_escapesNewlinesInFields() {
        val campsite = sampleCampsite.copy(
            address = "Site 12\nLoop A",
        )
        val csv = CsvExporter.toCsv(sampleReservation, campsite)
        // With an embedded newline, the escaped field spans two physical lines.
        assertEquals(true, csv.contains("\"Site 12\nLoop A\""))
    }

    @Test
    fun toCsv_fallsBackToReservationCampsiteName() {
        val campsiteWithoutName = sampleCampsite.copy(name = "")
        val csv = CsvExporter.toCsv(sampleReservation, campsiteWithoutName)
        val dataRow = csv.lines()[1]
        assertEquals(true, dataRow.contains("Two Jack Main"))
    }

    @Test
    fun toCsv_formatsStatusCorrectly_forCancelledAndRejected() {
        val cancelled = sampleReservation.copy(status = ReservationStatus.CANCELLED)
        val cancelledCsv = CsvExporter.toCsv(cancelled, sampleCampsite)
        assertEquals(true, cancelledCsv.lines()[1].endsWith(",CANCELLED"))

        val rejected = sampleReservation.copy(status = ReservationStatus.REJECTED)
        val rejectedCsv = CsvExporter.toCsv(rejected, sampleCampsite)
        assertEquals(true, rejectedCsv.lines()[1].endsWith(",REJECTED"))
    }

    @Test
    fun escapeCsvField_leavesPlainFieldsUnchanged() {
        assertEquals("Plain text", CsvExporter.escapeCsvField("Plain text"))
        assertEquals("", CsvExporter.escapeCsvField(""))
        assertEquals("12345", CsvExporter.escapeCsvField("12345"))
    }

    @Test
    fun escapeCsvField_escapesQuotesAndCommasTogether() {
        val input = "Lake \"Emerald\", AB"
        val expected = "\"Lake \"\"Emerald\"\", AB\""
        assertEquals(expected, CsvExporter.escapeCsvField(input))
    }
}
