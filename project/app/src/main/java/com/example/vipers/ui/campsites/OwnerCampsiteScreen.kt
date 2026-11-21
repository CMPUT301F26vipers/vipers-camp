/*
 * OwnerCampsiteScreen.kt
 * The owner's view of one of their campsites (mockup O04, US 02.01.04): all its saved details,
 * buttons to manage it, and its lotteries with a Create Lottery button (US 02.04.01).
 * OwnerCampsiteController loads the data; the whole promo photo shows at the top (US 02.01.02b).
 * Known issues: the map box shows the map location's coordinates (US 02.01.02c), and is hidden
 * when there is none, until T-04 draws a map there.
 * QR Code opens the campsite's QR code in a dialog (mockup O06, US 02.02.01), where Save stores
 * it as an image wherever the owner picks.
 */
package com.example.vipers.ui.campsites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.LotteryStatus
import com.example.vipers.ui.common.FullPhoto
import com.example.vipers.ui.common.SavedPhoto
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted
import java.text.DateFormat
import java.util.Date

/**
 * The My Campsite screen (mockup O04): the owner's campsite with all its saved details, the Edit,
 * QR Code, Availability and Reservations buttons, and its lotteries. QR Code opens the QR code
 * dialog (mockup O06, US 02.02.01), which can be opened again any time from here. Tests pass their own
 * [controller] with fake repositories. Edit only shows for the campsite's owner (US 02.01.03).
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
    var showQrCode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // the phone's own "save as" screen, so no storage permission is needed on any Android version
    val saveQrCode = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) controller.saveQrCode { context.contentResolver.openOutputStream(uri) }
    }

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
                    PhotoAndMap(campsite)
                    Text(
                        text = campsite.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // the empty space keeps QR Code in its place when Edit is hidden
                        if (controller.canEdit) {
                            OwnerButton("Edit", "button_edit", onEdit, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        OwnerButton(
                            "QR Code",
                            "button_qr_code",
                            {
                                controller.clearQrMessage()
                                showQrCode = true
                            },
                            Modifier.weight(1f),
                        )
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

                if (showQrCode) {
                    QrCodeDialog(
                        campsite = campsite,
                        message = controller.qrMessage,
                        onSave = { saveQrCode.launch(qrFileName(campsite.name)) },
                        onClose = { showQrCode = false },
                    )
                }
            }
        }
    }
}

/**
 * The Campsite QR Code dialog (mockup O06, US 02.02.01): the QR code for [campsite], drawn here
 * from its link each time so it never has to be stored. Save lets the owner keep it as an image
 * for posters; [message] says whether that worked.
 */
@Composable
private fun QrCodeDialog(
    campsite: Campsite,
    message: String?,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val link = campsite.qrLink()
    val qrImage = remember(link) { qrCodeBitmap(link, QR_SHOWN_SIZE).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Campsite QR Code") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = qrImage,
                    contentDescription = "QR code for ${campsite.name}",
                    // keeps the squares sharp instead of blurring their edges when scaled
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(220.dp).testTag("qr_code_image"),
                )
                Text(
                    campsite.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary,
                        modifier = Modifier.testTag("qr_code_message"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.testTag("button_qr_save"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onClose,
                border = BorderStroke(1.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                modifier = Modifier.testTag("button_qr_close"),
            ) {
                Text("Close")
            }
        },
    )
}

/**
 * The photo and map boxes at the top. A campsite with a promo photo shows the whole photo across
 * the screen, with the map box under it (US 02.01.02b); without one, the placeholder and the map
 * box sit side by side as in mockup O04. The map box shows [campsite]'s map coordinates
 * (US 02.01.02c) until T-04 puts a map there, and isn't shown at all when it has no map location.
 */
@Composable
private fun PhotoAndMap(campsite: Campsite) {
    if (campsite.imageId != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FullPhoto(
                campsite.imageId,
                contentDescription = "Campsite photo",
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("owner_photo"),
                placeholderHeight = 96.dp,
            )
            if (campsite.hasLocation()) MapBox(campsite, Modifier.fillMaxWidth().height(72.dp))
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SavedPhoto(
            null,
            contentDescription = "Campsite photo",
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(96.dp).testTag("owner_photo"),
        )
        if (campsite.hasLocation()) MapBox(campsite, Modifier.weight(1f).height(96.dp))
    }
}

// The map location's coordinates on green, until T-04 draws a map here.
@Composable
private fun MapBox(campsite: Campsite, modifier: Modifier) {
    Column(
        modifier
            .background(GreenFill, RoundedCornerShape(12.dp))
            .testTag("owner_map_location"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_campsite),
            contentDescription = null,
            tint = Primary,
        )
        Text(
            campsite.coordinatesText().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
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
        val registrationEndLabel = if (lottery.registrationEnd > 0L) {
            "Registration ends: ${formatDate(lottery.registrationEnd)}"
        } else {
            "Registration ends: Not set"
        }
        Text(
            text = registrationEndLabel,
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
 * The Details section: location, maximum guests and price ([Campsite.summaryText], unless
 * [showSummary] is false because the screen shows it higher up), then the description, tags,
 * region, and the rules and cancellation policy from [RulesAndPolicySection]. Empty optional
 * fields are left out. Also used by the entrant's Campsite Details screen (E05).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CampsiteDetails(campsite: Campsite, showSummary: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSummary) {
            Text(campsite.summaryText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        LabelledText("Region", campsite.region.displayName)
        RulesAndPolicySection(campsite)
    }
}

/** [timestamp] (milliseconds since 1970) as a date and time in the phone's format, or "Not set". */
internal fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Not set"
    return DateFormat.getDateTimeInstance().format(Date(timestamp))
}
