/*
 * CampsiteFormScreen.kt
 * The Add Campsite screen (mockup O02), opened from the "+" on My Campsites.
 * Known issues: placeholder. The form is added in US 02.01.01 and 02.01.02a.
 */
package com.example.vipers.ui.campsites

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The form for a new campsite. [onBack] goes back to My Campsites. */
@Composable
fun CampsiteFormScreen(onBack: () -> Unit) {
    Column {
        TopBar(title = "Add Campsite", onBack = onBack)
        Placeholder("The campsite form is added in US 02.01.01 and 02.01.02a.")
    }
}
