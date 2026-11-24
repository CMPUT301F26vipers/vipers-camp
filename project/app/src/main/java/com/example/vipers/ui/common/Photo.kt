/*
 * Photo.kt
 * Shared composables that draw a photo with a green placeholder and icon when there's none
 * (US 02.01.02b). SavedPhoto and JpegPhoto crop a saved or just-picked photo to fill a small box:
 * thumbnails on the form (O02, O05), the Home cards (E02) and My Campsites (O01). FullPhoto shows
 * the whole photo, uncropped, on My Campsite (O04) and Campsite Details (E05). Profile photos
 * (US 01.08.02) and the admin image browser (US 03.06.01) can reuse them.
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val photo = rememberSavedPhoto(imageId)
    PhotoBox(photo, contentDescription, shape, modifier, placeholderIcon)
}

/**
 * Draws the whole saved photo with this [imageId], uncropped, as wide as [modifier] allows and at
 * most [maxHeight] tall; a tall photo is shown in full, centred on green. Shows a placeholder
 * [placeholderHeight] tall while it loads, or if there's no photo or it can't be loaded.
 */
@Composable
fun FullPhoto(
    imageId: String?,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholderHeight: Dp = 140.dp,
    maxHeight: Dp = 360.dp,
) {
    val photo = rememberSavedPhoto(imageId)
    if (photo == null) {
        val box = modifier.height(placeholderHeight)
        PhotoBox(null, contentDescription, shape, box, R.drawable.ic_image)
        return
    }
    Box(
        modifier.heightIn(max = maxHeight).clip(shape).background(GreenFill),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = photo,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height),
        )
    }
}

// The saved photo with this imageId, loaded through the app's ImageRepository (or the cache), or
// null while it loads, if there's none, or if it can't be loaded.
@Composable
private fun rememberSavedPhoto(imageId: String?): ImageBitmap? {
    val app = LocalContext.current.applicationContext as? CampApp
    val photo by produceState(imageId?.let { PhotoCache.get(it) }, imageId) {
        // produceState keeps the last value when imageId changes, so a replaced or removed photo
        // (e.g. on Profile, US 01.08.02) would stay on screen without this
        value = imageId?.let { PhotoCache.get(it) }
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
    return photo
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
            Icon(
                painterResource(placeholderIcon),
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.testTag("photo_placeholder"),
            )
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
