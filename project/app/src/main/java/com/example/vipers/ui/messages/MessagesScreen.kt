/*
 * MessagesScreen.kt
 * The Messages screen (mockup E17, US 02.12.01): one reservation's conversation between its
 * entrant and the campsite's owner, oldest first, each message with its sender and time, and a
 * box to send a new one. Owners open it from a reservation (O15) and both sides from a
 * "New message" notification.
 * Known issues: none. The entrant's "Message Owner" button on their reservation comes with
 * US 01.14.01.
 */
package com.example.vipers.ui.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Message
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.InputHeight
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * The conversation about reservation [reservationId] (mockup E17, US 02.12.01). The top bar shows
 * the other person's name. Tests pass their own [controller] with fake repositories.
 *
 * @param reservationId ID of the reservation the conversation is about.
 * @param onBack Called when the back arrow is tapped.
 * @param controller Loads the conversation and sends messages.
 */
@Composable
fun MessagesScreen(
    reservationId: String,
    onBack: () -> Unit,
    controller: MessageController = viewModel(factory = MessageController.factory(reservationId)),
) {
    LaunchedEffect(Unit) { controller.loadThread() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = controller.otherName.ifEmpty { "Messages" }, onBack = onBack)

        when {
            controller.loading ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> CenteredText(controller.error!!)
            controller.reservation == null -> CenteredText("Conversation not found")
            else -> {
                MessageList(controller, Modifier.weight(1f))
                SendBar(controller)
            }
        }
    }
}

/**
 * The messages, oldest first, scrolled to the newest one. Shows "No messages yet" when the
 * conversation is empty.
 */
@Composable
private fun MessageList(controller: MessageController, modifier: Modifier) {
    val messages = controller.messages
    if (messages.isEmpty()) {
        Box(modifier.fillMaxWidth()) { CenteredText("No messages yet") }
        return
    }
    val listState = rememberLazyListState()
    // keep the newest message in view when one is sent or arrives
    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.size - 1) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().testTag("message_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                label = "${controller.senderName(message)} · ${messageTime(message.createdAt)}",
                mine = controller.isMine(message),
            )
        }
    }
}

/**
 * One message as in mockup E17: "sender · time" above a bubble. The user's own messages are green
 * on the right, the other person's white on the left.
 */
@Composable
private fun MessageBubble(message: Message, label: String, mine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("message_bubble"),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (mine) Primary else MaterialTheme.colorScheme.surface,
            contentColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
            border = if (mine) null else BorderStroke(1.dp, Border),
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

/**
 * The message box and the round send button at the bottom, above the keyboard. The button only
 * works when there is text to send ([MessageController.canSend]).
 */
@Composable
private fun SendBar(controller: MessageController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (controller.sendError != null) {
            Text(
                text = controller.sendError!!,
                color = Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp).testTag("send_error"),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageField(
                text = controller.draft,
                onTextChange = controller::onDraftChange,
                onSend = { if (controller.canSend) controller.send() },
                modifier = Modifier.weight(1f).testTag("message_field"),
            )
            FilledIconButton(
                onClick = { controller.send() },
                enabled = controller.canSend,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(InputHeight).testTag("button_send"),
            ) {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The "Message" box, [InputHeight] tall like the other inputs. OutlinedTextField can't be shorter
 * than 56dp without cutting off its text, so this is a BasicTextField with the same outlined look,
 * like the search bar on Home. The keyboard's send key calls [onSend].
 */
@Composable
private fun MessageField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        interactionSource = interactionSource,
        modifier = modifier.height(InputHeight),
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = text,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            placeholder = { Text("Message") },
            contentPadding = PaddingValues(horizontal = 16.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    shape = InputShape,
                )
            },
        )
    }
}

/** A centred grey [text] shown instead of the conversation. */
@Composable
private fun CenteredText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
