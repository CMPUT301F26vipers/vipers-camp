/*
 * CampsiteDetailsScreen.kt
 * The screen to view details of a campsite.
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar

@Composable
fun CampsiteDetailsScreen(
    campsiteId: String,
    onBack: () -> Unit,
    onCreateLottery: () -> Unit,
    onLotteryClick: (String) -> Unit,
    controller: CampsiteDetailsController = viewModel(factory = CampsiteDetailsController.factory(campsiteId))
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopBar(title = "Campsite Details", onBack = onBack)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null -> Text(controller.error!!)
                controller.campsite != null -> {
                    Text(controller.campsite!!.name)

                    Button(onClick = onCreateLottery) {
                        Text("Create Lottery")
                    }

                    HorizontalDivider()
                    Text("Lotteries")

                    if (controller.lotteries.isEmpty()) {
                        Text("No lotteries created yet.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(controller.lotteries) { lottery ->
                                Column(
                                    modifier = Modifier
                                        .clickable { onLotteryClick(lottery.id) }
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val firstNight = lottery.nightsOffered.firstOrNull() ?: "Unknown dates"
                                    Text("Lottery starting $firstNight")
                                    if (lottery.registrationStart <= 0L || lottery.registrationEnd <= 0L ||
                                        lottery.bookingStart <= 0L || lottery.bookingEnd <= 0L) {
                                        Text("Status: Not set up yet")
                                    } else {
                                        Text("Status: ${lottery.status(System.currentTimeMillis()).name}")
                                    }
                                    Text("${lottery.nightsOffered.size} nights offered")
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> Text("Campsite not found.")
            }
        }
    }
}
