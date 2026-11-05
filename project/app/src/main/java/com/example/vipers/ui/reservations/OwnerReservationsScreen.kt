/*
 * OwnerReservationsScreen.kt
 * The owner's Reservations screen (mockup O14), with Active, Past and Cancelled tabs. The Active
 * tab lists the confirmed reservations at the owner's campsites whose check-out is today or later
 * (US 02.07.01).
 * Known issues: the Past tab (US 02.07.02) and the Cancelled tab (US 02.07.03) are placeholders.
 * Tapping a row opens a placeholder until the reservation details screen (mockup O15) is built.
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFillStrong
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes

private enum class ReservationTab(val label: String) {
    ACTIVE("Active"),
    PAST("Past"),
    CANCELLED("Cancelled"),
}

/**
 * The owner's reservations, split into Active, Past and Cancelled tabs (mockup O14, US 02.07.01).
 *
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenReservation Called with the reservation's ID when its row is tapped.
 * @param controller Loads the reservations; tests can pass one built with fake repositories.
 */
@Composable
fun OwnerReservationsScreen(
    onBack: () -> Unit,
    onOpenReservation: (String) -> Unit,
    controller: ReservationController = viewModel(factory = ReservationController.Factory),
) {
    var selectedTab by rememberSaveable { mutableStateOf(ReservationTab.ACTIVE) }

    // here rather than in the Active tab, so switching tabs doesn't load the list again
    LaunchedEffect(Unit) { controller.loadOwnerActive() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "Reservations", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReservationTab.entries.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (selectedTab) {
            ReservationTab.ACTIVE -> ActiveTab(controller, onOpenReservation)
            ReservationTab.PAST -> Placeholder("Past reservations are added in US 02.07.02.")
            ReservationTab.CANCELLED -> Placeholder("Cancelled reservations are added in US 02.07.03.")
        }
    }
}

/** One of the rounded tabs at the top of mockup O14; the [selected] one is filled in. */
@Composable
private fun TabPill(
    tab: ReservationTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .semantics { role = Role.Tab }
            .testTag("reservations_tab_${tab.name.lowercase()}"),
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

/** The Active tab: a loading indicator, a message, or the list of reservations (US 02.07.01). */
@Composable
private fun ActiveTab(controller: ReservationController, onOpenReservation: (String) -> Unit) {
    when {
        controller.isLoading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        controller.loadFailed -> TabMessage("Couldn't load reservations")
        controller.activeRows.isEmpty() -> TabMessage("No active reservations")
        else ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(controller.activeRows, key = { it.reservationId }) { row ->
                    ReservationRowItem(row, onClick = { onOpenReservation(row.reservationId) })
                }
            }
    }
}

/** A centred grey [text] shown instead of the list. */
@Composable
private fun TabMessage(text: String) {
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

/** One reservation: the entrant's name, then its campsite, dates and guests, as in mockup O14. */
@Composable
private fun ReservationRowItem(row: ReservationRow, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reservation_row"),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(GreenFillStrong, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(row.entrantName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${row.campsiteName} · ${row.datesText} · ${row.guestsText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}
