/*
 * ReserveScreen.kt
 * The Reserve screen (mockup E11, US 01.04.01), opened from Campsite Details or the win
 * notification when the user won a lottery. It shows the reserve-by date, the campsite, the
 * lottery's nights with the taken ones greyed out, the nightly price times the picked nights and
 * total, the group size selector, the Reserve action button (T-03), the confirmation dialog
 * (01.05.01), and the campsite rules. A winner can decline the win here (US 01.04.03, mockup E12).
 * ReserveController does the work.
 * Known issues: choosing between several campsites (01.04.02) is added by that story. Declining
 * doesn't pick a replacement winner yet (T-12).
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Reservation
import com.example.vipers.ui.campsites.RulesAndPolicySection
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.common.shortDate
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputHeight
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Surface
import com.example.vipers.ui.theme.TextMain
import com.example.vipers.ui.theme.TextMuted
import java.time.LocalDate

/**
 * The Reserve screen (mockup E11) for the user's win in [lotteryId] at [campsiteId]. Free nights
 * are white, picked ones green and taken or closed ones grey, like the mockup. Tests pass their
 * own [controller] with fake repositories.
 *
 * @param lotteryId ID of the lottery the user won.
 * @param campsiteId ID of the campsite to reserve, or null for the lottery's first campsite.
 * @param onBack Called when pressing back.
 * @param controller ViewModel that loads the win and keeps track of the picked nights.
 */
