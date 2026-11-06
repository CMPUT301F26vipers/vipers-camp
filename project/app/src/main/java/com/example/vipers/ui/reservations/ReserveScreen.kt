/*
 * ReserveScreen.kt
 * The Reserve screen (mockup E11, US 01.04.01), opened from Campsite Details when the user won a
 * lottery there. It shows the reserve-by date, the campsite, the lottery's nights with the taken
 * ones greyed out, the nightly price times the picked nights and the total, and the campsite's
 * rules and cancellation policy. ReserveController does the work.
 * Known issues: the Reserve and Decline buttons, the guests field and the "I accept the campsite
 * rules" box come with T-03, 01.04.03 and 02.11.01; opening this screen from the win
 * notification comes with T-13 (#77).
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Lottery
import com.example.vipers.ui.campsites.RulesAndPolicySection
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.common.shortDate
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Divider
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
            }
        }
    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
