/*
 * FakePhotoReader.kt
 * A PhotoReader for unit and intent tests (a test double): returns the bytes it was given instead
 * of reading a photo from the phone (US 02.01.02b).
 * Known issues: none. Lives in main (not test) so both unit tests and intent tests can use it.
 */
package com.example.vipers.data.fake

import com.example.vipers.domain.repository.PhotoReader
import java.io.IOException

/**
 * Returns [jpeg] for any photo, or throws an [IOException] when [fails] is true, like a photo
 * that can't be opened.
 */
class FakePhotoReader(
    private val jpeg: ByteArray = SAMPLE_JPEG,
    var fails: Boolean = false,
) : PhotoReader {

    /** The URIs read so far, oldest first. */
    val reads = mutableListOf<String>()

    override suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray {
        reads.add(uri)
        if (fails) throw IOException("Can't read the photo")
        return jpeg
    }

    companion object {
        /**
         * A real 8 × 8 green JPEG, so screens that draw the photo show something in intent tests.
         */
        val SAMPLE_JPEG: ByteArray = java.util.Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAASABIAAD/4QCARXhpZgAATU0AKgAAAAgABAEaAAUAAAABAAAAPgEbAAUA" +
                "AAABAAAARgEoAAMAAAABAAIAAIdpAAQAAAABAAAATgAAAAAAAABIAAAAAQAAAEgAAAABAAOgAQAD" +
                "AAAAAQABAACgAgAEAAAAAQAAAAigAwAEAAAAAQAAAAgAAAAA/+0AOFBob3Rvc2hvcCAzLjAAOEJJ" +
                "TQQEAAAAAAAAOEJJTQQlAAAAAAAQ1B2M2Y8AsgTpgAmY7PhCfv/AABEIAAgACAMBIgACEQEDEQH/" +
                "xAAfAAABBQEBAQEBAQAAAAAAAAAAAQIDBAUGBwgJCgv/xAC1EAACAQMDAgQDBQUEBAAAAX0BAgMA" +
                "BBEFEiExQQYTUWEHInEUMoGRoQgjQrHBFVLR8CQzYnKCCQoWFxgZGiUmJygpKjQ1Njc4OTpDREVG" +
                "R0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0" +
                "tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4eLj5OXm5+jp6vHy8/T19vf4+fr/xAAfAQADAQEBAQEB" +
                "AQEBAAAAAAAAAQIDBAUGBwgJCgv/xAC1EQACAQIEBAMEBwUEBAABAncAAQIDEQQFITEGEkFRB2Fx" +
                "EyIygQgUQpGhscEJIzNS8BVictEKFiQ04SXxFxgZGiYnKCkqNTY3ODk6Q0RFRkdISUpTVFVWV1hZ" +
                "WmNkZWZnaGlqc3R1dnd4eXqCg4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TF" +
                "xsfIycrS09TV1tfY2dri4+Tl5ufo6ery8/T19vf4+fr/2wBDAAQEBAQEBAYEBAYJBgYGCQwJCQkJ" +
                "DA8MDAwMDA8SDw8PDw8PEhISEhISEhIVFRUVFRUZGRkZGRwcHBwcHBwcHBz/2wBDAQQFBQcHBwwH" +
                "BwwdFBAUHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR3/" +
                "3QAEAAH/2gAMAwEAAhEDEQA/APJaKKK4T4U//9k="
        )
    }
}
