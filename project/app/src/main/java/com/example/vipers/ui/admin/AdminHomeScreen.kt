/*
 * AdminHomeScreen.kt
 * The Admin tab (mockup A01). The tab only shows for profiles with isAdmin set (US 03.11.01).
 * Known issues: browsing and removing campsites, profiles and images, and the
 * logs, are added in the admin stories.
 */
package com.example.vipers.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.TopBar

/** Displays the admin menu. */
@Composable
fun AdminHomeScreen(
    onOpenCampsites: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenNotificationLog: () -> Unit,
    onOpenMessageLog: () -> Unit,
) {
    Column {
        TopBar(title = "Admin")

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MenuRow("Campsites", onClick = onOpenCampsites, subtitle = "Browse and remove")
            MenuRow("Profiles", onClick = onOpenProfiles, subtitle = "Browse and remove")
            MenuRow("Images", onClick = onOpenImages, subtitle = "Browse and remove")
            MenuRow("Notification Log", onClick = onOpenNotificationLog, subtitle = "Every notification sent")
            MenuRow("Message Log", onClick = onOpenMessageLog, subtitle = "Owner–entrant conversations")
        }
    }
}
