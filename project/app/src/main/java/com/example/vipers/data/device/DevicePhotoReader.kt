/*
 * DevicePhotoReader.kt
 * The real PhotoReader: opens a photo the user picked with the phone's photo picker, shrinks it so
 * its longest side is at most MAX_SIDE pixels, and saves it as a JPEG, lowering the quality until
 * it is small enough to store in Firestore (US 02.01.02b). Uses only Android's BitmapFactory.
 * Known issues: the photo's rotation (EXIF) isn't read, so a few phone photos may show sideways.
 */
package com.example.vipers.data.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.vipers.domain.repository.PhotoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Reads picked photos with the [context]'s ContentResolver. */
class DevicePhotoReader(context: Context) : PhotoReader {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray =
        withContext(Dispatchers.IO) { shrink(Uri.parse(uri), maxBytes) }

    // Reads the photo, shrinks it and compresses it until it is at most maxBytes.
    private fun shrink(photo: Uri, maxBytes: Int): ByteArray {
        // First read only the size, so a big photo is loaded already shrunk.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // (decodeStream returns null here on purpose; it only fills in bounds)
        val stream = resolver.openInputStream(photo) ?: throw IOException("Can't open the photo")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(photo)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Can't read the photo")
        val scaled = scaleDown(decoded)
        var quality = START_QUALITY
        var jpeg = compress(scaled, quality)
        while (jpeg.size > maxBytes && quality > MIN_QUALITY) {
            quality -= 10
            jpeg = compress(scaled, quality)
        }
        if (jpeg.size > maxBytes) throw IOException("Photo is too big")
        return jpeg
    }

    // The bitmap shrunk so its longest side is at most MAX_SIDE pixels.
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE) return bitmap
        val ratio = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt(),
            (bitmap.height * ratio).roundToInt(),
            true,
        )
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    companion object {
        /** The longest side a saved photo can have, in pixels. */
        const val MAX_SIDE = 1024
        private const val START_QUALITY = 80
        private const val MIN_QUALITY = 30
    }
}
