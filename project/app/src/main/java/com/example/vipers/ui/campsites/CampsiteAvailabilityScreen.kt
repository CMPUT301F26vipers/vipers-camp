/*
 * CampsiteAvailabilityScreen.kt
 *
 * Screen for viewing and managing campsite availability.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Screen where a campsite owner manages booked and open dates for their campsite.
 * Added in US 02.06.01.
 *
 * @param campsiteId The ID of the campsite whose availability is managed.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun CampsiteAvailabilityScreen(campsiteId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "Availability", onBack = onBack)
        Placeholder("Managing campsite availability is added in US 02.06.01.")
    }
}
