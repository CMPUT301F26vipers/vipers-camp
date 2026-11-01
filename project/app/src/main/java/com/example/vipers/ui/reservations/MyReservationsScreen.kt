/*
 * MyReservationsScreen.kt
 * The Reservations tab (mockup E14).
 * Known issues: placeholder. The Active and Past lists are added in US 01.06.01 and 01.06.02.
 */
package com.example.vipers.ui.reservations

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The user's reservations. */
@Composable
fun MyReservationsScreen() {
    Column {
        TopBar(title = "My Reservations")
        Placeholder("Your active and past reservations are added in US 01.06.01.")
    }
}