@Composable
fun ReserveScreen(
    lotteryId: String,
    campsiteId: String?,
    onBack: () -> Unit,
    controller: ReserveController =
        viewModel(factory = ReserveController.factory(lotteryId, campsiteId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }
    val lottery = controller.lottery
    val campsite = controller.campsite

    val confirmed = controller.confirmedReservation
    if (confirmed != null) {
        ReservationConfirmationDialog(
            reservation = confirmed,
            onDismiss = {
                controller.dismissConfirmation()
                onBack()
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Reserve", onBack = onBack)

        when {
            controller.loading && lottery == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> Text(controller.error!!, Modifier.padding(16.dp))
            lottery == null || campsite == null ->
                Text("This lottery is no longer available.", Modifier.padding(16.dp))
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OfferBanner(controller.offerState, lottery)

                Label("Campsite")
                // one campsite for now; choosing between several comes with 01.04.02
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(InputHeight)
                        .background(Surface, InputShape)
                        .border(1.dp, Border, InputShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(campsite.name, modifier = Modifier.testTag("reserve_campsite"))
                }

                Label("Pick nights")
                NightGrid(
                    nights = controller.nights,
                    freeNights = controller.freeNights,
                    selectedNights = controller.selectedNights,
                    canPick = controller.offerState == OfferState.OPEN,
                    onNightTap = controller::onNightTap,
                )
                if (controller.noNightsLeft) {
                    Text(
                        "No nights are left at this campsite. You can decline your win so " +
                            "someone else gets a chance.",
                        color = TextMuted,
                        modifier = Modifier.testTag("reserve_no_nights"),
                    )
                }

                GuestsSection(
                    groupSize = controller.groupSize,
                    maxOccupancy = campsite.maxOccupancy,
                    canEdit = controller.offerState == OfferState.OPEN,
                    onDecrease = controller::decrementGroupSize,
                    onIncrease = controller::incrementGroupSize,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        controller.nightsTimesPriceText ?: "",
                        color = TextMuted,
                        modifier = Modifier.weight(1f).testTag("reserve_nights_price"),
                    )
                    Text(
                        controller.totalText ?: "",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("reserve_total"),
                    )
                }
                HorizontalDivider(color = Divider)
                RulesAndPolicySection(campsite)

                if (controller.reserveError != null) {
                    Text(
                        text = controller.reserveError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("reserve_error_message"),
                    )
                }

                Button(
                    onClick = controller::reserve,
                    enabled = controller.offerState == OfferState.OPEN && !controller.isReserving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("button_reserve"),
                ) {
                    if (controller.isReserving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("reserve_loading"),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(
                            "Reserve",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }

                DeclineSection(controller)
            }
        }
    }
}

/**
 * The Decline button (mockup E11, marker 7), shown only while the win can be declined (US 01.04.03).
 * Tapping it opens [DeclineWinDialog]; once declined, it's replaced by "You declined this win.".
 */
@Composable
private fun DeclineSection(controller: ReserveController) {
    // saved, so turning the phone doesn't close the dialog
    var showDialog by rememberSaveable { mutableStateOf(false) }

    when {
        controller.canDecline -> OutlinedButton(
            onClick = { showDialog = true },
            enabled = !controller.isDeclining,
            shape = ButtonShape,
            border = BorderStroke(1.5.dp, Error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .testTag("reserve_decline"),
        ) {
            Text("Decline", fontWeight = FontWeight.SemiBold)
        }
        controller.entry?.status == EntryStatus.DECLINED -> Text(
            "You declined this win.",
            color = TextMuted,
            modifier = Modifier.testTag("reserve_declined"),
        )
    }
    controller.declineError?.let { message ->
        Text(message, color = Error, modifier = Modifier.testTag("reserve_decline_error"))
    }

    if (showDialog) {
        DeclineWinDialog(
            onConfirm = {
                showDialog = false
                controller.declineWin()
            },
            onCancel = { showDialog = false },
        )
    }
}

/**
 * Asks the winner to confirm declining their win (mockup E12, US 01.04.03). [onConfirm] declines;
 * [onCancel] only closes the dialog, as does tapping outside it.
 */
@Composable
private fun DeclineWinDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Decline this win?") },
        text = { Text("You won't be able to reserve from it.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("decline_confirm")) {
                Text("Confirm", color = Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("decline_cancel")) {
                Text("Cancel")
            }
        },
    )
}

/**
 * The banner at the top (mockup E11, marker 1): "You won! Reserve by May 31" while booking is open,
 * or why the user can't pick nights.
 */
@Composable
private fun OfferBanner(state: OfferState, lottery: Lottery) {
    val reserveBy = shortDate(lottery.bookingEnd)
    val text = when (state) {
        OfferState.OPEN -> "You won! Reserve by $reserveBy"
        OfferState.NOT_YET_OPEN ->
            "You won! Booking opens ${shortDate(lottery.bookingStart)}. Reserve by $reserveBy"
        OfferState.EXPIRED -> "This offer expired on $reserveBy. You can no longer reserve."
        OfferState.USED -> "You have already used this win."
        OfferState.NO_WIN -> "Only winners of this lottery can reserve."
    }
    val good = state == OfferState.OPEN || state == OfferState.NOT_YET_OPEN
    Text(
        text,
        color = if (good) Primary else TextMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (good) GreenFill else Divider, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("reserve_banner"),
    )
}

/** A small bold label above a field, like "Campsite" and "Pick nights" on the mockup. */
@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

/**
 * The lottery's [nights], seven to a row like mockup E11 (marker 3). Nights in [freeNights] can be
 * tapped when [canPick]; the others are greyed out.
 */
@Composable
private fun NightGrid(
    nights: List<String>,
    freeNights: List<String>,
    selectedNights: List<String>,
    canPick: Boolean,
    onNightTap: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in nights.chunked(7)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (night in row) {
                    NightChip(
                        night = night,
                        free = canPick && night in freeNights,
                        selected = night in selectedNights,
                        onClick = { onNightTap(night) },
                    )
                }
                // keep the last row's nights the same width as the others
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One night: green when picked, white when it can be picked, grey when it's taken or closed. */
@Composable
private fun RowScope.NightChip(
    night: String,
    free: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val state = when {
        selected -> "Selected"
        free -> "Free"
        else -> "Taken"
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .background(
                when (state) {
                    "Selected" -> Primary
                    "Free" -> Surface
                    else -> Divider
                },
                shape,
            )
            .border(
                if (state == "Free") BorderStroke(1.dp, Border) else BorderStroke(0.dp, Color.Transparent),
                shape,
            )
            .clickable(enabled = free, onClick = onClick)
            .semantics { stateDescription = state }
            .testTag("night_$night"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${LocalDate.parse(night).dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = when (state) {
                "Selected" -> Color.White
                "Free" -> TextMain
                else -> TextMuted
            },
        )
    }
}

/** Group size counter matching campsite max occupancy (T-03). */
@Composable
private fun GuestsSection(
    groupSize: Int,
    maxOccupancy: Int,
    canEdit: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Guests")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$groupSize ${if (groupSize == 1) "guest" else "guests"} (max $maxOccupancy)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("reserve_guests_text"),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val canDec = canEdit && groupSize > 1
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            BorderStroke(1.5.dp, if (canDec) Primary else TextMuted),
                            CircleShape,
                        )
                        .clickable(enabled = canDec, onClick = onDecrease)
                        .testTag("reserve_guests_decrease"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "−",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canDec) Primary else TextMuted,
                    )
                }

                Text(
                    text = "$groupSize",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .testTag("reserve_guests_count"),
                )

                val canInc = canEdit && groupSize < maxOccupancy
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            BorderStroke(1.5.dp, if (canInc) Primary else TextMuted),
                            CircleShape,
                        )
                        .clickable(enabled = canInc, onClick = onIncrease)
                        .testTag("reserve_guests_increase"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canInc) Primary else TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * The reservation confirmation dialog (mockup E12, US 01.05.01) shown after a winner
 * reserves their nights. Shows the reservation number, campsite name, stay dates,
 * guests count, and total price.
 */
@Composable
fun ReservationConfirmationDialog(
    reservation: Reservation,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_reservation_confirmed"),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Reservation Confirmed!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Text(
                    text = reservation.reservationNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("confirm_reservation_number"),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConfirmationRow(
                    label = "Campsite",
                    value = reservation.campsiteName,
                    tag = "confirm_campsite_name",
                )
                ConfirmationRow(
                    label = "Dates",
                    value = formatStayDates(reservation.checkIn, reservation.checkOut),
                    tag = "confirm_dates",
                )
                ConfirmationRow(
                    label = "Guests",
                    value = formatGuests(reservation.groupSize),
                    tag = "confirm_guests",
                )
                ConfirmationRow(
                    label = "Total",
                    value = reservation.totalPriceText,
                    tag = "confirm_total_price",
                    isBold = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("button_confirm_done"),
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** A single label-value row inside the reservation confirmation dialog (US 01.05.01). */
@Composable
private fun ConfirmationRow(
    label: String,
    value: String,
    tag: String,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.testTag(tag),
        )
    }
}
