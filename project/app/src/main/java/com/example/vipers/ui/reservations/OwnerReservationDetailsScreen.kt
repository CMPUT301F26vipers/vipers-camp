/*
 * OwnerReservationDetailsScreen.kt
 * The owner's view of one reservation (mockup O15, US 02.07.01): the entrant's name and contact
 * details, then the campsite, dates, guests and whether the rules were accepted, a Message
 * Entrant button that opens the conversation (US 02.12.01), and a Reject Reservation button that
 * asks for a reason in the Reject reservation dialog (mockup O16, US 02.08.01).
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.GreenFillStrong
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * One reservation at the owner's campsites (mockup O15, US 02.07.01), which the owner can reject
 * with a reason (US 02.08.01). Tests pass their own [controller] with fake repositories.
 *
 * @param reservationId ID of the reservation being viewed.
 * @param onBack Called when the back arrow is tapped.
 * @param onMessage Called with the reservation's ID when Message Entrant is tapped (US 02.12.01).
 * @param controller Loads the reservation and its entrant.
 */
@Composable
fun OwnerReservationDetailsScreen(
    reservationId: String,
    onBack: () -> Unit,
    onMessage: (reservationId: String) -> Unit,
    controller: OwnerReservationDetailsController =
        viewModel(factory = OwnerReservationDetailsController.factory(reservationId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }

    val reservation = controller.reservation

    if (controller.showRejectDialog) {
        RejectReservationDialog(
            reason = controller.rejectReason,
            reasonError = controller.reasonError,
            rejecting = controller.rejecting,
            onReasonChange = controller::onReasonChange,
            onCancel = controller::dismissRejectDialog,
            onReject = controller::rejectReservation,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Reservation", onBack = onBack)

        when {
            controller.loading ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> Message(controller.error!!)
            reservation == null -> Message("Reservation not found")
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    EntrantHeader(controller.entrant)
                    DetailLine("Campsite", reservation.campsiteName)
                    DetailLine("Dates", formatStayDates(reservation.checkIn, reservation.checkOut))
                    DetailLine("Guests", reservation.groupSize.toString())
                    DetailLine("Rules accepted", rulesAcceptedText(reservation))
                    if (reservation.status != ReservationStatus.CONFIRMED) {
                        DetailLine(
                            label = "Status",
                            value = statusText(reservation.status),
                            valueColor = if (reservation.status == ReservationStatus.REJECTED) {
                                Error
                            } else {
                                Color.Unspecified
                            },
                        )
                    }
                    reservation.rejectionReason?.let { DetailLine("Reason", it) }
                }
                // the buttons stay at the bottom, as in mockup O15
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onMessage(reservation.id) },
                        shape = ButtonShape,
                        border = BorderStroke(1.5.dp, Primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ButtonHeight)
                            .testTag("button_message_entrant"),
                    ) {
                        Text("Message Entrant", fontWeight = FontWeight.SemiBold)
                    }
                    if (controller.canReject()) {
                        OutlinedButton(
                            onClick = controller::openRejectDialog,
                            enabled = !controller.rejecting,
                            shape = ButtonShape,
                            border = BorderStroke(1.5.dp, Error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ButtonHeight)
                                .testTag("button_reject_reservation"),
                        ) {
                            Text("Reject Reservation", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    controller.rejectError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Error,
                            modifier = Modifier.testTag("text_reject_error"),
                        )
                    }
                }
            }
        }
    }
}

/** "Yes" if the entrant accepted the campsite's rules when booking, otherwise "No". */
private fun rulesAcceptedText(reservation: Reservation): String =
    if (reservation.rulesAcceptedAt > 0) "Yes" else "No"

/** [status] as a word for the UI, e.g. "Rejected". */
private fun statusText(status: ReservationStatus): String =
    status.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * Asks the owner why they are rejecting the reservation (mockup O16, US 02.08.01). Reject needs a
 * [reason]; [reasonError] shows under the field. Cancel keeps the booking. Both buttons are
 * disabled while [rejecting].
 */
@Composable
private fun RejectReservationDialog(
    reason: String,
    reasonError: String?,
    rejecting: Boolean,
    onReasonChange: (String) -> Unit,
    onCancel: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!rejecting) onCancel() },
        title = { Text("Reject reservation?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The entrant will be told why.", color = TextMuted)
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason") },
                    placeholder = { Text("e.g. Forest fire warning") },
                    isError = reasonError != null,
                    supportingText = reasonError?.let { error ->
                        { Text(error, modifier = Modifier.testTag("reject_reason_error")) }
                    },
                    enabled = !rejecting,
                    shape = InputShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reject_reason"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onReject,
                enabled = !rejecting,
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                modifier = Modifier.testTag("reject_confirm"),
            ) {
                Text("Reject")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                enabled = !rejecting,
                modifier = Modifier.testTag("reject_cancel"),
            ) {
                Text("Cancel")
            }
        },
    )
}

/**
 * The entrant's name with "email · phone" under it, as in mockup O15. Shows "Deleted user" when
 * their profile is gone, and leaves out contact details they didn't give.
 */
@Composable
private fun EntrantHeader(entrant: UserProfile?) {
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(GreenFillStrong, CircleShape),
        )
        Column {
            Text(
                text = entrant?.name ?: "Deleted user",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val contact = listOfNotNull(entrant?.email, entrant?.phone).filter { it.isNotBlank() }
            if (contact.isNotEmpty()) {
                Text(
                    text = contact.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/**
 * A [label] on the left and its [value] on the right in [valueColor], with a line under it, as in
 * mockup O15.
 */
@Composable
private fun DetailLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = TextMuted)
        Text(value, modifier = Modifier.weight(1f), color = valueColor, textAlign = TextAlign.End)
    }
    HorizontalDivider(color = Divider)
}

/** A centred grey [text] shown instead of the reservation. */
@Composable
private fun Message(text: String) {
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
