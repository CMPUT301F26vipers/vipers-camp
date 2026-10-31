/*
 * ImageAsset.kt
 * The record of an uploaded image (a campsite promo photo or a profile photo).
 * Stored in the Firestore "images" collection so admins can browse and remove images.
 * Known issues: none.
 */
package com.example.vipers.domain.model

/**
 * An uploaded image.
 *
 * @property id Firestore document ID.
 * @property storagePath Where the image file is stored.
 * @property uploaderId Device ID of the user who uploaded it.
 * @property ownerType What it belongs to: "campsite" or "profile".
 * @property ownerId ID of the campsite or profile it belongs to.
 * @property label Text shown in the admin image browser, e.g. the campsite name.
 */
data class ImageAsset(
    val id: String = "",
    val storagePath: String = "",
    val uploaderId: String = "",
    val ownerType: String = "campsite",
    val ownerId: String = "",
    val label: String = "",
)
