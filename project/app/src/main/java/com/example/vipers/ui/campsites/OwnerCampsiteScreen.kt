/*
 * OwnerCampsiteScreen.kt
 * The owner's view of one of their campsites (mockup O04, US 02.01.04): all its saved details,
 * buttons to manage it, and its lotteries with a Create Lottery button (US 02.04.01).
 * OwnerCampsiteController loads the data.
 * Known issues: the photo and map are placeholders until 02.01.02b and 02.01.02c, and the QR Code
 * button stays off until 02.02.01. Edit (02.01.03) and Availability (T-10) open placeholder
 * screens for now.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * The My Campsite screen (mockup O04): the owner's campsite with all its saved details, the Edit,
 * QR Code, Availability and Reservations buttons, and its lotteries. Tests pass their own
 * [controller] with fake repositories.
 *
 * @param campsiteId ID of the campsite being viewed.
 * @param onBack Called when pressing back.
 * @param onEdit Called when tapping Edit.
 * @param onOpenAvailability Called when tapping Availability.
 * @param onOpenReservations Called when tapping Reservations.
 * @param onCreateLottery Called when tapping Create Lottery.
 * @param onLotteryClick Called with the lottery's ID when tapping a lottery.
 * @param controller ViewModel that loads the campsite and its lotteries.
 */
@Composable
fun OwnerCampsiteScreen(
    campsiteId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenAvailability: () -> Unit,
    onOpenReservations: () -> Unit,
    onCreateLottery: () -> Unit,
    onLotteryClick: (String) -> Unit,
    controller: OwnerCampsiteController = viewModel(factory = OwnerCampsiteController.factory(campsiteId))
) {
    // load each time the screen comes back into view, so it shows the latest saved values
    // (e.g. after Edit or Create Lottery)
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
        TopBar(title = "My Campsite", onBack = onBack)

        when {
            // keep showing the campsite while it reloads
            controller.loading && campsite == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            controller.error != null -> Text(controller.error!!, Modifier.padding(16.dp))
            campsite == null -> Text("Campsite not found.", Modifier.padding(16.dp))
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhotoAndMap()
                    Text(
                        text = campsite.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnerButton("Edit", "button_edit", onEdit, Modifier.weight(1f))
                        // turned on by 02.02.01
                        OwnerButton("QR Code", "button_qr_code", {}, Modifier.weight(1f), enabled = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnerButton("Availability", "button_availability", onOpenAvailability, Modifier.weight(1f))
                        OwnerButton("Reservations", "button_reservations", onOpenReservations, Modifier.weight(1f))
                    }

                    SectionTitle("Lotteries")
                    if (controller.lotteries.isEmpty()) {
                        Text("No lotteries created yet.")
                    } else {
                        Column {
                            for (lottery in controller.lotteries) {
                                LotteryRow(
                                    lottery,
                                    entrySummary = controller.entrySummaries[lottery.id] ?: "",
                                    onClick = { onLotteryClick(lottery.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    SectionTitle("Details")
                    CampsiteDetails(campsite)
                }

                Button(
                    onClick = onCreateLottery,
                    shape = ButtonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(ButtonHeight)
                        .testTag("button_create_lottery"),
                ) {
                    Text("Create Lottery", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The photo and map boxes at the top; placeholders until 02.01.02b and 02.01.02c. */
@Composable
private fun PhotoAndMap() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (icon in listOf(R.drawable.ic_image, R.drawable.ic_campsite)) {
            Box(
                Modifier.weight(1f).height(96.dp).background(GreenFill, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = Primary)
            }
        }
    }
}

/** One of the outlined buttons under the campsite's name. */
@Composable
private fun OwnerButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Primary else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
        modifier = modifier.height(ButtonHeight).testTag(tag),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * One lottery: its nights, how many joined or reserved ([entrySummary]) and its status. Tapping it
 * opens the lottery's details.
 */
@Composable
private fun LotteryRow(lottery: Lottery, entrySummary: String, onClick: () -> Unit) {
    // the labels from mockups O04 and O01
    val status = lottery.status(System.currentTimeMillis())
    val statusLabel = when (status) {
        LotteryStatus.OPEN -> "Open"
        LotteryStatus.UPCOMING -> "Upcoming"
        LotteryStatus.DRAWN -> "Booking period"
        LotteryStatus.CLOSED -> "Closed"
    }
    val isOpen = status == LotteryStatus.OPEN
    val displayNights = lottery.nightsDescription
        ?: lottery.nightsOffered.firstOrNull()
        ?: "Unknown dates"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("lottery_row"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Lottery: $displayNights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = entrySummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // green while entrants can join, grey otherwise, like the badge on Home
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isOpen) Primary else TextMuted,
                modifier = Modifier
                    .background(if (isOpen) GreenFill else Divider, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The Details section: location, maximum guests and price, then the description, tags, rules
 * and cancellation policy. Empty optional fields are left out, and so is a price of 0, which
 * campsites have until the form asks for one (T-09).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampsiteDetails(campsite: Campsite) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val summary = mutableListOf(campsite.address, "Up to ${campsite.guestsText()}")
        if (campsite.pricePerNightCents > 0) summary.add(campsite.priceText())
        Text(summary.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (campsite.description.isNotBlank()) {
            Text(campsite.description)
        }
        if (campsite.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tag in campsite.tags) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary,
                        modifier = Modifier
                            .background(GreenFill, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (campsite.rules.isNotBlank()) {
            LabelledText("Rules", campsite.rules)
        }
        if (campsite.cancellationPolicy.isNotBlank()) {
            LabelledText("Cancellation", campsite.cancellationPolicy)
        }
    }
}

/** Text like "Rules: quiet hours after 10 PM", with the [label] in bold. */
@Composable
private fun LabelledText(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$label: ") }
            append(text)
        }
    )
}
