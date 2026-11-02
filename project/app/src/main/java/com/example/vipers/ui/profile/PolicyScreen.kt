/*
 * PolicyScreen.kt
 * The Policy screen (mockup E21), opened from Profile or Settings.
 * Known issues: placeholder. The policy text is added in US 01.11.01.
 */
package com.example.vipers.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The app policy and guidelines. */
@Composable
fun PolicyScreen(onBack: () -> Unit) {
    Column {
        TopBar(title = "Policy", onBack = onBack)
        Placeholder("How the lottery works, reserving and fair use are added in US 01.11.01.")
    }
}
