/*
 * MessageLogConversationScreen.kt
 * Displays a read-only message log conversation between owner and entrant for admins (US 03.12.01, mockup A09b).
 * Known issues: none.
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Message
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.messages.messageTime
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes
import com.example.vipers.ui.theme.TextMuted

/**
 * Read-only conversation view for admins (mockup A09b, US 03.12.01).
 *
 * @param reservationId ID of the reservation whose conversation is displayed.
 * @param onBack Called when the back button is tapped.
 * @param controller Provides the message logs; tests can pass their own instance.
 */
@Composable
fun MessageLogConversationScreen(
    reservationId: String,
    onBack: () -> Unit,
    controller: AdminController = viewModel(factory = AdminController.Factory),
) {
    LaunchedEffect(reservationId) {
        if (controller.messageLogs.isEmpty()) {
            controller.loadMessageLogs()
        }
    }

    val conversation = controller.getConversation(reservationId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Conversation", onBack = onBack)

        when {
            controller.messageLogsLoading && conversation == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            controller.messageLogsLoadFailed && conversation == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = controller.messageLogError ?: "Unable to load conversation.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { controller.loadMessageLogs() }) {
                        Text("Retry")
                    }
                }
            }
            conversation == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Conversation not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                ConversationHeaderCard(conversation)

                if (conversation.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No messages yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("message_log_conversation_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(conversation.messages, key = { it.id.ifBlank { "${it.createdAt}_${it.senderId}" } }) { message ->
                            val isOwner = conversation.ownerId.isNotBlank() && message.senderId == conversation.ownerId
                            AdminMessageBubble(
                                message = message,
                                senderName = conversation.senderName(message),
                                isOwner = isOwner,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Information card displayed at the top showing campsite, owner, and entrant. */
@Composable
private fun ConversationHeaderCard(conversation: MessageLogConversation) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = conversation.campsiteName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Owner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = conversation.ownerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Entrant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = conversation.entrantName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** A single chat bubble in the admin message log conversation. */
@Composable
private fun AdminMessageBubble(
    message: Message,
    senderName: String,
    isOwner: Boolean,
) {
    val alignment = if (isOwner) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwner) Primary else MaterialTheme.colorScheme.surface
    val contentColor = if (isOwner) Color.White else MaterialTheme.colorScheme.onSurface
    val border = if (isOwner) null else BorderStroke(1.dp, Border)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_bubble"),
        horizontalAlignment = alignment,
    ) {
        val label = if (message.createdAt > 0L) {
            "$senderName · ${messageTime(message.createdAt)}"
        } else {
            senderName
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bubbleColor,
            contentColor = contentColor,
            border = border,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
