package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Displays the detailed view for a specific campsite, allowing the owner
 * to view metrics and eventually edit the listing (US 02.01.04).
 *
 * @param campsiteId The unique identifier of the campsite to display.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun CampsiteDetailsScreen(campsiteId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "Campsite Details", onBack = onBack)
        Placeholder("Viewing details for campsite ID: $campsiteId")
        Placeholder("Editing will be added in US 02.01.04, 02.04.01, T-04")
    }
}