/*
 * LotteryFormScreen.kt
 * The screen to create a new lottery for a campsite (mockup O08).
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Screen allowing campsite owners to configure and publish a new lottery (mockup O08),
 * including multi-campsite selection, nights offered, registration period, booking period,
 * winner count, and optional waiting list limit.
 *
 * @param campsiteId ID of the initial campsite selected.
 * @param onBack Callback triggered when navigating back.
 * @param controller ViewModel managing creation state and validation.
 */
@Composable
fun LotteryFormScreen(
    campsiteId: String,
    onBack: () -> Unit,
    controller: LotteryController = viewModel(factory = LotteryController.factory(campsiteId))
) {
    val context = LocalContext.current

    LaunchedEffect(controller.saved) {
        if (controller.saved) {
            onBack()
        }
    }

    fun formatDateTime(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Not set"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(millis))
    }

    fun showDateTimePicker(initialMillis: Long?, onSelected: (Long) -> Unit) {
        val initialCal = Calendar.getInstance().apply {
            if (initialMillis != null && initialMillis > 0L) {
                timeInMillis = initialMillis
            }
        }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val resultCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onSelected(resultCal.timeInMillis)
                    },
                    initialCal.get(Calendar.HOUR_OF_DAY),
                    initialCal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            initialCal.get(Calendar.YEAR),
            initialCal.get(Calendar.MONTH),
            initialCal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = "Create Lottery",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null && controller.campsite == null -> Text(controller.error!!)
                else -> {
                    // 1. Campsite selection (multi-select)
                    Text("Select Campsites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (controller.ownedCampsites.isEmpty()) {
                        controller.campsite?.let { camp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = true, onCheckedChange = {})
                                Text(camp.name)
                            }
                        }
                    } else {
                        controller.ownedCampsites.forEach { camp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = camp.id in controller.selectedCampsiteIds,
                                    onCheckedChange = { checked -> controller.toggleCampsite(camp.id, checked) }
                                )
                                Text(camp.name)
                            }
                        }
                    }

                    HorizontalDivider()

                    // 2. Nights Offered
                    Text("Nights Offered", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = controller.nightsOfferedText,
                        onValueChange = { controller.onNightsOfferedTextChange(it) },
                        label = { Text("Nights description") },
                        placeholder = { Text("e.g. May 2027 weekends") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_nights_offered")
                    )

                    HorizontalDivider()

                    // 3. Registration Period
                    Text("Registration Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDateTimePicker(controller.registrationStartMillis) {
                                    controller.setRegistrationStart(it)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_reg_start")
                        ) {
                            Text("Start: ${formatDateTime(controller.registrationStartMillis)}")
                        }

                        Button(
                            onClick = {
                                showDateTimePicker(controller.registrationEndMillis) {
                                    controller.setRegistrationEnd(it)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_reg_end")
                        ) {
                            Text("End: ${formatDateTime(controller.registrationEndMillis)}")
                        }
                    }

                    HorizontalDivider()

                    // 4. Booking Period
                    Text("Booking Period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDateTimePicker(controller.bookingStartMillis) {
                                    controller.setBookingStart(it)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_book_start")
                        ) {
                            Text("Start: ${formatDateTime(controller.bookingStartMillis)}")
                        }

                        Button(
                            onClick = {
                                showDateTimePicker(controller.bookingEndMillis) {
                                    controller.setBookingEnd(it)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_book_end")
                        ) {
                            Text("End: ${formatDateTime(controller.bookingEndMillis)}")
                        }
                    }

                    HorizontalDivider()

                    // 5. Winners
                    Text("Winners", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = controller.winnersCountText,
                        onValueChange = { controller.onWinnersCountChange(it) },
                        label = { Text("Number of winners") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_winners_count")
                    )

                    // 6. List Limit (optional)
                    Text("Waiting List Limit (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = controller.waitlistCapacityText,
                        onValueChange = { controller.onWaitlistCapacityChange(it) },
                        label = { Text("Max capacity (leave blank for no limit)") },
                        placeholder = { Text("No limit") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_waitlist_limit")
                    )

                    if (controller.error != null) {
                        Text(
                            text = controller.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 7. Create Lottery Primary Button at Bottom
                    Button(
                        onClick = {
                            controller.clearError()
                            controller.saveLottery()
                        },
                        enabled = !controller.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("button_save_lottery")
                    ) {
                        Text(if (controller.saving) "Saving..." else "Create Lottery")
                    }
                }
            }
        }
    }
}
