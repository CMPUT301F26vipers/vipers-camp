/*
 * HomeScreen.kt
 * The Home tab (mockups E02, E02b, E02c), where the app opens. The entrant searches campsites by
 * name here (US 01.01.01) and filters them by region, date range, and guests (US 01.01.02). Each
 * result shows a summary card (US 01.01.04), and tapping a card opens its Campsite Details
 * (US 01.01.05); CampsiteSearchController does the searching and filtering. The number of results
 * and a loading indicator are shown (US 01.01.03).
 * Known issues: Scan QR (01.12.01) is added by that story. The picture on each card is a
 * placeholder until campsites have promo images (02.01.02b), and preferred tags / "% match" on
 * the mockup are the WOW factor (W-01).
 */
package com.example.vipers.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.Region
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Divider
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputHeight
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/**
 * The Home screen. The mockup titles it "Campsites" because it's where you search for them.
 * Tapping a result calls [onOpenCampsite] with its ID. Tests pass their own [controller] with a
 * fake repository.
 */
@Composable
fun HomeScreen(
    controller: CampsiteSearchController = viewModel(factory = CampsiteSearchController.Factory),
    onOpenCampsite: (String) -> Unit = {},
) {
    var showFilterDialog by remember { mutableStateOf(false) }

    // load each time Home opens, so newly added campsites show up
    LaunchedEffect(Unit) { controller.loadCampsites() }
    val results = controller.results

    Column {
        TopBar(title = "Campsites")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                text = controller.searchText,
                onTextChange = controller::onSearchTextChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_field"),
            )
            OutlinedButton(
                onClick = { showFilterDialog = true },
                shape = InputShape,
                modifier = Modifier
                    .size(InputHeight)
                    .semantics {
                        contentDescription = "Filters"
                    }
                    .testTag("button_filters"),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(
                    1.dp,
                    if (controller.hasActiveFilters) Primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = "Filters",
                        tint = if (controller.hasActiveFilters) Primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    if (controller.hasActiveFilters) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .align(Alignment.TopEnd)
                                .background(Primary, CircleShape)
                        )
                    }
                }
            }
        }

        if (controller.hasActiveFilters && !showFilterDialog) {
            ActiveFiltersBar(
                selectedRegion = controller.selectedRegion,
                startDate = controller.filterStartDate,
                endDate = controller.filterEndDate,
                minGuests = controller.filterMinGuests,
                onClearFilters = controller::clearFilters,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            // only show loading indicator if there is nothing from an earlier load to show (US 01.01.03)
            controller.isLoading && results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("loading_indicator"),
                        color = Primary,
                    )
                }
            }
            // only show an error if there is nothing from an earlier load to show
            controller.loadFailed && results.isEmpty() ->
                EmptyResults("Couldn't load campsites", "Check your connection and open Home again.")
            else -> {
                Text(
                    text = controller.resultCountText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("text_result_count"),
                )
                if (results.isEmpty()) {
                    EmptyResults(
                        "No campsites found",
                        if (controller.hasActiveFilters) "Try adjusting or clearing your filters."
                        else "Check the spelling, or clear the search.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("campsite_list"),
                    ) {
                        items(results, key = { it.id }) { campsite ->
                            CampsiteResultRow(
                                campsite,
                                hasOpenLottery = controller.hasOpenLottery(campsite),
                                onClick = { onOpenCampsite(campsite.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        CampsiteFilterDialog(
            currentRegion = controller.selectedRegion,
            currentStartDate = controller.filterStartDate,
            currentEndDate = controller.filterEndDate,
            currentMinGuests = controller.filterMinGuests,
            onApply = { region, start, end, guests ->
                controller.applyFilters(region, start, end, guests)
                showFilterDialog = false
            },
            onClear = {
                controller.clearFilters()
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false },
        )
    }
}

/**
 * Summary bar shown below search when filters are active, displaying the active criteria
 * and a quick "Clear filters" action.
 */
@Composable
private fun ActiveFiltersBar(
    selectedRegion: Region?,
    startDate: String?,
    endDate: String?,
    minGuests: Int?,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val filterSummaries = mutableListOf<String>()
        if (selectedRegion != null) filterSummaries.add(selectedRegion.displayName)
        if (!startDate.isNullOrBlank() || !endDate.isNullOrBlank()) {
            filterSummaries.add("${startDate ?: "..."} to ${endDate ?: "..."}")
        }
        if (minGuests != null && minGuests > 0) {
            filterSummaries.add("$minGuests+ guests")
        }
        Text(
            text = "Active: " + filterSummaries.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClearFilters,
            modifier = Modifier.testTag("button_clear_filters_bar"),
        ) {
            Text("Clear filters", style = MaterialTheme.typography.labelSmall)
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
private fun CampsiteResultRow(campsite: Campsite, hasOpenLottery: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("campsite_result"),
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
