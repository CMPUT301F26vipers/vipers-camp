/*
 * AppRoot.kt
 * Picks the first screen when the app opens: Welcome if this device has no profile yet (T-01),
 * otherwise Home. The user is recognized by their device ID, with no login (US 01.09.01).
 * Known issues: none.
 */
package com.example.vipers.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.ui.profile.WelcomeScreen
import kotlinx.coroutines.CancellationException

/**
 * Shows Welcome or the app's navigation, depending on whether [deviceIdentityManager] finds a
 * profile for this device. Also hides the Admin tab unless the profile is an admin.
 */
@Composable
fun AppRoot(deviceIdentityManager: DeviceIdentityManager, onLoadDemoData: () -> Unit) {
    // null while we check for a profile
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val profile = deviceIdentityManager.currentProfile()
            hasProfile = profile != null
            isAdmin = profile?.isAdmin ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline on the very first launch: nothing is saved on the phone yet, so ask for a name
            hasProfile = false
        }
    }

    when (hasProfile) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> WelcomeScreen(onProfileCreated = { hasProfile = true })
        true -> AppNavigation(isAdmin = isAdmin, onLoadDemoData = onLoadDemoData)
    }
}
