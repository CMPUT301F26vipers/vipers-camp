/*
 * WaitingListScreen.kt
 * The owner's Waiting List screen (mockup O11, US 02.09.01), opened from Lottery Details (O09).
 * Shows who joined (name, group size, join date), the total against the limit, and each
 * entrant's status. WaitingListController keeps it live.
 * Known issues: the Send Notification button (02.10.01) and the All / Won / Lost filter tabs on
 * the mockup are not added yet.
 */
package com.example.vipers.ui.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.LotteryEntry
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.Error
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists the entrants of [lotteryId]'s waiting list (O11), updating as they join, leave or get
 * their draw result.
 *
 * @param lotteryId The lottery whose waiting list is shown.
 * @param onBack Goes back to Lottery Details.
 * @param controller ViewModel that loads the lottery and listens to its entries.
 */
@Composable
fun WaitingListScreen(
    lotteryId: String,
    onBack: () -> Unit,
    controller: WaitingListController = viewModel(factory = WaitingListController.factory(lotteryId)),
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Waiting List", onBack = onBack)

        Column(Modifier.padding(16.dp)) {
            when {
                controller.loading -> CircularProgressIndicator()
                controller.error != null -> Text(controller.error!!, color = Error)
                else -> {
                    Text(
                        headerText(controller),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("waiting_list_count"),
                    )
                    if (controller.entries.isEmpty()) {
                        Text(
                            "No one has joined this waiting list yet.",
                            color = TextMuted,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            items(controller.entries, key = { it.id }) { entry ->
                                EntrantRow(entry, controller.names[entry.userId] ?: "")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "3 of 10 joined", plus " · drawn Apr 2" once the draw has run. */
private fun headerText(controller: WaitingListController): String {
    val drawnAt = controller.lottery?.drawnAt
    return if (drawnAt != null) "${controller.countLabel} · drawn ${shortDate(drawnAt)}" else controller.countLabel
}

/** One entrant: name, group size and join date, with a status chip on the right. */
@Composable
private fun EntrantRow(entry: LotteryEntry, name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenFill.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("entrant_${entry.userId}"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(entrantDetails(entry), color = TextMuted)
        }
        StatusChip(entry.status)
    }
}

/** A small rounded label with the entry's status: green for Won/Reserved, red for Declined (O11). */
@Composable
private fun StatusChip(status: EntryStatus) {
    val (textColor, fill) = when (status) {
        EntryStatus.WON, EntryStatus.RESERVED -> Primary to GreenFill
        EntryStatus.DECLINED -> Error to Error.copy(alpha = 0.12f)
        else -> TextMuted to Divider
    }
    Text(
        statusLabel(status),
        color = textColor,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(fill, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "Group of 4 · joined Mar 26" for [entry]. */
internal fun entrantDetails(entry: LotteryEntry): String =
    "Group of ${entry.groupSize} · joined ${shortDate(entry.joinedAt)}"

/** The status as shown to the owner, e.g. WON → "Won". */
internal fun statusLabel(status: EntryStatus): String =
    status.name.lowercase().replaceFirstChar { it.uppercase() }

/** A date like "Mar 26" in the phone's time zone. */
private fun shortDate(millis: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
