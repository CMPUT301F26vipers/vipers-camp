/*
 * HomeScreen.kt
 * The Home tab (mockups E02, E02b, E02c), where the app opens. The entrant searches campsites by
 * name here (US 01.01.01); CampsiteSearchController does the searching.
 * Known issues: Filters (01.01.02), the result count and loading indicator (01.01.03), the full
 * summary card (01.01.04), opening a campsite's details (01.01.05) and Scan QR (01.12.01) are
 * added by those stories.
 */
package com.example.vipers.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.InputShape
import com.example.vipers.ui.theme.Primary

/**
 * The Home screen. The mockup titles it "Campsites" because it's where you search for them.
 * Tests pass their own [controller] with a fake repository.
 */
@Composable
fun HomeScreen(
    controller: CampsiteSearchController = viewModel(factory = CampsiteSearchController.Factory),
) {
    // load each time Home opens, so newly added campsites show up
    LaunchedEffect(Unit) { controller.loadCampsites() }
    val results = controller.results

    Column {
        TopBar(title = "Campsites")
        OutlinedTextField(
            value = controller.searchText,
            onValueChange = controller::onSearchTextChange,
            placeholder = { Text("Search campsites") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            shape = InputShape,
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
                items(results, key = { it.id }) { campsite -> CampsiteResultRow(campsite) }
            }
        }
    }
}

/** One search result: the campsite's [Campsite.name] and [Campsite.address]. */
@Composable
private fun CampsiteResultRow(campsite: Campsite) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("campsite_result"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
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
        }
    }
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
