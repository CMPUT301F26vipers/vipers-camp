/*
 * CampsiteFilterDialog.kt
 * The filter dialog opened from Home (US 01.01.02, mockup E02b), allowing entrants to filter
 * campsites by region (fixed list), date range, and number of guests.
 * Known issues: "Preferred tags (optional)" from mockup E02b is added by the WOW factor (W-01)
 * story for calculating percentage match, not 01.01.02.
 */
package com.example.vipers.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vipers.R
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import java.time.LocalDate
import java.util.Calendar

/**
 * Filter dialog for filtering campsites on Home (US 01.01.02, mockup E02b).
 * Allows entrant to pick a region from a fixed list, pick/enter a date range, and adjust
 * the number of guests.
 */
@Composable
fun CampsiteFilterDialog(
    currentRegion: Region?,
    currentStartDate: String?,
    currentEndDate: String?,
    currentMinGuests: Int?,
    onApply: (region: Region?, startDate: String?, endDate: String?, minGuests: Int?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedRegion by remember { mutableStateOf(currentRegion) }
    var startDate by remember { mutableStateOf(currentStartDate ?: "") }
    var endDate by remember { mutableStateOf(currentEndDate ?: "") }
    var guestsCount by remember { mutableStateOf(currentMinGuests?.takeIf { it > 0 }) }

    var datesRawText by remember {
        val initialText = when {
            !currentStartDate.isNullOrBlank() && !currentEndDate.isNullOrBlank() && currentStartDate != currentEndDate ->
                "$currentStartDate to $currentEndDate"
            !currentStartDate.isNullOrBlank() -> currentStartDate
            !currentEndDate.isNullOrBlank() -> currentEndDate
            else -> ""
        }
        mutableStateOf(initialText)
    }

    fun openDateRangePicker() {
        val calendar = Calendar.getInstance()
        try {
            if (startDate.isNotBlank()) {
                val parsed = LocalDate.parse(startDate.trim())
                calendar.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
            }
        } catch (_: Exception) {}

        val startPicker = DatePickerDialog(
            context,
            { _, sYear, sMonth, sDay ->
                val sDate = LocalDate.of(sYear, sMonth + 1, sDay)
                val sStr = sDate.toString()
                startDate = sStr

                val endCal = Calendar.getInstance().apply {
                    set(sYear, sMonth, sDay)
                }
                val endPicker = DatePickerDialog(
                    context,
                    { _, eYear, eMonth, eDay ->
                        val eDate = LocalDate.of(eYear, eMonth + 1, eDay)
                        val eStr = eDate.toString()
                        endDate = eStr
                        datesRawText = "$sStr to $eStr"
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH),
                )
                endPicker.datePicker.minDate = endCal.timeInMillis
                endPicker.setTitle("Select end date")
                endPicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        startPicker.setTitle("Select start date")
        startPicker.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Region
                RegionSection(
                    selectedRegion = selectedRegion,
                    onRegionSelected = { selectedRegion = it },
                )

                // 2. Dates
                DatesSection(
                    datesText = datesRawText,
                    onDatesTextChange = { newText ->
                        datesRawText = newText
                        val trimmed = newText.trim()
                        if (trimmed.isEmpty()) {
                            startDate = ""
                            endDate = ""
                        } else {
                            val dateMatches = Regex("""\d{4}-\d{2}-\d{2}""").findAll(trimmed).map { it.value }.toList()
                            if (dateMatches.size >= 2) {
                                startDate = dateMatches[0]
                                endDate = dateMatches[1]
                            } else if (dateMatches.size == 1) {
                                startDate = dateMatches[0]
                                endDate = dateMatches[0]
                            } else {
                                val parts = trimmed.split(Regex("""\s+to\s+|\s+[–—]\s+|\s+-\s+"""))
                                if (parts.size >= 2) {
                                    startDate = parts[0].trim()
                                    endDate = parts[1].trim()
                                } else {
                                    startDate = trimmed
                                    endDate = trimmed
                                }
                            }
                        }
                    },
                    onOpenPicker = { openDateRangePicker() },
                    onClearDates = {
                        datesRawText = ""
                        startDate = ""
                        endDate = ""
                    },
                )

                // 3. Guests
                GuestsSection(
                    guestsCount = guestsCount,
                    onGuestsChange = { guestsCount = it },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClear,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { contentDescription = "Clear filters" }
                        .testTag("button_clear_filters"),
                ) {
                    Text(
                        "Clear",
                        fontWeight = FontWeight.SemiBold,
                        color = Primary,
                    )
                }
                Button(
                    onClick = {
                        onApply(selectedRegion, startDate, endDate, guestsCount)
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("button_apply_filters"),
                ) {
                    Text(
                        "Apply",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        },
    )
}

@Composable
private fun RegionSection(
    selectedRegion: Region?,
    onRegionSelected: (Region?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            "Region",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            OutlinedTextField(
                value = selectedRegion?.displayName ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Any region") },
                shape = InputShape,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                    )
                },
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
                    .testTag("field_filter_region")
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Any region") },
                    onClick = {
                        onRegionSelected(null)
                        expanded = false
                    },
                )
                for (region in Region.entries) {
                    DropdownMenuItem(
                        text = { Text(region.displayName) },
                        onClick = {
                            onRegionSelected(region)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DatesSection(
    datesText: String,
    onDatesTextChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onClearDates: () -> Unit,
) {
    Column {
        Text(
            "Dates",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = datesText,
            onValueChange = onDatesTextChange,
            placeholder = { Text("Pick dates") },
            singleLine = true,
            shape = InputShape,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field_filter_dates"),
            trailingIcon = {
                if (datesText.isNotEmpty()) {
                    IconButton(onClick = onClearDates) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear dates")
                    }
                } else {
                    IconButton(
                        onClick = onOpenPicker,
                        modifier = Modifier.testTag("button_pick_dates"),
                    ) {
                        Icon(painterResource(R.drawable.ic_calendar), contentDescription = "Pick dates")
                    }
                }
            },
        )
    }
}

@Composable
private fun GuestsSection(
    guestsCount: Int?,
    onGuestsChange: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Guests",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Minus circular button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(BorderStroke(1.5.dp, Primary), CircleShape)
                    .clickable {
                        val current = guestsCount ?: 1
                        if (current > 1) onGuestsChange(current - 1)
                        else onGuestsChange(null)
                    }
                    .testTag("button_guests_minus"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
            }

            // Count display
            Text(
                text = guestsCount?.toString() ?: "Any",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 36.dp)
                    .padding(horizontal = 4.dp)
                    .testTag("text_guests_count"),
            )

            // Plus circular button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(BorderStroke(1.5.dp, Primary), CircleShape)
                    .clickable {
                        val current = guestsCount ?: 0
                        onGuestsChange(current + 1)
                    }
                    .testTag("button_guests_plus"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
            }
        }
    }
}
