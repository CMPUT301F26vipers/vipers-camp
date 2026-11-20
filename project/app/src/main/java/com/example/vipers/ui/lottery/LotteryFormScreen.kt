/*
 * LotteryFormScreen.kt
 * The screen to create a new lottery for a campsite (mockup O08) or edit an existing one (mockup O10).
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.ui.campsites.FormMode
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputHeight
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.PrimaryDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Screen allowing campsite owners to configure and publish a new lottery or edit an existing one (mockup O10),
 * including available night selection, nights description, registration period, booking period,
 * winner count, and optional waiting list limit.
 *
 * Text inputs conform to the team's [InputHeight] (46dp) rule using [BasicTextField]
 * with [OutlinedTextFieldDefaults.DecorationBox].
 *
 * @param campsiteId ID of the campsite selected (when creating).
 * @param lotteryId ID of the lottery being edited (when editing).
 * @param onBack Callback triggered when navigating back.
 * @param controller ViewModel managing creation/edit state and validation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryFormScreen(
    campsiteId: String? = null,
    lotteryId: String? = null,
    onBack: () -> Unit,
    controller: LotteryController = viewModel(
        factory = if (lotteryId != null) {
            LotteryController.editFactory(lotteryId)
        } else {
            LotteryController.factory(campsiteId ?: "")
        }
    )
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val editing = lotteryId != null || controller.mode == FormMode.EDIT

    LaunchedEffect(lotteryId) {
        if (lotteryId != null && controller.mode != FormMode.EDIT) {
            controller.loadForEdit(lotteryId)
        }
    }

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

    fun showDatePicker(isStartDate: Boolean) {
        val targetDateStr = if (isStartDate) controller.rangeStart else controller.rangeEnd
        val initialCal = Calendar.getInstance().apply {
            if (targetDateStr.isNotEmpty()) {
                val parsed = controller.parseStrictDate(targetDateStr)
                if (parsed != null) time = parsed
            }
        }

        val startCalendar = Calendar.getInstance().apply {
            if (controller.rangeStart.isNotEmpty()) {
                val parsed = controller.parseStrictDate(controller.rangeStart)
                if (parsed != null) time = parsed
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
            initialCal.get(Calendar.YEAR),
            initialCal.get(Calendar.MONTH),
            initialCal.get(Calendar.DAY_OF_MONTH)
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

    val pageTitle = if (editing) "Edit Lottery" else "Create Lottery"

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            title = pageTitle,
            onBack = onBack
        )

        when {
            editing && controller.notAllowed -> {
                Text(
                    text = "You do not own this lottery or it was not found.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }
            editing && controller.loadFailed -> {
                Text(
                    text = "Couldn't load the lottery. Check your connection and try again.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }
            editing && controller.mode != FormMode.EDIT -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                controller.loading && !editing -> CircularProgressIndicator()
                controller.error != null && controller.campsite == null && !editing -> Text(controller.error!!)
                else -> {
                    // O10 Header / Status Banner when editing
                    if (editing && controller.loadedLottery != null) {
                        val loaded = controller.loadedLottery!!
                        val now = System.currentTimeMillis()
                        val status = loaded.status(now)
                        val joinedCount = loaded.waitingUserIds.size

                        val statusBannerText = when (status) {
                            LotteryStatus.OPEN -> "Registration is open • $joinedCount joined"
                            LotteryStatus.UPCOMING -> "Registration upcoming"
                            LotteryStatus.DRAWN -> "Registration closed • Drawn"
                            LotteryStatus.CLOSED -> "Registration closed"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GreenFill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusBannerText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryDark,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (controller.isReadOnly) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = controller.readOnlyReason ?: "This lottery cannot be edited.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    controller.campsite?.let { camp ->
                        Text("Campsite: ${camp.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    val isReadOnly = controller.isReadOnly
                    val loaded = controller.loadedLottery
                    val isRegistrationOpen = editing && loaded != null && loaded.status(System.currentTimeMillis()) == LotteryStatus.OPEN

                    // 1. Selectable Available Nights Range & Checkboxes
                    Text("Select Offered Nights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Choose date range to load available nights:")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDatePicker(true) },
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_range_start")
                        ) {
                            Text(if (controller.rangeStart.isEmpty()) "Start Date" else controller.rangeStart)
                        }

                        Button(
                            onClick = { showDatePicker(false) },
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_range_end")
                        ) {
                            Text(if (controller.rangeEnd.isEmpty()) "End Date" else controller.rangeEnd)
                        }
                    }

                    Button(
                        onClick = { controller.loadSelectedRange() },
                        enabled = !isReadOnly && controller.rangeStart.isNotEmpty() && controller.rangeEnd.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load nights")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Available nights:")
                        Button(
                            onClick = { controller.toggleSelectAll() },
                            enabled = !isReadOnly && controller.availableNights.isNotEmpty()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = night in controller.selectedNights,
                                onCheckedChange = { checked ->
                                    controller.toggleNight(night, checked)
                                },
                                enabled = !isReadOnly
                            )
                            Text(text = night)
                        }
                    }

                    Text("${controller.selectedNights.size} night(s) selected")

                    HorizontalDivider()

                    // 2. Optional Nights Offered Description
                    Lottery46dpInputField(
                        label = "Nights Description (Optional)",
                        value = controller.nightsOfferedText,
                        onValueChange = { controller.onNightsOfferedTextChange(it) },
                        placeholder = "e.g. May 2027 weekends",
                        enabled = !isReadOnly,
                        testTag = "tag_nights_offered"
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
                            enabled = !isReadOnly && !isRegistrationOpen,
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
                            enabled = !isReadOnly,
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
                            enabled = !isReadOnly,
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
                            enabled = !isReadOnly,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_book_end")
                        ) {
                            Text("End: ${formatDateTime(controller.bookingEndMillis)}")
                        }
                    }

                    HorizontalDivider()

                    // 5. Winners
                    Lottery46dpInputField(
                        label = "Winners",
                        value = controller.winnersCountText,
                        onValueChange = { controller.onWinnersCountChange(it) },
                        placeholder = "Number of winners",
                        keyboardType = KeyboardType.Number,
                        enabled = !isReadOnly,
                        testTag = "tag_winners_count"
                    )

                    // 6. List Limit (optional)
                    Lottery46dpInputField(
                        label = "Waiting List Capacity (Optional)",
                        value = controller.waitlistCapacityText,
                        onValueChange = { controller.onWaitlistCapacityChange(it) },
                        placeholder = "No limit",
                        keyboardType = KeyboardType.Number,
                        enabled = !isReadOnly,
                        isError = controller.waitlistCapacityError != null,
                        supportingText = controller.waitlistCapacityError,
                        testTag = "tag_waitlist_limit"
                    )

                    if (controller.error != null) {
                        Text(
                            text = controller.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 7. Save / Publish Button at Bottom
                    val saveButtonLabel = when {
                        controller.saving -> "Saving..."
                        editing -> "Save changes"
                        else -> "Create Lottery"
                    }

                    Button(
                        onClick = {
                            controller.clearError()
                            controller.saveLottery()
                        },
                        enabled = !controller.saving && !isReadOnly,
                        shape = ButtonShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("button_save_lottery")
                    ) {
                        Text(saveButtonLabel)
                    }
                }
            }
        }
    }
}

/**
 * 46dp outlined text input field matching the team's [InputHeight] convention
 * from [HomeScreen.kt] using [BasicTextField] and [OutlinedTextFieldDefaults.DecorationBox].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lottery46dpInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    testTag: String = "",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(InputHeight)
                .testTag(testTag),
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text(placeholder) },
                isError = isError,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        shape = InputShape,
                    )
                },
            )
        }

        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}
