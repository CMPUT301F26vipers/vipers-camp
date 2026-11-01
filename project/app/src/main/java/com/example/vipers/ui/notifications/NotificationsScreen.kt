/*
 * NotificationsScreen.kt
 * The Notifications tab (mockup E10).
 * Known issues: placeholder. The inbox is added in T-13.
 */
package com.example.vipers.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The user's notification inbox. */
@Composable
fun NotificationsScreen() {
    Column {
        TopBar(title = "Notifications")
        Placeholder("Your notifications are added in T-13.")
    }
}
