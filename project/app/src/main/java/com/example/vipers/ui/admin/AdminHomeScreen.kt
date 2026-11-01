/*
 * AdminHomeScreen.kt
 * The Admin tab (mockup A00). The tab only shows for profiles with isAdmin set (US 03.11.01).
 * Known issues: placeholder. Browsing and removing campsites, profiles and images, and the
 * logs, are added in the admin stories.
 */
package com.example.vipers.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

/** The admin home screen. */
@Composable
fun AdminHomeScreen() {
    Column {
        TopBar(title = "Admin")
        Placeholder("Browsing and removing campsites, profiles and images is added in the admin stories.")
    }
}
