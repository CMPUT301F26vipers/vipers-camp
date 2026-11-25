/*
 * PhotoReader.kt
 * Reads a photo the user picked and shrinks it to a small JPEG that fits in a Firestore document
 * (US 02.01.02b). DevicePhotoReader reads it from the phone; FakePhotoReader returns set bytes for
 * tests, so the form can be tested without the photo picker.
 * Known issues: none.
 */
package com.example.vipers.domain.repository

/** Turns a picked photo into JPEG bytes. */
interface PhotoReader {

    /**
     * Reads the photo at [uri] (the text form of the picker's content URI) and returns it as a
     * JPEG of at most [maxBytes] bytes.
     *
     * @throws java.io.IOException if the photo can't be read or made that small.
     */
    suspend fun readJpeg(uri: String, maxBytes: Int): ByteArray
}
