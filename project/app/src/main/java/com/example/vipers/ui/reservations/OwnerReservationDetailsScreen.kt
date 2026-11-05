/*
 * OwnerReservationDetailsScreen.kt
 * The owner's view of one reservation (mockup O15, US 02.07.01): the entrant's name and contact
 * details, then the campsite, dates, guests and whether the rules were accepted.
 * Known issues: the Message Entrant (US 02.12.01) and Reject Reservation (US 02.08.01) buttons
 * from the mockup are added by those stories.
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFillStrong
import com.example.vipers.ui.theme.TextMuted

/**
 * One reservation at the owner's campsites (mockup O15, US 02.07.01). Tests pass their own
 * [controller] with fake repositories.
 *
 * @param reservationId ID of the reservation being viewed.
 * @param onBack Called when the back arrow is tapped.
 * @param controller Loads the reservation and its entrant.
 */
@Composable
fun OwnerReservationDetailsScreen(
    reservationId: String,
    onBack: () -> Unit,
    controller: OwnerReservationDetailsController =
        viewModel(factory = OwnerReservationDetailsController.factory(reservationId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }

    val reservation = controller.reservation

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
            else ->
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    EntrantHeader(controller.entrant)
                    DetailLine("Campsite", reservation.campsiteName)
                    DetailLine("Dates", formatStayDates(reservation.checkIn, reservation.checkOut))
                    DetailLine("Guests", reservation.groupSize.toString())
                    DetailLine("Rules accepted", rulesAcceptedText(reservation))
                }
        }
    }
}

/** "Yes" if the entrant accepted the campsite's rules when booking, otherwise "No". */
private fun rulesAcceptedText(reservation: Reservation): String =
    if (reservation.rulesAcceptedAt > 0) "Yes" else "No"

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

/** A [label] on the left and its [value] on the right, with a line under it, as in mockup O15. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = TextMuted)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
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
