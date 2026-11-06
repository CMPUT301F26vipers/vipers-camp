/*
 * CampsiteDetailsScreen.kt
 * The entrant's view of one campsite (mockups E05 and E05b, US 01.01.05, US 01.01.06), opened by
 * tapping a search result on Home: all its details, the lottery they can join with its registration
 * deadline, and an action to join the waiting list. CampsiteDetailsController loads the data.
 * Known issues: the photo is a placeholder until promo images (02.01.02b), the map comes with
 * T-04, and Rate (01.13.01) is added by that story.
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMain
import com.example.vipers.ui.theme.TextMuted

/**
 * The Campsite Details screen (mockups E05, E05b): the campsite's photo, name, location, guests,
 * price and rating, its active lottery or "No active lottery", then its description, tags,
 * region, rules and cancellation policy. Tests pass their own [controller] with fake repositories.
 *
 * @param campsiteId ID of the campsite being viewed.
 * @param onBack Called when pressing back; Home keeps its search text.
 * @param onJoinClick Called when tapping the join action; navigates to the lottery details screen.
 * @param controller ViewModel that loads the campsite and its active lottery.
 */
@Composable
fun CampsiteDetailsScreen(
    campsiteId: String,
    onBack: () -> Unit,
    onJoinClick: (String) -> Unit = {},
    controller: CampsiteDetailsController =
        viewModel(factory = CampsiteDetailsController.factory(campsiteId)),
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
                LotteryBox(
                    lottery = controller.activeLottery,
                    userEntry = controller.userEntry,
                    spotsLeft = controller.spotsLeft,
                    isOwner = controller.isOwner,
                    isJoined = controller.isJoined,
                    isFull = controller.isFull,
                    onJoinClick = onJoinClick,
                )
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
 * The green box with the active [lottery]'s registration deadline, remaining waiting list spots,
 * nights, and waiting list action (E05), a grey "No active lottery" box when there is none (E05b),
 * or the entrant's draw results after a completed draw (E07b, E07c).
 */
@Composable
private fun LotteryBox(
    lottery: Lottery?,
    userEntry: LotteryEntry? = null,
    spotsLeft: Int? = null,
    isOwner: Boolean = false,
    isJoined: Boolean = false,
    isFull: Boolean = false,
    onJoinClick: (String) -> Unit = {},
) {
    val isLost = userEntry?.status == EntryStatus.LOST
    val isWon = userEntry?.status == EntryStatus.WON && lottery?.drawnAt != null
    val boxColor = if (lottery != null && !isLost) GreenFill else Divider

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(boxColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("lottery_box"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            lottery == null -> {
                Text("No active lottery", color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
            isWon -> {
                Text(
                    "You won! Reserve by ${formatDate(lottery.bookingEnd)}",
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
                Button(
                    onClick = { /* placeholder until T-03 / US 01.04.01 */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("reserve_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Reserve")
                }
            }
            isLost -> {
                Text(
                    "Not selected this time",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                )
                if (lottery.drawnAt != null) {
                    Text(
                        "The draw ran on ${formatDate(lottery.drawnAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                lottery.nightsDescription?.let { nights ->
                    Text(
                        "Available: $nights",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            else -> {
                val headline = if (spotsLeft != null) {
                    val spotsText = if (spotsLeft == 1) "1 spot left" else "$spotsLeft spots left"
                    "Lottery open until ${formatDate(lottery.registrationEnd)} · $spotsText"
                } else {
                    "Lottery open until ${formatDate(lottery.registrationEnd)}"
                }
                Text(
                    headline,
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
                if (!isOwner) {
                    when {
                        isJoined -> {
                            Button(
                                onClick = { onJoinClick(lottery.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("joined_lottery_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text("On Waiting List · View")
                            }
                        }
                        isFull -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("full_lottery_button"),
                            ) {
                                Text("Waiting List Full")
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onJoinClick(lottery.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("join_lottery_button"),
                            ) {
                                Text("Join Waiting List")
                            }
                        }
                    }
                }
            }
        }
    }
}
