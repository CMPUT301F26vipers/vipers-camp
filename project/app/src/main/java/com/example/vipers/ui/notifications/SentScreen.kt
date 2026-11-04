/*
 * SentScreen.kt
 *
 * Displays notifications or requests sent by the campsite owner.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Displays a historical list of notifications and reminders sent by the owner
 * to their campsite guests (US 02.10.02).
 *
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun SentScreen(onBack: () -> Unit) {
    Column {
        TopBar(title = "Sent", onBack = onBack)
        Placeholder("Sent notifications is added in US 02.10.02.")
    }
}