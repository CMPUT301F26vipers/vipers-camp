/*
 * CampsiteDetailsScreen.kt
 * The screen to view details of a campsite (mockup O08).
 * Known issues: none.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.ui.common.TopBar

/**
 * Displays details for a single campsite (mockup O08), including its active and past lotteries.
 * Allows owners to create a new lottery for this campsite via a bottom action button.
 *
 * @param campsiteId ID of the campsite being viewed.
 * @param onBack Callback triggered when pressing back.
 * @param onCreateLottery Callback triggered when clicking Create Lottery.
 * @param onLotteryClick Callback triggered when tapping a lottery item.
 * @param controller ViewModel managing campsite and lottery details.
 */
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
        TopBar(title = "My Campsite", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null -> Text(controller.error!!)
                controller.campsite != null -> {
                    Text(
                        text = controller.campsite!!.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()
                    Text("Lotteries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    if (controller.lotteries.isEmpty()) {
                        Text("No lotteries created yet.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(controller.lotteries) { lottery ->
                                val now = System.currentTimeMillis()
                                val status = lottery.status(now)
                                val statusLabel = when (status) {
                                    LotteryStatus.OPEN -> "Registration is open"
                                    LotteryStatus.UPCOMING -> "Registration upcoming"
                                    LotteryStatus.DRAWN -> "Drawn"
                                    LotteryStatus.CLOSED -> "Closed"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLotteryClick(lottery.id) }
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val displayNights = lottery.nightsDescription
                                        ?: lottery.nightsOffered.firstOrNull()
                                        ?: "Unknown dates"

                                    Text(
                                        text = "Lottery: $displayNights",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_chevron_right),
                                            contentDescription = "View details",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    // Primary Create Lottery Button positioned at bottom
                    Button(
                        onClick = onCreateLottery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Create Lottery")
                    }
                }
                else -> Text("Campsite not found.")
            }
        }
    }
}
