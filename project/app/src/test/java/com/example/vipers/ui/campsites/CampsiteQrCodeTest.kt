/*
 * CampsiteQrCodeTest.kt
 * Unit tests for drawing a campsite's QR code (US 02.02.01): the code scans back to the
 * campsite's link, it is the same after edits, and the suggested file name is safe to save.
 * Known issues: the PNG itself needs Android, so saving it is tested in GenerateCampsiteQrCodeTest.
 */
package com.example.vipers.ui.campsites

import com.example.vipers.domain.model.Campsite
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CampsiteQrCodeTest {

    private val hinton = Campsite(id = "hinton", name = "Hinton Ridge Campground")

    /** Reads [matrix] the way a phone camera would and returns the text in it. */
    private fun scan(matrix: BitMatrix): String {
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xFF000000.toInt() else -1
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun qrCodeMatrix_scansBackToTheCampsiteLink() {
        val matrix = qrCodeMatrix(hinton.qrLink(), QR_SHOWN_SIZE)

        assertEquals("vipers-camp://campsite/hinton", scan(matrix))
    }

    @Test
    fun qrCodeMatrix_withALongFirestoreId_stillScans() {
        val campsite = Campsite(id = "Zq8vT2mXr4LpB7wNc1Hd")

        assertEquals(campsite.qrLink(), scan(qrCodeMatrix(campsite.qrLink(), QR_SAVED_SIZE)))
    }

    @Test
    fun qrCodeMatrix_isTheRequestedSize() {
        val matrix = qrCodeMatrix(hinton.qrLink(), QR_SAVED_SIZE)

        assertEquals(QR_SAVED_SIZE, matrix.width)
        assertEquals(QR_SAVED_SIZE, matrix.height)
    }

    @Test
    fun qrCodeMatrix_leavesAWhiteBorder() {
        val matrix = qrCodeMatrix(hinton.qrLink(), QR_SHOWN_SIZE)

        // the corners are outside the code, so phones can find its edge
        assertFalse(matrix[0, 0])
        assertFalse(matrix[QR_SHOWN_SIZE - 1, QR_SHOWN_SIZE - 1])
    }

    @Test
    fun qrCodeMatrix_isTheSameAfterTheCampsiteIsEdited() {
        val edited = hinton.copy(name = "Hinton Ridge", pricePerNightCents = 7000)

        assertEquals(
            qrCodeMatrix(hinton.qrLink(), QR_SHOWN_SIZE),
            qrCodeMatrix(edited.qrLink(), QR_SHOWN_SIZE),
        )
    }

    @Test
    fun qrCodeMatrix_isDifferentForAnotherCampsite() {
        val jasper = Campsite(id = "jasper", name = "Jasper Lakeside Sites")

        assertNotEquals(
            qrCodeMatrix(hinton.qrLink(), QR_SHOWN_SIZE),
            qrCodeMatrix(jasper.qrLink(), QR_SHOWN_SIZE),
        )
    }

    @Test
    fun qrFileName_usesTheCampsiteName() {
        assertEquals("Hinton Ridge Campground QR code.png", qrFileName(hinton.name))
    }

    @Test
    fun qrFileName_replacesCharactersFileNamesCantHave() {
        assertEquals("Bow River  Site 4 QR code.png", qrFileName("Bow River: Site 4"))
        assertEquals("Lake A B QR code.png", qrFileName("Lake A/B"))
    }

    @Test
    fun qrFileName_withNoName_usesAPlainName() {
        assertEquals("Campsite QR code.png", qrFileName("  "))
        assertEquals("Campsite QR code.png", qrFileName("?"))
    }
}
