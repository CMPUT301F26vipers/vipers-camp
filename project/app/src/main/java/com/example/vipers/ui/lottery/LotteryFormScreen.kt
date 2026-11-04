/*
 * LotteryFormScreen.kt
 * The screen to create a new lottery for a campsite.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun LotteryFormScreen(
    campsiteId: String,
    onBack: () -> Unit,
    controller: LotteryController = viewModel(factory = LotteryController.factory(campsiteId))
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    LaunchedEffect(controller.saved) {
        if (controller.saved) {
            onBack()
        }
    }

    fun showDatePicker(isStartDate: Boolean) {
        val current = Calendar.getInstance()
        val startCalendar = Calendar.getInstance().apply {
            if (controller.rangeStart.isNotEmpty()) {
                time = dateFormat.parse(controller.rangeStart) ?: time
            }
        }

        val picker = DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                val formatted = dateFormat.format(selectedDate.time)
                if (isStartDate) {
                    controller.onRangeStartChange(formatted)
                } else {
                    controller.onRangeEndChange(formatted)
                }
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        )

        picker.datePicker.minDate =
            if (isStartDate) {
                System.currentTimeMillis()
            } else if (controller.rangeStart.isNotEmpty()) {
                startCalendar.timeInMillis
            } else {
                System.currentTimeMillis()
            }

        picker.show()
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null && controller.campsite == null -> Text(controller.error!!)
                controller.campsite != null -> {
                    Text("Campsite: ${controller.campsite!!.name}")

                    HorizontalDivider()

                    Text("Choose the nights available for this lottery.")
                    Text("Select a date range:")

                    OutlinedTextField(
                        value = controller.rangeStart,
                        onValueChange = { controller.onRangeStartChange(it) },
                        label = { Text("Start Date (yyyy-MM-dd)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_range_start")
                    )

                    Button(onClick = { showDatePicker(true) }) {
                        Text(
                            if (controller.rangeStart.isEmpty()) "Choose start date from picker"
                            else "Start Picker: ${controller.rangeStart}"
                        )
                    }

                    OutlinedTextField(
                        value = controller.rangeEnd,
                        onValueChange = { controller.onRangeEndChange(it) },
                        label = { Text("End Date (yyyy-MM-dd)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_range_end")
                    )

                    Button(onClick = { showDatePicker(false) }) {
                        Text(
                            if (controller.rangeEnd.isEmpty()) "Choose end date from picker"
                            else "End Picker: ${controller.rangeEnd}"
                        )
                    }

                    Button(
                        onClick = { controller.loadSelectedRange() },
                        enabled = controller.rangeStart.isNotEmpty() && controller.rangeEnd.isNotEmpty()
                    ) {
                        Text("Load nights")
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Select nights to offer:")

                        Button(
                            onClick = { controller.toggleSelectAll() },
                            enabled = controller.availableNights.isNotEmpty()
                        ) {
                            Text(
                                if (controller.availableNights.isNotEmpty() &&
                                    controller.selectedNights.size == controller.availableNights.size
                                ) "Deselect All" else "Select All"
                            )
                        }
                    }

                    if (controller.availableNights.isEmpty()) {
                        Text("No available nights loaded yet.")
                    }

                    controller.availableNights.forEach { night ->
                        Row {
                            Checkbox(
                                checked = night in controller.selectedNights,
                                onCheckedChange = { checked ->
                                    controller.toggleNight(night, checked)
                                }
                            )

                            Text(
                                text = night,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    Text("${controller.selectedNights.size} night(s) selected")

                    if (controller.error != null) {
                        Text(controller.error!!)
                    }

                    Button(
                        onClick = {
                            controller.clearError()
                            controller.saveLottery()
                        },
                        enabled = controller.selectedNights.isNotEmpty() && !controller.saving,
                        modifier = Modifier.testTag("button_save_lottery")
                    ) {
                        Text(if (controller.saving) "Saving..." else "Create Lottery")
                    }
                }
            }
        }
    }
}
