/*
 * ScanQrCodeTest.kt
 * Unit tests for US 01.12.01: CampsiteSearchController.onQrScanned opens our campsite codes, and
 * says why for anything else, without needing a camera.
 * Known issues: none.
 */
package com.example.vipers.ui.home

import com.example.vipers.data.fake.FakeCampsiteRepository
import com.example.vipers.data.fake.FakeLotteryRepository
import com.example.vipers.domain.model.Campsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanQrCodeTest {

    private val hinton = Campsite(id = "hinton", name = "Hinton Ridge Campground")

    private fun controller() =
        CampsiteSearchController(FakeCampsiteRepository(listOf(hinton)), FakeLotteryRepository())

    @Test
    fun scanningACampsiteCode_returnsItsId() {
        val controller = controller()

        val campsiteId = controller.onQrScanned(QrScanResult.Scanned(hinton.qrLink()))

        assertEquals("hinton", campsiteId)
        assertNull(controller.scanMessage)
    }

    @Test
    fun scanningAnUnknownOrRemovedCampsitesCode_stillReturnsItsId() {
        // Campsite Details is the one that says "Campsite not found."
        val controller = controller()

        val campsiteId = controller.onQrScanned(QrScanResult.Scanned("vipers-camp://campsite/gone"))

        assertEquals("gone", campsiteId)
        assertNull(controller.scanMessage)
    }

    @Test
    fun scanningAnotherAppsCode_saysItIsNotValid() {
        val controller = controller()

        val campsiteId = controller.onQrScanned(QrScanResult.Scanned("https://example.com/menu"))

        assertNull(campsiteId)
        assertEquals("Not a valid campsite code", controller.scanMessage)
    }

    @Test
    fun scanningAnEmptyCode_saysItIsNotValid() {
        val controller = controller()

        assertNull(controller.onQrScanned(QrScanResult.Scanned("")))
        assertEquals("Not a valid campsite code", controller.scanMessage)
    }

    @Test
    fun closingTheScanner_saysNothing() {
        val controller = controller()

        assertNull(controller.onQrScanned(QrScanResult.Cancelled))
        assertNull(controller.scanMessage)
    }

    @Test
    fun cameraPermissionDenied_explainsHowToAllowIt() {
        val controller = controller()

        assertNull(controller.onQrScanned(QrScanResult.NoCameraPermission))
        assertEquals(
            "Scan QR needs the camera. Allow camera access in your phone's " +
                "Settings, then try again.",
            controller.scanMessage,
        )
    }

    @Test
    fun scannerThatCantOpen_saysSo() {
        val controller = controller()

        assertNull(controller.onQrScanned(QrScanResult.Failed))
        assertEquals("Couldn't open the scanner. Try again.", controller.scanMessage)
    }

    @Test
    fun aGoodScanAfterABadOne_clearsTheOldMessage() {
        val controller = controller()
        controller.onQrScanned(QrScanResult.Scanned("not ours"))

        controller.onQrScanned(QrScanResult.Scanned(hinton.qrLink()))

        assertNull(controller.scanMessage)
    }

    @Test
    fun closingTheScannerAfterABadScan_clearsTheOldMessage() {
        val controller = controller()
        controller.onQrScanned(QrScanResult.Failed)

        controller.onQrScanned(QrScanResult.Cancelled)

        assertNull(controller.scanMessage)
    }

    @Test
    fun searchingOrFiltering_clearsTheOldMessage() {
        val controller = controller()
        controller.onQrScanned(QrScanResult.Failed)
        controller.onSearchTextChange("hin")
        assertNull(controller.scanMessage)

        controller.onQrScanned(QrScanResult.Failed)
        controller.applyFilters(minGuests = 2)
        assertNull(controller.scanMessage)

        controller.onQrScanned(QrScanResult.Failed)
        controller.clearFilters()
        assertNull(controller.scanMessage)
    }

    @Test
    fun clearScanMessage_removesTheMessage() {
        val controller = controller()
        controller.onQrScanned(QrScanResult.Failed)

        controller.clearScanMessage()

        assertNull(controller.scanMessage)
    }
}
