/*
 * DeviceIdentityManager.kt
 * Identifies the user by their device, since the app has no usernames or passwords (US 01.09.01).
 * Gives the current user's ID and profile, and decides whether to show Welcome or Home first.
 * Known issues: ANDROID_ID changes if the phone is factory reset, which makes it a new user.
 */
package com.example.vipers.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.ProfileRepository

/**
 * Knows who the current user is.
 *
 * @property deviceId This device's ID, used as the user's ID everywhere. Tests pass any string.
 */
class DeviceIdentityManager(
    val deviceId: String,
    private val profiles: ProfileRepository,
) {
    /** Loads the current user's profile, or returns null if this device hasn't made one yet. */
    suspend fun currentProfile(): UserProfile? = profiles.getProfile(deviceId)

    /** True if this device already has a profile, so the app can skip Welcome and open Home. */
    suspend fun hasProfile(): Boolean = currentProfile() != null

    companion object {
        /** Reads this device's ANDROID_ID, which stays the same across app restarts. */
        @SuppressLint("HardwareIds") // Using the device ID as the account is a project requirement.
        fun androidId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
