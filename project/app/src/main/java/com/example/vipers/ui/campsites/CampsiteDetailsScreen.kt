/*
 * CampsiteDetailsScreen.kt
 * The entrant's view of one campsite (mockups E05 and E05b, US 01.01.05), opened by tapping a
 * search result on Home: all its details and, if it has one, the lottery they can join with its
 * registration deadline. CampsiteDetailsController loads the data.
 * Known issues: the photo is a placeholder until promo images (02.01.02b), the map comes with
 * T-04, and Rate (01.13.01) and Join Waiting List (01.01.06) are added by those stories.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * The Campsite Details screen (mockups E05, E05b): the campsite's photo, name, location, guests,
 * price and rating, its active lottery or "No active lottery", then its description, tags,
 * region, rules and cancellation policy. Tests pass their own [controller] with fake repositories.
 *
 * @param campsiteId ID of the campsite being viewed.
 * @param onBack Called when pressing back; Home keeps its search text.
 * @param controller ViewModel that loads the campsite and its active lottery.
 */
@Composable
fun CampsiteDetailsScreen(
    campsiteId: String,
    onBack: () -> Unit,
    controller: CampsiteDetailsController =
        viewModel(factory = CampsiteDetailsController.factory(campsiteId)),
) {
    LaunchedEffect(Unit) { controller.loadData() }
    val campsite = controller.campsite

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Campsite", onBack = onBack)

        when {
            controller.loading && campsite == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> Text(controller.error!!, Modifier.padding(16.dp))
            campsite == null -> Text("Campsite not found.", Modifier.padding(16.dp))
            else -> Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // placeholder until campsites have promo images
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(GreenFill, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        tint = Primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        campsite.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(campsite.summaryText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ratingWithCount(campsite))
                }
                LotteryBox(controller.activeLottery)
                CampsiteDetails(campsite, showSummary = false)
            }
        }
    }
}

/** The rating as on mockup E05, e.g. "★ 4.5 (12)", or "No ratings yet". */
private fun ratingWithCount(campsite: Campsite): String {
    if (campsite.ratingCount == 0) return campsite.ratingText()
    return "${campsite.ratingText()} (${campsite.ratingCount})"
}

/**
 * The green box with the active [lottery]'s registration deadline and nights (E05), or a grey
 * "No active lottery" box when there is none (E05b).
 */
@Composable
private fun LotteryBox(lottery: Lottery?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (lottery != null) GreenFill else Divider, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("lottery_box"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (lottery == null) {
            Text("No active lottery", color = TextMuted, fontWeight = FontWeight.SemiBold)
        } else {
            Text(
                "Lottery open until ${formatDate(lottery.registrationEnd)}",
                color = Primary,
                fontWeight = FontWeight.SemiBold,
            )
            lottery.nightsDescription?.let { nights ->
                Text(
                    "Available: $nights",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary,
                )
            }
        }
    }
}
