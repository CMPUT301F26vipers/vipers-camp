
package com.example.vipers.ui.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.ui.common.TopBar
import java.text.DateFormat
import java.util.Date

@Composable
fun LotteryDetailsScreen(
    lotteryId: String,
    lotteryRepository: LotteryRepository,
    onBack: () -> Unit
) {
    var lottery by remember { mutableStateOf<Lottery?>(null) }
    var waitingEntries by remember { mutableStateOf<List<LotteryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showWaitingList by remember { mutableStateOf(false) }

    LaunchedEffect(lotteryId) {
        loading = true
        error = null

        try {
            lottery = lotteryRepository.getLottery(lotteryId)
            waitingEntries = lotteryRepository.getEntries(lotteryId)
                .filter { it.status == EntryStatus.WAITING }
        } catch (e: Exception) {
            error = e.message ?: "Unable to load lottery details"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopBar(
            title = "Lottery Details",
            onBack = onBack
        )

        when {
            loading -> {
                CircularProgressIndicator()
            }

            error != null -> {
                Text("Error: $error")
            }

            lottery == null -> {
                Text("Lottery not found.")
            }

            else -> {
                val currentLottery = lottery!!

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Status: ${currentLottery.status(System.currentTimeMillis())}")
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
                        Text("Waiting-list Count: ${waitingEntries.size}")

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

    if (showWaitingList) {
        AlertDialog(
            onDismissRequest = { showWaitingList = false },
            title = { Text("Waiting List") },
            text = {
                if (waitingEntries.isEmpty()) {
                    Text("There are no entrants on the waiting list.")
                } else {
                    LazyColumn {
                        items(waitingEntries) { entry ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("User ID: ${entry.userId}")
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
