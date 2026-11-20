/*
 * MyReservationsScreen.kt
 * The Reservations tab (mockup E14), with Active and Past tabs. The Active tab lists the entrant's
 * confirmed reservations whose check-out is today or later, soonest first (US 01.06.01). Tapping
 * one opens its details (mockup E15).
 * Known issues: the Past tab (US 01.06.02) is a placeholder. The grey square stands in for the
 * campsite's photo until images are added (US 02.01.02b).
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.GreenFillStrong
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes
import com.example.vipers.ui.theme.TextMuted

private enum class MyReservationsTab(val label: String) {
    ACTIVE("Active"),
    PAST("Past"),
}

/**
 * The entrant's reservations, split into Active and Past tabs (mockup E14, US 01.06.01).
 *
 * @param onOpenReservation Called with the reservation's ID when its row is tapped.
 * @param controller Loads the reservations; tests can pass one built with fake repositories.
 */
@Composable
fun MyReservationsScreen(
    onOpenReservation: (String) -> Unit,
    controller: ReservationController = viewModel(factory = ReservationController.Factory),
) {
    var selectedTab by rememberSaveable { mutableStateOf(MyReservationsTab.ACTIVE) }

    // reloads every time the screen is shown again: coming back from the details screen (so a
    // cancelled stay leaves the list) or bringing the app back from the background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.loadMyReservations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopBar(title = "My Reservations")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MyReservationsTab.entries.forEach { tab ->
                TabPill(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (selectedTab) {
            MyReservationsTab.ACTIVE -> ActiveTab(controller, onOpenReservation)
            MyReservationsTab.PAST -> Placeholder("Past reservations are added in US 01.06.02.")
        }
    }
}

/** One of the rounded tabs at the top of mockup E14; the [selected] one is filled in. */
@Composable
private fun TabPill(
    tab: MyReservationsTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .semantics { role = Role.Tab }
            .testTag("my_reservations_tab_${tab.name.lowercase()}"),
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

/** The Active tab: a loading indicator, a message, or the list of reservations (US 01.06.01). */
@Composable
private fun ActiveTab(controller: ReservationController, onOpenReservation: (String) -> Unit) {
    when {
        controller.isLoading ->
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        controller.loadFailed -> TabMessage("Couldn't load reservations")
        controller.myActive.isEmpty() -> TabMessage("No active reservations")
        else ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(controller.myActive, key = { it.id }) { reservation ->
                    MyReservationItem(reservation, onClick = { onOpenReservation(reservation.id) })
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
            .padding(32.dp)
            .testTag("my_reservations_message"),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** One reservation: the campsite name, its dates and guests, and its status, as in mockup E14. */
@Composable
private fun MyReservationItem(reservation: Reservation, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("my_reservation_row"),
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
                    .size(48.dp)
                    .background(GreenFillStrong, Shapes.small),
            )
            Column(Modifier.weight(1f)) {
                Text(reservation.campsiteName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = formatStayDates(reservation.checkIn, reservation.checkOut) +
                        " · " + formatGuests(reservation.groupSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(reservation.status)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

/** A small rounded label with the status: green for Confirmed, red for Rejected, grey otherwise. */
@Composable
private fun StatusChip(status: ReservationStatus) {
    val (textColor, fill) = when (status) {
        ReservationStatus.CONFIRMED -> Primary to GreenFill
        ReservationStatus.REJECTED -> Error to Error.copy(alpha = 0.12f)
        ReservationStatus.CANCELLED -> TextMuted to Divider
    }
    Text(
        text = status.name.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        maxLines = 1,
        modifier = Modifier
            .background(fill, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("reservation_status"),
    )
}
