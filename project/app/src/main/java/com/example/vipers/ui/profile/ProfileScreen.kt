/*
 * ProfileScreen.kt
 * The Profile tab (mockup E18). Settings and Policy open from here.
 * Known issues: placeholder. Editing the profile is added in US 01.07.03.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The user's profile, with rows that open Settings and Policy. */
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, onOpenPolicy: () -> Unit) {
    Column {
        TopBar(title = "Profile")
        Placeholder("Editing your name, email and phone is added in US 01.07.03.")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuRow("Settings", onClick = onOpenSettings)
            MenuRow("Policy", onClick = onOpenPolicy)
        }
    }
}
