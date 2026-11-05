/*
 * CampsiteQrScreen.kt
 *
 * Displays the QR code for a campsite.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Screen displaying the campsite's QR code.
 * Added in US 02.02.01.
 *
 * @param campsiteId The ID of the campsite whose QR code is displayed.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun CampsiteQrScreen(campsiteId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "QR Code", onBack = onBack)
        Placeholder("Campsite QR code is added in US 02.02.01.")
    }
}
