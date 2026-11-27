/*
 * QrScanResult.kt
 * What came back from the QR scanner on Home (US 01.12.01). MainActivity runs Google's code
 * scanner and turns its answer into one of these; tests pass their own instead of opening the
 * camera.
 * Known issues: none.
 */
package com.example.vipers.ui.home

/** The result of one Scan QR, as handed to CampsiteSearchController.onQrScanned. */
sealed class QrScanResult {

    /** The scanner read a code; [text] is what the code holds. */
    data class Scanned(val text: String) : QrScanResult()

    /** The user closed the scanner without scanning anything. */
    data object Cancelled : QrScanResult()

    /** The user didn't let the scanner use the camera. */
    data object NoCameraPermission : QrScanResult()

    /** The scanner couldn't open, e.g. Google Play services is missing or out of date. */
    data object Failed : QrScanResult()
}
