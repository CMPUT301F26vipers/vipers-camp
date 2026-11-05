/*
 * HomeScreen.kt
 * The Home tab (mockups E02, E02b, E02c), where the app opens. The entrant searches campsites by
 * name here (US 01.01.01), and each result shows a summary card (US 01.01.04);
 * CampsiteSearchController does the searching.
 * Known issues: Filters (01.01.02), the result count and loading indicator (01.01.03), opening a
 * campsite's details (01.01.05) and Scan QR (01.12.01) are added by those stories. The picture on
 * each card is a placeholder until campsites have promo images (02.01.02b), and the "% match"
 * on the mockup is the WOW factor (W-01).
 */
package com.example.vipers.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputHeight
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * The Home screen. The mockup titles it "Campsites" because it's where you search for them.
 * [onCampsiteClick] is invoked when an entrant taps a campsite summary card.
 * Tests pass their own [controller] with a fake repository.
 */
@Composable
fun HomeScreen(
    controller: CampsiteSearchController = viewModel(factory = CampsiteSearchController.Factory),
    onCampsiteClick: (String) -> Unit = {},
) {
    // load each time Home opens, so newly added campsites show up
    LaunchedEffect(Unit) { controller.loadCampsites() }
    val results = controller.results

    Column {
        TopBar(title = "Campsites")
        SearchField(
            text = controller.searchText,
            onTextChange = controller::onSearchTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("search_field"),
        )
        when {
            // only show an error if there is nothing from an earlier load to show
            controller.loadFailed && results.isEmpty() ->
                EmptyResults("Couldn't load campsites", "Check your connection and open Home again.")
            // wait for the first load before saying nothing was found
            controller.isLoading && results.isEmpty() -> {}
            results.isEmpty() ->
                EmptyResults("No campsites found", "Check the spelling, or clear the search.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results, key = { it.id }) { campsite ->
                    CampsiteResultRow(
                        campsite = campsite,
                        hasOpenLottery = controller.hasOpenLottery(campsite),
                        onClick = { onCampsiteClick(campsite.id) },
                    )
                }
            }
        }
    }
}

/**
 * The search bar, [InputHeight] tall like the mockup. OutlinedTextField can't be shorter than 56dp
 * without cutting off its text, so this is a BasicTextField with the same outlined look.
 */
@Composable
private fun SearchField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        modifier = modifier.height(InputHeight),
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = text,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            placeholder = { Text("Search campsites") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            contentPadding = PaddingValues(horizontal = 16.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    shape = InputShape,
                )
            },
        )
    }
}

/**
 * One search result, the summary card from mockup E02: the campsite's name, location, maximum
 * occupancy and rating, and a badge saying whether it [hasOpenLottery]. Long names and locations
 * are cut short with "…" so the card keeps its size.
 */
@Composable
private fun CampsiteResultRow(
    campsite: Campsite,
    hasOpenLottery: Boolean,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            }
            .semantics {
                onClick {
                    onClick()
                    true
                }
            }
            .testTag("campsite_result"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // placeholder until campsites have promo images
            Box(
                Modifier.size(48.dp).background(GreenFill, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_campsite), contentDescription = null, tint = Primary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    campsite.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    campsite.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${campsite.guestsText()} · ${campsite.ratingText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LotteryBadge(hasOpenLottery)
        }
    }
}

/** The small "Lottery open" or "No lottery" label on a result card. */
@Composable
private fun LotteryBadge(hasOpenLottery: Boolean) {
    Text(
        if (hasOpenLottery) "Lottery open" else "No lottery",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (hasOpenLottery) Primary else TextMuted,
        maxLines = 1,
        modifier = Modifier
            .background(if (hasOpenLottery) GreenFill else Divider, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The search icon, [title] and [message] shown when there are no results (mockup E02c). */
@Composable
private fun EmptyResults(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(GreenFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Primary)
        }
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
