/*
 * LotteryDetailsScreen.kt
 * The screen to view details of a lottery (mockup O09).
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
 * @param controller ViewModel managing lottery details and waitlist state.
 */
@Composable
fun LotteryDetailsScreen(
    lotteryId: String,
    onBack: () -> Unit,
    controller: LotteryDetailsController = viewModel(factory = LotteryDetailsController.factory(lotteryId))
) {
    var showWaitingList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopBar(
            title = "Lottery Details",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null && controller.lottery == null -> Text(controller.error!!)
                controller.lottery != null -> {
                    val currentLottery = controller.lottery!!
                    val now = System.currentTimeMillis()
                    val status = currentLottery.status(now)
                    val statusLabel = when (status) {
                        LotteryStatus.OPEN -> "Registration is open"
                        LotteryStatus.UPCOMING -> "Registration upcoming"
                        LotteryStatus.DRAWN -> "Drawn"
                        LotteryStatus.CLOSED -> "Closed"
                    }
                    val joinedCount = controller.waitingEntries.size
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

                        if (controller.error != null) {
                            item {
                                Text(
                                    text = controller.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        item {
                            if (controller.userEntry != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = "You have joined this waiting list",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { controller.joinLottery() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    enabled = currentLottery.canEntrantJoin()
                                ) {
                                    Text(
                                        when {
                                            currentLottery.status(System.currentTimeMillis()) == LotteryStatus.UPCOMING -> "Registration not started"
                                            currentLottery.status(System.currentTimeMillis()) == LotteryStatus.CLOSED -> "Registration closed"
                                            else -> "Join Waiting List"
                                        }
                                    )
                                }
                            }
                        }

                        item {
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
                                    onClick = { showWaitingList = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("View Waiting List")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWaitingList) {
        AlertDialog(
            onDismissRequest = { showWaitingList = false },
            title = { Text("Waiting List") },
            text = {
                if (controller.waitingEntries.isEmpty()) {
                    Text("There are no entrants on the waiting list.")
                } else {
                    LazyColumn {
                        items(controller.waitingEntries) { entry ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                val name = controller.userNames[entry.userId] ?: "Unknown User"
                                Text(name)
                                Text("Group size: ${entry.groupSize}")
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWaitingList = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Not set"
    return DateFormat.getDateTimeInstance().format(Date(timestamp))
}
