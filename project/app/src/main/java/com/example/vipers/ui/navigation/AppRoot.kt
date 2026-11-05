/*
 * AppRoot.kt
 * MainActivity's screen content, kept in its own file so intent tests can run it with fake
 * repositories. DeviceIdentityManager checks whether this device has a profile: if not the
 * app opens on Welcome (T-01), otherwise on Home, with no login (US 01.09.01).
 * Known issues: none.
 */
package com.example.vipers.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.ui.profile.WelcomeScreen
import kotlinx.coroutines.CancellationException

/**
 * Shows Welcome or the app's navigation, depending on [DeviceIdentityManager.hasProfile].
 * Also hides the Admin tab unless the profile is an admin.
 */
@Composable
fun AppRoot(deviceIdentityManager: DeviceIdentityManager, onLoadDemoData: () -> Unit) {
    // null while we check for a profile
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var checkFailed by remember { mutableStateOf(false) }
    // bumped by Retry to run the check again
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        checkFailed = false
        try {
            val found = deviceIdentityManager.hasProfile()
            if (found) isAdmin = deviceIdentityManager.currentProfile()?.isAdmin ?: false
            hasProfile = found
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't show Welcome here: an existing user would make a new profile and lose
            // their old one (admin, photo, settings). Ask them to retry instead.
            checkFailed = true
        }
    }

    when {
        checkFailed -> CouldNotConnect(onRetry = { attempt++ })
        hasProfile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        hasProfile == false -> WelcomeScreen(onProfileCreated = { hasProfile = true })
        else -> AppNavigation(isAdmin = isAdmin, onLoadDemoData = onLoadDemoData)
    }
}

@Composable
private fun CouldNotConnect(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Couldn't connect. Check your internet connection and try again.", textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.testTag("button_retry")) { Text("Retry") }
    }
}
