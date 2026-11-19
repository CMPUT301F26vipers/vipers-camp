/*
 * SettingsScreen.kt
 * The Settings screen (mockups E20, E20b), opened from Profile: the notifications switch
 * (US 01.10.01), which ProfileController saves to the profile, and the Policy row. Also has a
 * button that loads the demo data (SeedLoader) so teammates and the TA have something to look at.
 * Known issues: none.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.Primary

/**
 * App settings: the notifications switch (US 01.10.01), which [controller] loads and saves, and the
 * Policy row. [onLoadDemoData] adds the demo campsites, lotteries and reservations.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPolicy: () -> Unit,
    onLoadDemoData: () -> Unit,
    controller: ProfileController = viewModel(factory = ProfileController.Factory),
) {
    Column {
        TopBar(title = "Settings", onBack = onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NotificationsRow(
                on = controller.notificationsEnabled,
                enabled = !controller.isLoading && !controller.loadFailed,
                onChange = controller::onNotificationsSwitchChange,
            )
            if (controller.loadFailed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Couldn't load your settings.",
                        color = Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = controller::loadProfile) { Text("Retry") }
                }
            } else if (controller.notificationsSaveFailed) {
                Text(
                    "Couldn't save. Check your connection and try again.",
                    color = Error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!controller.notificationsEnabled) {
                Text(
                    "Draw results still show on each campsite's details screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            MenuRow("Policy and Guidelines", onClick = onOpenPolicy)
            OutlinedButton(
                onClick = onLoadDemoData,
                shape = ButtonShape,
                border = BorderStroke(1.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            ) {
                Text("Load demo data")
            }
        }
    }
}

// The "Notifications" card from E20: a title, a line saying what the switch does, and the switch.
// The whole row toggles it, so it's easy to tap and reads as one switch to TalkBack.
@Composable
private fun NotificationsRow(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .testTag("notifications_switch")
                .toggleable(
                    value = on,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onChange,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notifications", fontWeight = FontWeight.SemiBold)
                Text(
                    if (on) "Lottery results and messages"
                    else "Off: no lottery results or messages will be sent to you",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // the row handles taps, so the switch itself only shows the state; the off track uses
            // the app's grey-green instead of Material's default lavender (E20b)
            Switch(
                checked = on,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(uncheckedTrackColor = Divider),
            )
        }
    }
}
