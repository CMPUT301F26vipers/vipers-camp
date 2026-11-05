/*
 * CreateLotteryScreen.kt
 *
 * Screen for creating a new lottery for a campsite.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.lotteries

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Form for creating a lottery for a campsite.
 * Added in US 02.04.01.
 *
 * @param campsiteId The ID of the campsite to create a lottery for.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun CreateLotteryScreen(campsiteId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "Create Lottery", onBack = onBack)
        Placeholder("Creating a lottery is added in US 02.04.01.")
    }
}
