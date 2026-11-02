/*
 * SettingsScreen.kt
 * The Settings screen (mockup E20), opened from Profile. Also has a button that loads the
 * demo data (SeedLoader) so teammates and the TA have something to look at.
 * Known issues: the notifications switch is added in US 01.10.01.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vipers.ui.common.MenuRow
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar
import com.example.vipers.ui.theme.ButtonHeight
import com.example.vipers.ui.theme.ButtonShape
import com.example.vipers.ui.theme.Primary

/** App settings. [onLoadDemoData] adds the demo campsites, lotteries and reservations. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPolicy: () -> Unit, onLoadDemoData: () -> Unit) {
    Column {
        TopBar(title = "Settings", onBack = onBack)
        Placeholder("The notifications switch is added in US 01.10.01.")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuRow("Policy and Guidelines", onClick = onOpenPolicy)
            OutlinedButton(
                onClick = onLoadDemoData,
                shape = ButtonShape,
                border = BorderStroke(1.dp, Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            ) {
                Text("Load demo data")
            }
        }
    }
}
