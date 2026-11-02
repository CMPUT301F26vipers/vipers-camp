/*
 * MyCampsitesScreen.kt
 * The Campsites tab (mockup O01). Any user can add a campsite here; there is no owner account.
 * Known issues: placeholder. The list of your campsites is added in US 02.01.01.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.vipers.R
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The user's own campsites. The "+" in the top bar calls [onAddCampsite]. */
@Composable
fun MyCampsitesScreen(onAddCampsite: () -> Unit) {
    Column {
        TopBar(
            title = "My Campsites",
            actions = {
                IconButton(onClick = onAddCampsite) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Add campsite")
                }
            },
        )
        Placeholder("Your campsites are listed here in US 02.01.01.\nTap + to add one.")
    }
}
