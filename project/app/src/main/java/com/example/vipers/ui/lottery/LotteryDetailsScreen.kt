/*
 * LotteryDetailsScreen.kt
 * The screen to view details of a lottery (mockup O09).
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.ui.common.TopBar
import java.text.DateFormat
import java.util.Date

/**
 * Displays full configuration and status details for a specific lottery (mockup O09),
 * including registration status chip, covered campsites, periods, winner count, waitlist
 * count vs. limit, and notice: "Winners are drawn when registration closes."
 *
 * @param lotteryId ID of the lottery being inspected.
 * @param onBack Callback triggered when navigating back.
 * @param onViewWaitingList Opens the owner's Waiting List screen (O11, US 02.09.01).
 * @param controller ViewModel managing lottery details and waitlist state.
 */
@Composable
fun LotteryDetailsScreen(
    lotteryId: String,
    onBack: () -> Unit,
    onViewWaitingList: () -> Unit = {},
    controller: LotteryDetailsController = viewModel(factory = LotteryDetailsController.factory(lotteryId))
) {
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var groupSizeText by remember { mutableStateOf("1") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopBar(
            title = "Lottery Details",
            onBack = onBack
        )

        Column(modifier = Modifier.padding(16.dp)) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null -> Text("Error: ${controller.error}")
                controller.lottery == null -> Text("Lottery not found.")
                else -> {
                    val currentLottery = controller.lottery!!
                    val now = System.currentTimeMillis()
                    val status = currentLottery.status(now)
                    val statusLabel = when (status) {
                        LotteryStatus.OPEN -> "Registration is open"
                        LotteryStatus.UPCOMING -> "Registration upcoming"
                        LotteryStatus.DRAWN -> "Drawn"
                        LotteryStatus.CLOSED -> "Closed"
                    }
                    val joinedCount = if (currentLottery.drawnAt != null) controller.allEntries.size else controller.waitingEntries.size
                    val capacityLabel = currentLottery.waitlistCapacity?.toString() ?: "No limit"

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "$statusLabel · $joinedCount joined",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider()
                        }

                        item {
                            Text("Campsites Covered: ${currentLottery.campsiteIds.size}")
                            if (!currentLottery.nightsDescription.isNullOrBlank()) {
                                Text("Description: ${currentLottery.nightsDescription}")
                            }
                            Text(
                                "Nights Offered (${currentLottery.nightsOffered.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            currentLottery.nightsOffered.forEach { night ->
                                Text("• $night")
                            }
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                "Registration Period",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Start: ${formatDate(currentLottery.registrationStart)}")
                            Text("End: ${formatDate(currentLottery.registrationEnd)}")
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                "Booking Period",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Start: ${formatDate(currentLottery.bookingStart)}")
                            Text("End: ${formatDate(currentLottery.bookingEnd)}")
                        }

                        item {
                            HorizontalDivider()
                            Text("Number of Winners: ${currentLottery.winnersCount}")
                            Text("Waiting List Entrants: $joinedCount / $capacityLabel")
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                text = "Winners are drawn when registration closes.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        item {
                            if (controller.isOwner) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { /* O10 Edit Lottery reserved for edit story */ },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Edit Lottery")
                                    }

                                    Button(
                                        onClick = onViewWaitingList,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("View Waiting List")
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (controller.joinSuccess) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Successfully joined the waiting list!",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    if (controller.actionError != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = controller.actionError!!,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    if (controller.leaveSuccess) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Successfully left the waiting list.",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    when {
                                        controller.isJoined -> {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "On Waiting List",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    val groupSize = controller.userEntry?.groupSize ?: 1
                                                    Text(
                                                        text = "Group size: $groupSize",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    if (controller.canLeave) {
                                                        OutlinedButton(
                                                            onClick = { showLeaveDialog = true },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .testTag("leave_lottery_button"),
                                                            colors = ButtonDefaults.outlinedButtonColors(
                                                                contentColor = MaterialTheme.colorScheme.error,
                                                            ),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                                        ) {
                                                            Text("Leave Waiting List")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        status == LotteryStatus.UPCOMING -> {
                                            Button(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Registration Upcoming")
                                            }
                                        }
                                        status == LotteryStatus.CLOSED || status == LotteryStatus.DRAWN -> {
                                            Button(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Registration Closed")
                                            }
                                        }
                                        controller.isFull -> {
                                            Button(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Waiting List Full")
                                            }
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    groupSizeText = "1"
                                                    showJoinDialog = true
                                                },
                                                enabled = !controller.isJoining,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (controller.isJoining) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Joining...")
                                                } else {
                                                    Text("Join Waiting List")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        val maxOccupancy = controller.maxOccupancy
        val isOccupancyAvailable = maxOccupancy != null && maxOccupancy > 0
        val groupSizeInt = groupSizeText.toIntOrNull()
        val isValid = isOccupancyAvailable && groupSizeInt != null && groupSizeInt in 1..maxOccupancy!!

        AlertDialog(
            onDismissRequest = {
                if (!controller.isJoining) {
                    showJoinDialog = false
                }
            },
            title = { Text("Join Waiting List") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val promptText = if (isOccupancyAvailable) {
                        "Enter the number of people in your group (1 to $maxOccupancy people):"
                    } else {
                        "Enter the number of people in your group:"
                    }
                    Text(promptText)
                    OutlinedTextField(
                        value = groupSizeText,
                        onValueChange = { groupSizeText = it },
                        label = { Text("Group size") },
                        placeholder = if (isOccupancyAvailable) {
                            { Text("1 to $maxOccupancy") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isOccupancyAvailable || (groupSizeText.isNotBlank() && !isValid),
                        supportingText = if (!isOccupancyAvailable) {
                            { Text("Campsite occupancy information is unavailable.") }
                        } else if (groupSizeText.isNotBlank() && !isValid) {
                            { Text("Group size must be between 1 and $maxOccupancy") }
                        } else {
                            { Text("1 to $maxOccupancy people") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isValid) {
                            showJoinDialog = false
                            controller.joinWaitingList(groupSizeInt!!)
                        }
                    },
                    enabled = isValid && !controller.isJoining
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinDialog = false },
                    enabled = !controller.isJoining
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave waiting list?") },
            text = {
                Text("You will not be in the draw for this campsite.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        controller.leaveWaitingList()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_leave_button")
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Not set"
    return DateFormat.getDateTimeInstance().format(Date(timestamp))
}
