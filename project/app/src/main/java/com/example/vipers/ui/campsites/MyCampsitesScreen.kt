/*
 * MyCampsitesScreen.kt
 * The Campsites tab (mockup O01). Any user can add a campsite here; there is no owner account.
 * Known issues: placeholder. The list of your campsites is added in US 02.01.01.
 */
package com.example.vipers.ui.campsites

import android.graphics.Outline
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.domain.model.Campsite
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.Background
import com.example.vipers.ui.theme.Border
import com.example.vipers.ui.theme.Surface
import com.example.vipers.ui.theme.Shapes
import com.example.vipers.ui.theme.TextMain
import com.example.vipers.ui.theme.TextSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults

/** The user's own campsites. The "+" in the top bar calls [onAddCampsite]. */
@Composable
fun MyCampsitesScreen(onAddCampsite: () -> Unit, onOpenReservations: () -> Unit, onOpenSent: () -> Unit, onCampsiteDesc: (String) -> Unit, ownerID: String, viewModel: MyCampsitesViewModel = viewModel(factory = MyCampsitesViewModel.Factory)) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = "My Campsites",
            actions = {
                IconButton(onClick = onAddCampsite) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Add campsite")
                }
            },
        )

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),){
            OutlinedButton(onClick = onOpenReservations, modifier = Modifier.weight(1f).height(50.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                Text("Reservations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(onClick = onOpenSent, modifier = Modifier.weight(1f).height(50.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                Text("Sent",style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        val campsites by viewModel.campsites.collectAsState()

        LaunchedEffect(ownerID) {
            viewModel.loadCampsites(ownerID)
        }

        if (campsites.isEmpty()){
            Placeholder("You haven't listed any campsites yet.")
        }
        else{
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(campsites){ campsite ->
                    CampsiteItem(campsite = campsite, onClick = { onCampsiteDesc(campsite.id)})
                }
            }
        }

    }
}

/**
 * Displays a summary card for a single campsite, matching the UI mockups.
 * Includes a placeholder image, the campsite name, its region, and a chevron icon.
 *
 * @param campsite The [Campsite] data object to display.
 * @param onClick Callback triggered when the user taps anywhere on the card.
 */
@Composable
fun CampsiteItem(campsite: Campsite, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = Shapes.medium,
        border = BorderStroke(1.dp, color = Border),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image Placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer, // Maps to GreenFill
                        shape = RoundedCornerShape(8.dp)
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Text Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = campsite.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, // Maps to TextMain
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${campsite.region.name.lowercase().replaceFirstChar { it.uppercase() }} · Lottery status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary, // Maps to TextSecondary
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Chevron Icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

