/*
 * MyCampsitesScreen.kt
 * The Campsites tab (mockup O01). Any user can add a campsite here; there is no owner account.
 * Known issues: placeholder. The list of your campsites is added in US 02.01.01.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vipers.R
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The user's own campsites. The "+" in the top bar calls [onAddCampsite]. */
@Composable
fun MyCampsitesScreen(onAddCampsite: () -> Unit, ownerID: String, viewModel: MyCampsitesViewModel = viewModel(factory = MyCampsitesViewModel.Factory)) {
    Column {
        TopBar(
            title = "My Campsites",
            actions = {
                IconButton(onClick = onAddCampsite) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Add campsite")
                }
            },
        )

        val campsites by viewModel.campsites.collectAsState()

        LaunchedEffect(ownerID) {
            viewModel.loadCampsites(ownerID)
        }

        if (campsites.isEmpty()){
            Placeholder("You haven't listed any campsites yet.")
        }
        else{
            LazyColumn() {
                items(campsites){ campsites ->
                    Text(text = campsites.name)
                }
            }
        }

    }
}
