/*
 * BrowseProfilesScreen.kt
 * Shows the list of user profiles for admins (US 03.05.01, mockup A04).
 * Known issues: profile removal is added in US 03.02.01.
 */
package com.example.vipers.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes

/** Displays user profiles for admin browsing. */
@Composable
fun BrowseProfilesScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    controller: AdminController = viewModel(factory = AdminController.Factory)
) {
    var searchText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (controller.profiles.isEmpty()) {
            controller.loadProfiles()
        }
    }

    val filteredProfiles = controller.profiles.filter {
        it.matchesSearch(searchText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(title = "All Profiles", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search profiles") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        when {
            controller.isLoading && controller.profiles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            controller.loadFailed && controller.profiles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = controller.error ?: "Could not load profiles.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = { controller.loadProfiles() }) {
                        Text("Retry")
                    }
                }
            }
            filteredProfiles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchText.isBlank()) {
                            "No profiles found."
                        } else {
                            "No matching profiles."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        BrowseProfileItem(
                            profile = profile,
                            isOwner = controller.isCampsiteOwner(profile.id),
                            onClick = { onOpenProfile(profile.id) }
                        )
                    }
                }
            }
        }
    }
}

/** Displays a single user profile in the admin list. */
@Composable
private fun BrowseProfileItem(
    profile: UserProfile,
    isOwner: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenFill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.name.ifBlank { "Unnamed User" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val labelText = if (isOwner) "Owner" else "Entrant"
                    val labelBg = if (isOwner) GreenFill else MaterialTheme.colorScheme.secondaryContainer
                    val labelColor = if (isOwner) Primary else MaterialTheme.colorScheme.onSecondaryContainer
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor,
                        modifier = Modifier
                            .background(labelBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val contactDetails = listOfNotNull(
                    profile.email.ifBlank { null },
                    profile.phone.ifBlank { null }
                ).joinToString(" · ")

                if (contactDetails.isNotBlank()) {
                    Text(
                        text = contactDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun UserProfile.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return name.contains(q, ignoreCase = true) ||
            email.contains(q, ignoreCase = true) ||
            phone.contains(q, ignoreCase = true)
}
