/*
 * EditCampsiteScreen.kt
 *
 * Screen for editing an existing campsite's details.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Screen where a campsite owner can edit their campsite listing.
 * Added in US 02.01.03.
 *
 * @param campsiteId The ID of the campsite to edit.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun EditCampsiteScreen(campsiteId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "Edit Campsite", onBack = onBack)
        Placeholder("Editing campsite details is added in US 02.01.03.")
    }
}
