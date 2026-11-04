/*
 * LotteryDetailsScreen.kt
 * The screen to view details of a lottery.
 * Known issues: none.
 */
package com.example.vipers.ui.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import java.text.DateFormat
import java.util.Date

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

        Column(modifier = Modifier.padding(16.dp)) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null -> Text("Error: ${controller.error}")
                controller.lottery == null -> Text("Lottery not found.")
                else -> {
                    val currentLottery = controller.lottery!!

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // Instruction: "For lotteries where registration and booking periods have not yet been configured, display: Not set up yet"
                            if (currentLottery.registrationStart <= 0L || currentLottery.registrationEnd <= 0L ||
                                currentLottery.bookingStart <= 0L || currentLottery.bookingEnd <= 0L) {
                                Text("Status: Not set up yet")
                            } else {
                                Text("Status: ${currentLottery.status(System.currentTimeMillis())}")
                            }
                            HorizontalDivider()
                        }

                        item {
                            Text("Nights Offered")
                            currentLottery.nightsOffered.forEach { night ->
                                Text("• $night")
                            }
                        }

                        item {
                            HorizontalDivider()
                            Text("Registration Period")
                            Text("Start: ${formatDate(currentLottery.registrationStart)}")
                            Text("End: ${formatDate(currentLottery.registrationEnd)}")
                        }

                        item {
                            HorizontalDivider()
                            Text("Booking Period")
                            Text("Start: ${formatDate(currentLottery.bookingStart)}")
                            Text("End: ${formatDate(currentLottery.bookingEnd)}")
                        }

                        item {
                            HorizontalDivider()
                            Text("Number of Winners: ${currentLottery.winnersCount}")
                        }

                        item {
                            Text("Waiting-list Count: ${controller.waitingEntries.size}")

                            Button(
                                onClick = { showWaitingList = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Waiting List")
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
