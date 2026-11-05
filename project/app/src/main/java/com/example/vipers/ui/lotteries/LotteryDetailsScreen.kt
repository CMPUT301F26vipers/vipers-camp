/*
 * LotteryDetailsScreen.kt
 *
 * Displays the details of a specific lottery.
 *
 * Known issues: UI is currently a placeholder.
 */
package com.example.vipers.ui.lotteries

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/**
 * Screen showing details for a specific lottery.
 * Added in US 02.04.01.
 *
 * @param lotteryId The ID of the lottery to display.
 * @param onBack Callback triggered when the back navigation icon is pressed.
 */
@Composable
fun LotteryDetailsScreen(lotteryId: String, onBack: () -> Unit) {
    Column {
        TopBar(title = "Lottery Details", onBack = onBack)
        Placeholder("Lottery details is added in US 02.04.01.")
    }
}
