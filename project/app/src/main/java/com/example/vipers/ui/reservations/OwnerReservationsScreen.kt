/*
 * OwnerReservationsScreen.kt
 *
 * Displays the list of reservations made at the owner's campsites.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Displays a list of all reservations made across the owner's campsites.
 * Categorized by Active, Past, and Canceled statuses (US 02.07.01).
 *
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun OwnerReservationsScreen(onBack: () -> Unit) {
    Column {
        TopBar(title = "Reservations", onBack = onBack)
        Placeholder("List of reservations for your campsites is added in US 02.07.01")
    }
}