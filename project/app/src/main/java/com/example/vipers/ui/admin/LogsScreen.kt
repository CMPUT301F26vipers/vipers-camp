/*
 * LogsScreen.kt
 * Displays admin logs (Notification Log and Message Log) (US 03.12.01, mockup A09).
 * Known issues: Notification Log is added in US 03.08.01.
 */
package com.example.vipers.ui.admin

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.messages.messageTime
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes
import com.example.vipers.ui.theme.TextMuted

/** Tabs available in the admin logs screen (mockup A09). */
enum class LogsTab(val label: String) {
    NOTIFICATION_LOG("Notification Log"),
    MESSAGE_LOG("Message Log"),
}

/**
 * Admin logs screen (mockup A09) with Notification Log and Message Log tabs (US 03.12.01).
 *
 * @param onBack Called when the back button is tapped.
 * @param onOpenConversation Called with the reservation ID when a conversation is tapped.
 * @param initialTab The tab to open initially (defaults to [LogsTab.MESSAGE_LOG]).
 * @param controller Loads the message logs; tests can pass their own instance.
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
    initialTab: LogsTab = LogsTab.MESSAGE_LOG,
    controller: AdminController = viewModel(factory = AdminController.Factory),
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    LaunchedEffect(Unit) {
        if (controller.messageLogs.isEmpty()) {
            controller.loadMessageLogs()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Logs", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogsTab.entries.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (selectedTab) {
            LogsTab.NOTIFICATION_LOG -> Placeholder(
                "Notification Log (03.08.01)",
                modifier = Modifier.padding(16.dp),
            )
            LogsTab.MESSAGE_LOG -> MessageLogTab(
                controller = controller,
                onOpenConversation = onOpenConversation,
            )
        }
    }
}

/** A rounded pill tab at the top of the logs screen. */
@Composable
private fun TabPill(
    tab: LogsTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .semantics { role = Role.Tab }
            .testTag("logs_tab_${tab.name.lowercase()}"),
        shape = CircleShape,
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, Border),
    ) {
        Text(
            text = tab.label,
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The Message Log tab displaying all owner-entrant conversations. */
@Composable
private fun MessageLogTab(
    controller: AdminController,
    onOpenConversation: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { controller.loadMessageLogs() },
                enabled = !controller.messageLogsLoading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("button_refresh"),
            ) {
                Text("Refresh", style = MaterialTheme.typography.labelMedium)
            }
        }

        when {
            controller.messageLogsLoading && controller.messageLogs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            controller.messageLogsLoadFailed && controller.messageLogs.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = controller.messageLogError ?: "Unable to load message logs.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { controller.loadMessageLogs() }) {
                        Text("Retry")
                    }
                }
            }
            controller.messageLogs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No message logs found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("message_log_list"),
                ) {
                    items(controller.messageLogs, key = { it.reservationId }) { conversation ->
                        MessageLogRowItem(
                            conversation = conversation,
                            onClick = { onOpenConversation(conversation.reservationId) },
                        )
                    }
                }
            }
        }
    }
}

/** One conversation row displaying owner, entrant, campsite, and last message timestamp. */
@Composable
private fun MessageLogRowItem(
    conversation: MessageLogConversation,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_log_row"),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(GreenFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.campsiteName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.lastMessageTime > 0L) {
                        Text(
                            text = messageTime(conversation.lastMessageTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${conversation.ownerName} · ${conversation.entrantName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
