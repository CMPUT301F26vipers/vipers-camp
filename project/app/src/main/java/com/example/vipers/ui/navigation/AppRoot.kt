/*
 * AppRoot.kt
 * MainActivity's screen content, kept in its own file so intent tests can run it with fake
 * repositories. DeviceIdentityManager checks whether this device has a profile: if not the
 * app opens on Welcome (T-01), otherwise on Home, with no login (US 01.09.01). Each time the app
 * comes to the front it runs onAppOpened, which MainActivity uses to start any draws that are due
 * (US 02.05.01b). onScanQr is passed down to Home's Scan QR button (US 01.12.01). Once Home is
 * shown (not on Welcome), Android 13 and later ask once per app start for permission to show
 * phone notifications (T-14), and a tapped phone notification opens its screen (T-14).
 * Known issues: none.
 */
package com.example.vipers.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.ui.home.QrScanResult
import com.example.vipers.ui.profile.WelcomeScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Shows Welcome or the app's navigation, depending on [DeviceIdentityManager.hasProfile].
 * Also hides the Admin tab unless the profile is an admin. Runs [onAppOpened] each time the app
 * comes to the front, and passes [onScanQr] on to Home. Once the app's navigation is shown, on
 * Android 13 and later it asks for permission to show phone notifications and calls
 * [onNotificationPermissionGranted] if the user allows it (T-14). [pendingTarget] is the screen a
 * tapped phone notification opens (T-14): it is opened once the app's navigation is shown, and
 * dropped if this device has no profile yet (Welcome); either way [onPendingTargetHandled] is then
 * called.
 */
@Composable
fun AppRoot(
    deviceIdentityManager: DeviceIdentityManager,
    onLoadDemoData: () -> Unit,
    onAppOpened: suspend () -> Unit = {},
    onScanQr: suspend () -> QrScanResult = { QrScanResult.Failed },
    onNotificationPermissionGranted: () -> Unit = {},
    pendingTarget: NotificationTarget? = null,
    onPendingTargetHandled: () -> Unit = {},
) {
    // null while we check for a profile
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var checkFailed by remember { mutableStateOf(false) }
    // bumped by Retry to run the check again
    var attempt by remember { mutableIntStateOf(0) }

    // ON_START happens when the app opens and each time it comes back from the background
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    // the observer stays the same, so keep it calling the newest onAppOpened
    val latestOnAppOpened by rememberUpdatedState(onAppOpened)
    val latestOnPendingTargetHandled by rememberUpdatedState(onPendingTargetHandled)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) scope.launch { latestOnAppOpened() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
        hasProfile == false -> {
            LaunchedEffect(pendingTarget) {
                if (pendingTarget != null) latestOnPendingTargetHandled()
            }
            WelcomeScreen(onProfileCreated = { hasProfile = true })
        }
        else -> {
            AskForNotificationPermission(onGranted = onNotificationPermissionGranted)
            AppNavigation(
                isAdmin = isAdmin,
                onLoadDemoData = onLoadDemoData,
                onScanQr = onScanQr,
                pendingTarget = pendingTarget,
                onPendingTargetOpened = onPendingTargetHandled,
            )
        }
    }
}

/**
 * On Android 13 and later, asks once per app start for permission to show phone notifications
 * (T-14) if it isn't granted yet, and calls [onGranted] if the user allows it. If the user says
 * no, nothing else happens and the in-app inbox is used alone. Does nothing on older versions,
 * which don't need the permission.
 */
@Composable
private fun AskForNotificationPermission(onGranted: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val latestOnGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latestOnGranted()
    }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permission)
        }
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
