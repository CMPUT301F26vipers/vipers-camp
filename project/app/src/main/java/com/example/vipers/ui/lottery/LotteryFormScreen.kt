
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vipers.domain.model.AvailabilityCalendar
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.ui.common.TopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun LotteryFormScreen(
    campsiteId: String,
    campsiteRepository: CampsiteRepository,
    lotteryRepository: LotteryRepository,
    ownerId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    var campsite by remember { mutableStateOf<Campsite?>(null) }
    var availability by remember { mutableStateOf<AvailabilityCalendar?>(null) }
    var availableNights by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedNights by remember { mutableStateOf(setOf<String>()) }

    var rangeStart by remember { mutableStateOf("") }
    var rangeEnd by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(campsiteId) {
        loading = true
        try {
            val result = campsiteRepository.getCampsite(campsiteId)

            if (result == null || result.ownerId != ownerId) {
                error = "Campsite not found or you do not own it."
            } else {
                campsite = result
                val calendar = campsiteRepository.getAvailability(campsiteId)
                availability = calendar

                availableNights = calendar.openNights
                    .filterNot { it in calendar.heldNights }
                    .distinct()
                    .sorted()

                error = null
            }
        } catch (e: Exception) {
            error = "Unable to load campsite availability."
        } finally {
            loading = false
        }
    }

    fun showDatePicker(isStartDate: Boolean) {
        val current = Calendar.getInstance()

        val startCalendar = Calendar.getInstance().apply {
            if (rangeStart.isNotEmpty()) {
                time = dateFormat.parse(rangeStart) ?: time
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
                    rangeStart = formatted

                    // Reset the end date if it is now before the start date.
                    if (rangeEnd.isNotEmpty()) {
                        val end = dateFormat.parse(rangeEnd)
                        if (end != null && selectedDate.time.after(end)) {
                            rangeEnd = ""
                        }
                    }
                } else {
                    rangeEnd = formatted
                }
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        )

        picker.datePicker.minDate =
            if (isStartDate) {
                System.currentTimeMillis()
            } else if (rangeStart.isNotEmpty()) {
                startCalendar.timeInMillis
            } else {
                System.currentTimeMillis()
            }

        picker.show()
    }

    fun loadSelectedRange() {
        try {
            val start = dateFormat.parse(rangeStart)
                ?: throw IllegalArgumentException()

            val end = dateFormat.parse(rangeEnd)
                ?: throw IllegalArgumentException()

            if (end.before(start)) {
                error = "End date must be on or after the start date."
                return
            }

            val cursor = Calendar.getInstance().apply {
                time = start
            }

            val endCalendar = Calendar.getInstance().apply {
                time = end
            }

            val newNights = mutableListOf<String>()

            while (!cursor.after(endCalendar)) {
                val night = dateFormat.format(cursor.time)

                if (night !in (availability?.heldNights ?: emptyList())) {
                    newNights.add(night)
                }

                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }

            availableNights = (availableNights + newNights)
                .distinct()
                .sorted()

            error = if (availableNights.isEmpty()) {
                "No available nights in this date range."
            } else {
                null
            }
        } catch (e: Exception) {
            error = "Please select valid start and end dates."
        }
    }

    LaunchedEffect(saving) {
        if (saving) {
            try {
                val latestCalendar =
                    campsiteRepository.getAvailability(campsiteId)

                val nightsToSave = selectedNights
                    .filterNot { it in latestCalendar.heldNights }

                if (nightsToSave.isEmpty()) {
                    throw IllegalStateException(
                        "Your selected nights are no longer available."
                    )
                }

                campsiteRepository.saveAvailability(
                    latestCalendar.copy(
                        openNights = (
                                latestCalendar.openNights + nightsToSave
                                ).distinct().sorted()
                    )
                )

                val lottery = Lottery(
                    ownerId = ownerId,
                    campsiteIds = listOf(campsiteId),
                    nightsOffered = nightsToSave.sorted(),
                )

                lotteryRepository.addLottery(lottery)
                onBack()
            } catch (e: Exception) {
                error = e.message ?: "Unable to save lottery."
                saving = false
            }
        }
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
                loading -> CircularProgressIndicator()

                error != null && campsite == null -> Text(error!!)

                campsite != null -> {
                    Text("Campsite: ${campsite!!.name}")

                    HorizontalDivider()

                    Text("Choose the nights available for this lottery.")

                    Text("Select a date range:")

                    Button(
                        onClick = { showDatePicker(true) }
                    ) {
                        Text(
                            if (rangeStart.isEmpty())
                                "Choose start date"
                            else
                                "Start: $rangeStart"
                        )
                    }

                    Button(
                        onClick = { showDatePicker(false) }
                    ) {
                        Text(
                            if (rangeEnd.isEmpty())
                                "Choose end date"
                            else
                                "End: $rangeEnd"
                        )
                    }

                    Button(
                        onClick = { loadSelectedRange() },
                        enabled = rangeStart.isNotEmpty() &&
                                rangeEnd.isNotEmpty()
                    ) {
                        Text("Load nights")
                    }

                    HorizontalDivider()

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Select nights to offer:")

                        Button(
                            onClick = {
                                selectedNights =
                                    if (selectedNights.size == availableNights.size) {
                                        emptySet()
                                    } else {
                                        availableNights.toSet()
                                    }
                            },
                            enabled = availableNights.isNotEmpty()
                        ) {
                            Text(
                                if (availableNights.isNotEmpty() &&
                                    selectedNights.size == availableNights.size
                                ) "Deselect All" else "Select All"
                            )
                        }
                    }

                    if (availableNights.isEmpty()) {
                        Text("No available nights loaded yet.")
                    }

                    availableNights.forEach { night ->
                        Row {
                            Checkbox(
                                checked = night in selectedNights,
                                onCheckedChange = { checked ->
                                    selectedNights =
                                        if (checked) {
                                            selectedNights + night
                                        } else {
                                            selectedNights - night
                                        }
                                }
                            )

                            Text(
                                text = night,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    Text("${selectedNights.size} night(s) selected")

                    if (error != null) {
                        Text(error!!)
                    }

                    Button(
                        onClick = {
                            error = null
                            saving = true
                        },
                        enabled = selectedNights.isNotEmpty() && !saving
                    ) {
                        Text(
                            if (saving) "Saving..." else "Create Lottery"
                        )
                    }
                }
            }
        }
    }
}
