/*
 * CampsiteDetailsScreen.kt
 *
 * Displays the details of a specific campsite and provides management options for the owner.
 *
 * Known issues: None.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.ui.common.TopBar
import androidx.compose.ui.res.painterResource
import com.example.vipers.R
import androidx.compose.material3.Icon

/**
 * Displays the detailed view for a specific campsite, allowing the owner
 * to view metrics and eventually edit the listing (US 02.01.04).
 *
 * @param campsiteId The unique identifier of the campsite to display.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun CampsiteDetailsScreen(campsiteId: String, onBack: () -> Unit, onEditClick: () -> Unit = {},
                          onQrCodeClick: () -> Unit = {},
                          onAvailabilityClick: () -> Unit = {},
                          onReservationsClick: () -> Unit = {},
                          onCreateLotteryClick: () -> Unit = {},
                          viewModel: CampsiteDetailsViewModel = viewModel(factory = CampsiteDetailsViewModel.provideFactory(campsiteId))) {

    val campsite = viewModel.campsite

    // Initializing the colors to make it easier later on
    val primaryGreen = MaterialTheme.colorScheme.primary
    val lightGreenBg = MaterialTheme.colorScheme.primaryContainer

    Scaffold(
        topBar = {
            TopBar(
                title = "My Campsite",
                onBack = onBack
            )
        },
        bottomBar = {
            // Create Lottery button pinned to the bottom
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onCreateLotteryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryGreen,
                        contentColor = Color.White // Forces text to white to match mockup
                    )
                ) {
                    Text("Create Lottery", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        if (campsite == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Image and Map Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .background(lightGreenBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Gallery", color = primaryGreen, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .background(lightGreenBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Map", color = primaryGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // 2. Name and Action Buttons Grid
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = campsite.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionOutlineButton(text = "Edit", onClick = onEditClick, modifier = Modifier.weight(1f))
                        ActionOutlineButton(text = "QR Code", onClick = onQrCodeClick, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionOutlineButton(text = "Availability", onClick = onAvailabilityClick, modifier = Modifier.weight(1f))
                        ActionOutlineButton(text = "Reservations", onClick = onReservationsClick, modifier = Modifier.weight(1f))
                    }
                }

                // 3. Lotteries Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lotteries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Dummy data for lotteries matching the mockup
                    LotteryCard(title = "May weekends", subtext = "36 joined", status = "Open", isOpen = true)
                    LotteryCard(title = "September", subtext = "3 reserved", status = "Closed", isOpen = false)
                }

                // 4. Details Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Text(
                        text = "${campsite.region} · Up to 6 guests · $50 / night",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Quiet forest sites with an outdoor cookhouse and a tennis court.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Tags using standard Row to avoid experimental APIs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagChip("Cookhouse")
                        TagChip("Tennis court")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text("Rules: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("quiet hours after 10 PM", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            Text("Cancellation: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("free up to 7 days before", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        // Changed to FontWeight.Bold for exact 1:1 mockup match on the 2x2 grid
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LotteryCard(title: String, subtext: String, status: String, isOpen: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(subtext, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .background(
                        if (isOpen) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall, // Adjusted for a tighter badge look
                    fontWeight = FontWeight.Bold, // Added to match the heavy text in the pill badges
                    color = if (isOpen) MaterialTheme.colorScheme.primary else Color.DarkGray
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold // Ensures tags have the heavy weight seen in the mockup
        )
    }
}