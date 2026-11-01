/*
 * HomeScreen.kt
 * The Home tab (mockup E02), where the app opens.
 * Known issues: placeholder. Campsite search is added in US 01.01.01 and Scan QR in US 01.12.01.
 */
package com.example.vipers.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The Home screen. The mockup titles it "Campsites" because it's where you search for them. */
@Composable
fun HomeScreen() {
    Column {
        TopBar(title = "Campsites")
        Placeholder("Campsite search is added in US 01.01.01.\nScan QR is added in US 01.12.01.")
    }
}
