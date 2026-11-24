/*
 * MainActivity.kt
 * The app's only Activity. It shows Welcome on first launch, or the app's navigation (bottom
 * bar and screens) once this device has a profile. Whenever the app opens it starts the lottery
 * draws that are due (US 02.05.01b). It also runs Google's code scanner for Home's Scan QR button
 * (US 01.12.01).
 * Known issues: if the screen rotates while the scanner is open, the scan is lost and the entrant
 * has to scan again.
 */
package com.example.vipers

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.vipers.data.device.DeviceNotificationLink
import com.example.vipers.data.seed.SeedLoader
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.home.QrScanResult
import com.example.vipers.ui.lottery.DrawAndNotifyWorker
import com.example.vipers.ui.navigation.AppRoot
import com.example.vipers.ui.theme.VipersTheme
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity : ComponentActivity() {

    private val container get() = (application as CampApp).container

    private var pendingTarget by mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // white clock and battery icons, since every top bar is dark green
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        DrawAndNotifyWorker.schedule(this)
        if (savedInstanceState == null) openNotificationTap(intent)
        setContent {
            VipersTheme {
                AppRoot(
                    container.deviceIdentityManager,
                    onLoadDemoData = { loadDemoData() },
                    onAppOpened = { container.dueDraws.runDueDraws() },
                    onScanQr = { scanQrCode() },
                    onNotificationPermissionGranted = { DrawAndNotifyWorker.checkNow(this) },
                    pendingTarget = pendingTarget,
                    onPendingTargetHandled = { pendingTarget = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotificationTap(intent)
    }

    private fun openNotificationTap(intent: Intent) {
        val tap = DeviceNotificationLink.fromIntent(intent)
        DeviceNotificationLink.clear(intent)
        if (tap == null) return
        pendingTarget = tap.target
        lifecycleScope.launch {
            try {
                NotificationService(
                    container.notificationRepository,
                    container.campsiteRepository,
                    container.profileRepository,
                ).markRead(tap.notificationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
        }
    }

    /**
     * Opens Google's code scanner (mockup E04, US 01.12.01) and waits for it. The scanner has its
     * own camera screen and asks for the camera itself, so the app never needs camera
     * permission; if the user says no, it tells us so Home can explain. It only reads QR codes.
     */
    private suspend fun scanQrCode(): QrScanResult = suspendCancellableCoroutine { continuation ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scan = try {
            GmsBarcodeScanning.getClient(this, options).startScan()
        } catch (e: Exception) {
            // the scanner couldn't even start (e.g. no Google Play services), so don't crash
            continuation.resume(QrScanResult.Failed)
            return@suspendCancellableCoroutine
        }
        scan
            .addOnSuccessListener { barcode ->
                if (continuation.isActive) {
                    continuation.resume(QrScanResult.Scanned(barcode.rawValue ?: ""))
                }
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(QrScanResult.Cancelled)
            }
            .addOnFailureListener { error ->
                val result = when ((error as? MlKitException)?.errorCode) {
                    MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED ->
                        QrScanResult.NoCameraPermission
                    MlKitException.CODE_SCANNER_CANCELLED -> QrScanResult.Cancelled
                    // e.g. Google Play services is missing, or the scanner couldn't download
                    else -> QrScanResult.Failed
                }
                if (continuation.isActive) continuation.resume(result)
            }
    }

    private fun loadDemoData() {
        lifecycleScope.launch {
            try {
                SeedLoader(container).seed()
                Toast.makeText(this@MainActivity, "Demo data loaded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Couldn't load demo data", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
