
package com.example.vipers.ui.campsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.ui.common.TopBar

@Composable
fun CampsiteDetailsScreen(
    campsiteId: String,
    lotteryRepository: LotteryRepository,
    onBack: () -> Unit,
    onCreateLottery: () -> Unit,
    onLotteryClick: (String) -> Unit,
) {
    var lotteries by remember { mutableStateOf<List<Lottery>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(campsiteId) {
        try {
            lotteries = lotteryRepository.getLotteriesForCampsite(campsiteId)
        } catch (e: Exception) {
            error = "Unable to load lotteries."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title = "Campsite Details", onBack = onBack)
        Text("Campsite ID: $campsiteId")

        Button(onClick = onCreateLottery) {
            Text("Create Lottery")
        }

        HorizontalDivider()
        Text("Lotteries")

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!)
            lotteries.isEmpty() -> Text("No lotteries created yet.")
            else -> lotteries.forEach { lottery ->
                Column(
                    modifier = Modifier
                        .clickable { onLotteryClick(lottery.id) }
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Lottery ${lottery.id}")
                    Text("Status: ${lottery.status(System.currentTimeMillis()).name}")
                    Text("${lottery.nightsOffered.size} nights offered")
                }
                HorizontalDivider()
            }
        }
    }
}
