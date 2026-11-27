/*
 * ImageAsset.kt
 * An uploaded image (a campsite promo photo or a profile photo) and its record. The JPEG itself is
 * stored as Base64 text in the Firestore "images" collection, so no file storage is needed, and
 * admins can browse and remove every image (US 02.01.02b, 03.06.01).
 * Known issues: a Firestore document can be at most 1 MB, so photos are shrunk before saving and
 * anything larger than MAX_DATA_LENGTH is refused.
 */
package com.example.vipers.domain.model

import java.util.Base64

/**
 * An uploaded image.
 *
 * @property id Firestore document ID.
 * @property data The JPEG image as Base64 text.
 * @property uploaderId Device ID of the user who uploaded it.
 * @property ownerType What it belongs to: [OWNER_CAMPSITE] or [OWNER_PROFILE].
 * @property ownerId ID of the campsite or profile it belongs to.
 * @property label Text shown in the admin image browser, e.g. the campsite name.
 * @property createdAt When it was uploaded, in ms.
 */
data class ImageAsset(
    val id: String = "",
    val data: String = "",
    val uploaderId: String = "",
    val ownerType: String = OWNER_CAMPSITE,
    val ownerId: String = "",
    val label: String = "",
    val createdAt: Long = 0,
) {

    /** The JPEG bytes, decoded from [data]. */
    fun jpegBytes(): ByteArray = Base64.getDecoder().decode(data)

    /** True if [data] is small enough to fit in one Firestore document with the other fields. */
    fun isSmallEnough(): Boolean = data.length <= MAX_DATA_LENGTH

    companion object {
        /** [ownerType] of a campsite promo photo (US 02.01.02b). */
        const val OWNER_CAMPSITE = "campsite"

        /** [ownerType] of a profile photo (US 01.08.02). */
        const val OWNER_PROFILE = "profile"

        /**
         * The longest [data] allowed, in characters. A Firestore document can be at most 1 MB,
         * and this leaves room for the other fields.
         */
        const val MAX_DATA_LENGTH = 900_000

        /**
         * The biggest JPEG to save, in bytes. As Base64 text it is 4/3 as long (800,000
         * characters), which is under [MAX_DATA_LENGTH].
         */
        const val MAX_JPEG_BYTES = 600_000

        /** Makes an image record from [jpeg] bytes, saved as Base64 text in [data]. */
        fun fromJpeg(
            jpeg: ByteArray,
            uploaderId: String,
            ownerType: String,
            ownerId: String,
            label: String,
            createdAt: Long,
        ) = ImageAsset(
            data = Base64.getEncoder().encodeToString(jpeg),
            uploaderId = uploaderId,
            ownerType = ownerType,
            ownerId = ownerId,
            label = label,
            createdAt = createdAt,
        )
    }
}
