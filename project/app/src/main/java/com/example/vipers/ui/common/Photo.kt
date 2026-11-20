/*
 * Photo.kt
 * Shared composables that draw a saved photo by its image ID, or a photo just picked, cropped to
 * fill their box, with a green placeholder and icon when there's no photo (US 02.01.02b). Used for
 * campsite photos on the form (O02, O05), My Campsite (O04), Campsite Details (E05) and the Home
 * cards (E02); profile photos (US 01.08.02) and the admin image browser (US 03.06.01) can reuse them.
 * Known issues: SavedPhoto reads the image through the app's ImageRepository itself, so every
 * screen can show photos without its controller loading them. Decoded photos are kept in a small
 * in-memory cache; an image never changes once saved (a new photo gets a new ID).
 */
package com.example.vipers.ui.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.vipers.CampApp
import com.example.vipers.R
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the saved photo with this [imageId] in a box of [modifier]'s size, cut to [shape]. Shows
 * the [placeholderIcon] on green while it loads, or if there's no photo or it can't be loaded.
 */
@Composable
fun SavedPhoto(
    imageId: String?,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    @DrawableRes placeholderIcon: Int = R.drawable.ic_image,
) {
    val app = LocalContext.current.applicationContext as? CampApp
    val photo by produceState(imageId?.let { PhotoCache.get(it) }, imageId) {
        if (imageId == null || value != null || app == null) return@produceState
        value = try {
            val image = app.container.imageRepository.getImage(imageId)
            image?.let { decodeJpeg(it.jpegBytes()) }?.also { PhotoCache.put(imageId, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // keep the placeholder
        }
    }
    PhotoBox(photo, contentDescription, shape, modifier, placeholderIcon)
}

/** Draws a photo given as [jpeg] bytes, e.g. one just picked on the campsite form. */
@Composable
fun JpegPhoto(
    jpeg: ByteArray,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val photo = remember(jpeg) { decodeBitmap(jpeg) }
    PhotoBox(photo, contentDescription, shape, modifier, R.drawable.ic_image)
}

// The photo, cropped to fill the box, or the placeholder icon on green if photo is null.
@Composable
private fun PhotoBox(
    photo: ImageBitmap?,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier,
    @DrawableRes placeholderIcon: Int,
) {
    Box(modifier.clip(shape).background(GreenFill), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(painterResource(placeholderIcon), contentDescription = null, tint = Primary)
        }
    }
}

// Decodes JPEG bytes off the main thread, or returns null if they aren't an image.
private suspend fun decodeJpeg(jpeg: ByteArray): ImageBitmap? =
    withContext(Dispatchers.Default) { decodeBitmap(jpeg) }

private fun decodeBitmap(jpeg: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()

// Recently shown photos by image ID, so going back to a list doesn't load them again.
private object PhotoCache {
    private val photos = LruCache<String, ImageBitmap>(30)

    fun get(imageId: String): ImageBitmap? = photos.get(imageId)

    fun put(imageId: String, photo: ImageBitmap) {
        photos.put(imageId, photo)
    }
}
