/*
 * ReservationDetailsScreen.kt
 * The entrant's view of their reservation (US 01.05.01): shows the reservation number,
 * campsite name, stay dates, group size, total price, and status.
 * Known issues: none.
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Reservation
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * Entrant's view of a confirmed reservation (US 01.05.01).
 *
 * @param reservationId ID of the reservation to load and view.
 * @param onBack Called when the top bar back button is tapped.
 * @param controller Controller that loads the reservation and campsite details.
 */
@Composable
fun ReservationDetailsScreen(
    reservationId: String,
    onBack: () -> Unit,
    controller: ReservationDetailsController =
        viewModel(factory = ReservationDetailsController.factory(reservationId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }

    val reservation = controller.reservation

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
                )
            }
        }
    }
}

/** Shows the reservation confirmation details and optional rules. */
@Composable
private fun ReservationContent(
    reservation: Reservation,
    rules: String?,
) {
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
                    text = "Confirmed Reservation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
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
                    isLast = true,
                )
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
    }
}

/** A single label-value line in the reservation details. */
@Composable
private fun ReservationDetailRow(
    label: String,
    value: String,
    tag: String,
    isBold: Boolean = false,
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
