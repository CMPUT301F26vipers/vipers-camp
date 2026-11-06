/*
 * NotificationsScreen.kt
 * The Notifications tab (mockup E10, T-13): the user's inbox, newest first and kept up to date
 * while it's open. Each row shows the kind of notification, its title and message, and when it
 * was sent; unread rows have a dot and a bold title. NotificationsController supplies the items.
 * Known issues: tapping a row doesn't open anything yet (added by a later T-13 step). The times
 * ("2m", "3h"...) are worked out when the list is drawn, so they don't tick while the tab stays
 * open with no new notifications.
 */
package com.example.vipers.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Notification
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFillStrong
import com.example.vipers.ui.theme.Shapes

/**
 * The user's notification inbox (T-13): a loading indicator until the inbox arrives, then the
 * notifications from [controller], newest first, or "No notifications yet" if there are none.
 */
@Composable
fun NotificationsScreen(
    controller: NotificationsController = viewModel(factory = NotificationsController.Factory),
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "Notifications")
        when {
            controller.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("notifications_loading"))
            }
            controller.items.isEmpty() -> Text(
                "No notifications yet",
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            else -> {
                val now = System.currentTimeMillis()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(controller.items, key = { it.id }) { item ->
                        NotificationRow(item, time = relativeTime(item.createdAt, now))
                    }
                }
            }
        }
    }
}

/**
 * One inbox row like mockup E10 (T-13): a round avatar, the [Notification.displayCategory] and
 * [time] on top, then the title (bold while unread) and the message, and a dot if it's unread.
 */
@Composable
private fun NotificationRow(item: Notification, time: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("notification_row"),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(GreenFillStrong, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.displayCategory,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!item.read) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("unread_dot")
                        .semantics { contentDescription = "Unread" },
                )
            }
        }
    }
}
