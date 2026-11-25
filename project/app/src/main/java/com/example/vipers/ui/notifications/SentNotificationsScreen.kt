/*
 * SentNotificationsScreen.kt
 * The owner's Sent Notifications screen (mockup O13, US 02.10.02), opened from My Campsites.
 * Lists every notification the owner has sent, newest first: the whole message, its campsite and
 * lottery, and when it was sent to how many entrants. SentNotificationsController supplies the rows.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Shapes

/**
 * The owner's sent notifications (O13, US 02.10.02): a loading indicator until they arrive, then
 * one card per send from [controller], newest first, or "You haven't sent any notifications yet."
 * if there are none. If they couldn't be loaded it shows why, with a Retry button.
 *
 * @param onBack Goes back to My Campsites.
 * @param controller ViewModel that loads the owner's sends.
 */
@Composable
fun SentNotificationsScreen(
    onBack: () -> Unit,
    controller: SentNotificationsController = viewModel(factory = SentNotificationsController.Factory),
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "Sent", onBack = onBack)
        val error = controller.error
        when {
            controller.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("sent_notifications_loading"))
            }
            error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.padding(8.dp))
                Button(onClick = controller::retry) {
                    Text("Retry")
                }
            }
            controller.isEmpty -> Text(
                "You haven't sent any notifications yet.",
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("sent_notification_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(controller.items, key = { it.batchId }) { row ->
                    SentNotificationCard(row)
                }
            }
        }
    }
}

/**
 * One send like mockup O13 (US 02.10.02): the whole message in bold, then the campsite and lottery,
 * then when it was sent and to how many, in grey.
 */
@Composable
private fun SentNotificationCard(row: SentNotificationRow) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("sent_notification_row"),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.message,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
