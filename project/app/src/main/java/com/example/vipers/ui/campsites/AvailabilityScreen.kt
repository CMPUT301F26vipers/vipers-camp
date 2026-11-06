/*
 * AvailabilityScreen.kt
 * The owner's Availability screen (mockup O07, T-10), opened from Availability on My Campsite.
 * A month calendar where tapping a night opens or closes it, "+ Open a range" and "− Close a range"
 * open or close every night between two taps, and Save stores the open nights.
 * AvailabilityController does the work.
 * Known issues: none. Booked nights come from reservations (T-03) and can't be changed here.
 */
package com.example.vipers.ui.campsites

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Surface
import com.example.vipers.ui.theme.TextMain
import com.example.vipers.ui.theme.TextMuted
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Availability screen (mockup O07) for the campsite with [campsiteId]. Open nights are green,
 * closed ones white and booked ones dark, like the mockup's legend. Tests pass their own
 * [controller] with a fake repository.
 *
 * @param campsiteId ID of the campsite whose nights are being set.
 * @param onBack Called when pressing back.
 * @param controller ViewModel that loads, changes and saves the calendar.
 */
@Composable
fun AvailabilityScreen(
    campsiteId: String,
    onBack: () -> Unit,
    controller: AvailabilityController =
        viewModel(factory = AvailabilityController.factory(campsiteId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }
    val calendar = controller.calendar

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Availability", onBack = onBack)

        when {
            controller.loading && calendar == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> Text(controller.error!!, Modifier.padding(16.dp))
            calendar != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MonthHeader(
                        controller.month,
                        onPrevious = controller::showPreviousMonth,
                        onNext = controller::showNextMonth,
                    )
                    if (controller.pickingRange) {
                        RangeButton("Cancel date range", "button_cancel_range") {
                            controller.cancelRange()
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val half = Modifier.weight(1f)
                            RangeButton("+ Open a range", "button_open_range", half) {
                                controller.startRange(open = true)
                            }
                            RangeButton("− Close a range", "button_close_range", half) {
                                controller.startRange(open = false)
                            }
                        }
                    }
                    MonthGrid(
                        controller.month,
                        calendar,
                        controller.rangeStart,
                        onNightTap = { night -> controller.onNightTap(night) },
                    )
                    Legend()
                    controller.message?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.testTag("availability_message"),
                        )
                    }
                }
                Button(
                    onClick = controller::save,
                    enabled = !controller.saving,
                    shape = ButtonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(ButtonHeight)
                        .testTag("button_save_availability"),
                ) {
                    Text(
                        if (controller.saving) "Saving…" else "Save",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** "‹ May 2027 ›": the shown [month] with arrows to the previous and next months. */
@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = "Previous month",
                modifier = Modifier.rotate(180f),
            )
        }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}",
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
        }
    }
}

/** An outlined button that starts or cancels choosing a date range, full width by default. */
@Composable
private fun RangeButton(
    text: String,
    tag: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = ButtonShape,
        border = BorderStroke(1.dp, Primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
        modifier = modifier.testTag(tag),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The nights of [month] in weeks from Sunday to Saturday. Each night shows whether it's open,
 * closed or booked in [calendar]; [rangeStart] gets a thick outline while choosing a range.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    calendar: AvailabilityCalendar,
    rangeStart: String?,
    onNightTap: (String) -> Unit,
) {
    val today = LocalDate.now()
    // Sunday is the first column, so a month starting on Sunday has no empty cells before it
    val blanks = month.atDay(1).dayOfWeek.value % 7
    val cells = List(blanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            for (day in listOf("S", "M", "T", "W", "T", "F", "S")) {
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (week in cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (date in week) {
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val night = date.toString()
                        NightCell(
                            day = date.dayOfMonth,
                            calendar = calendar,
                            night = night,
                            isPast = calendar.isPast(night, today),
                            isRangeStart = night == rangeStart,
                            onClick = { onNightTap(night) },
                        )
                    }
                }
                // keep the last week's nights the same width as the others
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One night in the calendar: green when open, dark when booked, white when closed. */
@Composable
private fun RowScope.NightCell(
    day: Int,
    calendar: AvailabilityCalendar,
    night: String,
    isPast: Boolean,
    isRangeStart: Boolean,
    onClick: () -> Unit,
) {
    val state = when {
        calendar.isHeld(night) -> "Booked"
        calendar.isOpen(night) -> "Available"
        else -> "Closed"
    }
    val fill = when (state) {
        "Booked" -> TextMain
        "Available" -> Primary
        else -> Surface
    }
    val outline = when {
        isRangeStart -> BorderStroke(3.dp, TextMain)
        state == "Closed" -> BorderStroke(1.dp, Border)
        else -> BorderStroke(0.dp, Color.Transparent)
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .alpha(if (isPast) 0.4f else 1f)
            .background(fill, shape)
            .border(outline, shape)
            .clickable(onClick = onClick)
            .semantics { stateDescription = state }
            .testTag("night_$night"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.bodySmall,
            color = if (state == "Closed") TextMain else Color.White,
        )
    }
}

/** The key under the calendar, like the mockup: Available, Closed, Booked. */
@Composable
private fun Legend() {
    val keys = listOf("Available" to Primary, "Closed" to Border, "Booked" to TextMain)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((label, color) in keys) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
