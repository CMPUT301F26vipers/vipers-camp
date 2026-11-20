/*
 * SendNotificationScreen.kt
 * The owner's Send Notification screen (mockup O12, US 02.10.01), opened from the Waiting List
 * (O11). The owner writes a message to everyone on the lottery's waiting list; an info card shows
 * how many entrants that is. SendNotificationController loads the count and sends the message.
 * Known issues: none.
 */
package com.example.vipers.ui.notifications

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.TextSecondary

/**
 * The Send Notification form (O12) for [lotteryId]'s waiting list.
 *
 * @param lotteryId The lottery whose waiting list gets the message.
 * @param onBack Goes back to the Waiting List without sending.
 * @param onSent Called once the message was sent, after a short confirmation is shown.
 * @param controller ViewModel that loads the count and sends the message.
 */
@Composable
fun SendNotificationScreen(
    lotteryId: String,
    onBack: () -> Unit,
    onSent: () -> Unit,
    controller: SendNotificationController = viewModel(factory = SendNotificationController.factory(lotteryId)),
) {
    val context = LocalContext.current

    LaunchedEffect(controller.sentText) {
        val text = controller.sentText
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            onSent()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = "Send Notification", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecipientsCard(controller)

            Column {
                Text(
                    "Message",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value = controller.message,
                    onValueChange = controller::onMessageChange,
                    placeholder = { Text("Write a message") },
                    minLines = 6,
                    enabled = !controller.isSending,
                    shape = InputShape,
                    modifier = Modifier.fillMaxWidth().testTag("field_message"),
                )
            }
        }

        controller.sendError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("send_error"),
            )
        }
        Button(
            onClick = controller::send,
            enabled = controller.canSend,
            shape = ButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(ButtonHeight)
                .testTag("button_send"),
        ) {
            Text(if (controller.isSending) "Sending…" else "Send", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The grey card from O12: who gets the message, and how many entrants that is. */
@Composable
private fun RecipientsCard(controller: SendNotificationController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Divider),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Sent to everyone on this lottery's waiting list", color = TextSecondary)
            when {
                controller.loading -> CircularProgressIndicator(Modifier.padding(top = 8.dp).size(20.dp))
                controller.loadError != null -> Text(
                    controller.loadError!!,
                    color = Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> Text(
                    controller.countText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).testTag("send_notification_count"),
                )
            }
        }
    }
}
