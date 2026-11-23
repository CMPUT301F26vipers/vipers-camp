/*
 * ProfileDetailsScreen.kt
 * Displays user profile details and owned campsites for admins (US 03.05.01, mockup A05).
 * Known issues: removing ownership is added in US 03.07.01.
 */
package com.example.vipers.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.domain.model.Campsite
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.Shapes
import java.text.DateFormat
import java.util.Date

/** Displays detailed information about a user profile and their owned campsites. */
@Composable
fun ProfileDetailsScreen(
    profileId: String,
    onBack: () -> Unit,
    controller: AdminController = viewModel(factory = AdminController.Factory)
) {
    LaunchedEffect(profileId) {
        if (controller.profiles.isEmpty()) {
            controller.loadProfiles()
        }
        if (controller.campsites.isEmpty()) {
            controller.loadCampsites()
        }
    }

    val profile = controller.getProfile(profileId)
    val isOwner = controller.isCampsiteOwner(profileId)
    val ownedCampsites = controller.getCampsitesForOwner(profileId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(title = "Profile", onBack = onBack)

        when {
            controller.isProfilesLoading && profile == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            controller.profilesLoadFailed && profile == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = controller.profilesError ?: "Could not load profile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = {
                        controller.loadProfiles()
                        controller.loadCampsites()
                    }) {
                        Text("Retry")
                    }
                }
            }
            profile == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Profile not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar and Name Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(GreenFill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.name.take(1).uppercase().ifBlank { "?" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = profile.name.ifBlank { "Unnamed User" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val statusText = if (isOwner) "Owner" else "Entrant"
                            val statusBg = if (isOwner) GreenFill else MaterialTheme.colorScheme.secondaryContainer
                            val statusColor = if (isOwner) Primary else MaterialTheme.colorScheme.onSecondaryContainer
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                                modifier = Modifier
                                    .background(statusBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Contact Details Section
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = Shapes.medium,
                        border = BorderStroke(1.dp, Border)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileDetailField(
                                label = "Email",
                                value = profile.email.ifBlank { "Not provided" }
                            )
                            ProfileDetailField(
                                label = "Phone",
                                value = profile.phone.ifBlank { "Not provided" }
                            )
                            ProfileDetailField(
                                label = "Joined",
                                value = formatDate(profile.createdAt)
                            )
                        }
                    }

                    // Campsites Owned Section
                    Text(
                        text = "Campsites owned",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (ownedCampsites.isEmpty()) {
                        Text(
                            text = "No campsites owned.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (campsite in ownedCampsites) {
                                OwnedCampsiteCard(campsite = campsite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OwnedCampsiteCard(campsite: Campsite) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, Border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(GreenFill, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = campsite.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (campsite.address.isNotBlank()) {
                    Text(
                        text = campsite.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Not set"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}
