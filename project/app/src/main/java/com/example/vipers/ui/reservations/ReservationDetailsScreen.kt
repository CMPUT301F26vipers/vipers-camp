/*
 * ReservationDetailsScreen.kt
 * The entrant's view of their reservation (US 01.05.01): shows the reservation number,
 * campsite name, stay dates, group size, total price, and status. Before check-in the entrant
 * can cancel it after confirming in a dialog (US 01.07.01, mockups E15 and E16).
 * Message Owner opens this reservation's conversation (US 01.14.01). A reservation the owner
 * rejected shows its status in red and the owner's reason (US 02.08.01, mockup E15c).
 * Known issues: the "Cancelled on …" banner from mockup E15b comes with US 01.07.02.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * Entrant's view of a confirmed reservation (US 01.05.01).
 *
 * @param reservationId ID of the reservation to load and view.
 * @param onBack Called when the top bar back button is tapped.
 * @param onMessage Opens the conversation for the displayed reservation.
 * @param controller Controller that loads the reservation and campsite details.
 */
@Composable
fun ReservationDetailsScreen(
    reservationId: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    controller: ReservationDetailsController =
        viewModel(factory = ReservationDetailsController.factory(reservationId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }

    val reservation = controller.reservation
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    if (showCancelDialog) {
        CancelReservationDialog(
            policy = controller.campsite?.takeIf { it.hasCancellationPolicy }?.cancellationPolicy,
            onKeep = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                controller.cancelReservation()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_reservation_details"),
    ) {
        TopBar(title = "Reservation Details", onBack = onBack)

        when {
            controller.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("progress_reservation_details")
                    )
                }
            }
            controller.error != null -> {
                MessageView(
                    text = controller.error!!,
                    tag = "text_reservation_error",
                )
            }
            reservation == null -> {
                MessageView(
                    text = "Reservation not found",
                    tag = "text_reservation_not_found",
                )
            }
            else -> {
                ReservationContent(
                    reservation = reservation,
                    rules = controller.campsite?.rules,
                    onMessage = { onMessage(reservation.id) },
                    showMessageOwner = reservation.canMessageOwner(controller.userId),
                    canCancel = controller.canCancel(),
                    cancelling = controller.cancelling,
                    cancelError = controller.cancelError,
                    onCancelClick = { showCancelDialog = true },
                )
            }
        }
    }
}

/**
 * Shows the reservation details and optional rules, and the Cancel Reservation button while
 * [canCancel] is true (US 01.07.01).
 */
@Composable
private fun ReservationContent(
    reservation: Reservation,
    rules: String?,
    onMessage: () -> Unit,
    showMessageOwner: Boolean,
    canCancel: Boolean,
    cancelling: Boolean,
    cancelError: String?,
    onCancelClick: () -> Unit,
) {
    val confirmed = reservation.status == ReservationStatus.CONFIRMED
    val rejected = reservation.status == ReservationStatus.REJECTED
    val reason = reservation.rejectionReason?.takeIf { rejected && it.isNotBlank() }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Confirmation Card Header
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GreenFill),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when (reservation.status) {
                        ReservationStatus.CONFIRMED -> "Confirmed Reservation"
                        ReservationStatus.CANCELLED -> "Cancelled Reservation"
                        ReservationStatus.REJECTED -> "Rejected Reservation"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (confirmed) Primary else Error,
                    modifier = Modifier.testTag("detail_header"),
                )
                Text(
                    text = reservation.reservationNumber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("detail_reservation_number"),
                )
            }
        }

        // Details list
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ReservationDetailRow(
                    label = "Campsite",
                    value = reservation.campsiteName,
                    tag = "detail_campsite_name",
                )
                ReservationDetailRow(
                    label = "Dates",
                    value = formatStayDates(reservation.checkIn, reservation.checkOut),
                    tag = "detail_dates",
                )
                ReservationDetailRow(
                    label = "Guests",
                    value = formatGuests(reservation.groupSize),
                    tag = "detail_guests",
                )
                ReservationDetailRow(
                    label = "Total",
                    value = reservation.totalPriceText,
                    tag = "detail_total_price",
                    isBold = true,
                )
                ReservationDetailRow(
                    label = "Status",
                    value = reservation.status.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    tag = "detail_status",
                    valueColor = if (rejected) Error else Color.Unspecified,
                    isLast = reason == null,
                )
                if (reason != null) {
                    ReservationDetailRow(
                        label = "Reason",
                        value = reason,
                        tag = "detail_rejection_reason",
                        isLast = true,
                    )
                }
            }
        }

        // Campsite Rules if available
        if (!rules.isNullOrBlank()) {
            Text(
                text = "Campsite Rules",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = rules,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.testTag("detail_campsite_rules"),
            )
        }

        if (showMessageOwner) {
            OutlinedButton(
                onClick = onMessage,
                shape = ButtonShape,
                border = BorderStroke(1.5.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .testTag("button_message_owner"),
            ) {
                Text("Message Owner", fontWeight = FontWeight.SemiBold)
            }
        }

        if (canCancel) {
            OutlinedButton(
                onClick = onCancelClick,
                enabled = !cancelling,
                shape = ButtonShape,
                border = BorderStroke(1.5.dp, Error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .testTag("button_cancel_reservation"),
            ) {
                Text("Cancel Reservation", fontWeight = FontWeight.SemiBold)
            }
        }
        if (cancelError != null) {
            Text(
                text = cancelError,
                style = MaterialTheme.typography.bodyMedium,
                color = Error,
                modifier = Modifier.testTag("text_cancel_error"),
            )
        }
    }
}

/**
 * Asks the entrant to confirm cancelling (mockup E16, US 01.07.01), showing the campsite's
 * cancellation [policy] when the owner wrote one.
 */
@Composable
private fun CancelReservationDialog(
    policy: String?,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Cancel reservation?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The nights will be released to other winners.", color = TextMuted)
                if (policy != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GreenFill, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .testTag("dialog_cancellation_policy"),
                    ) {
                        Text("Cancellation policy", fontWeight = FontWeight.SemiBold)
                        Text(policy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                modifier = Modifier.testTag("cancel_confirm"),
            ) {
                Text("Cancel")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onKeep, modifier = Modifier.testTag("cancel_keep")) {
                Text("Keep it")
            }
        },
    )
}

/** A single label-value line in the reservation details. */
@Composable
private fun ReservationDetailRow(
    label: String,
    value: String,
    tag: String,
    isBold: Boolean = false,
    valueColor: Color = Color.Unspecified,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
            modifier = Modifier.testTag(tag),
        )
    }
    if (!isLast) {
        HorizontalDivider(color = Divider)
    }
}

/** Error or not found message displayed centered in the screen. */
@Composable
private fun MessageView(text: String, tag: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(tag),
        )
    }
}
