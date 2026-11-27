/*
 * CampsiteQrCode.kt
 * Draws a campsite's QR code (US 02.02.01) with ZXing core, and writes it as a PNG so the owner
 * can save it to the phone and print it on posters. The code holds Campsite.qrLink().
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.OutputStream

/** Size in pixels of the QR image shown in the dialog. */
const val QR_SHOWN_SIZE = 512

/** Size in pixels of the saved QR image; bigger so it stays sharp when printed. */
const val QR_SAVED_SIZE = 1024

/**
 * The QR code for [link] as a [size] by [size] grid, where true is a black square. Keeps a white
 * border around it so phones can find the code on a poster. Doesn't use Android, so unit tests
 * can check it.
 */
fun qrCodeMatrix(link: String, size: Int): BitMatrix {
    val hints = mapOf(
        // M still scans with a bit of the poster torn or dirty
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    return QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size, hints)
}

/** The QR code for [link] as a black and white image, [size] pixels on each side. */
fun qrCodeBitmap(link: String, size: Int): Bitmap {
    val matrix = qrCodeMatrix(link, size)
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * Writes the QR code for [link] to [output] as a PNG of [QR_SAVED_SIZE] pixels. Returns false if
 * the image couldn't be written. Doesn't close [output].
 */
fun writeQrCodePng(link: String, output: OutputStream): Boolean {
    return qrCodeBitmap(link, QR_SAVED_SIZE).compress(Bitmap.CompressFormat.PNG, 100, output)
}

/** The file name the save picker suggests, e.g. "Hinton Ridge Campground QR code.png". */
fun qrFileName(campsiteName: String): String {
    // characters some phones don't allow in file names
    val safeName = campsiteName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
    return if (safeName.isEmpty()) "Campsite QR code.png" else "$safeName QR code.png"
}
