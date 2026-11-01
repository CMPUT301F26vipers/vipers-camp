/*
 * BottomBar.kt
 * The bottom navigation bar on every main screen (T-02): Home, Reservations, Campsites,
 * Notifications and Profile, plus Admin for admins only.
 * Known issues: the mockup uses a tent icon for Campsites; the basic icon set has no tent,
 * so it uses a map pin.
 */
package com.example.vipers.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.vipers.R
import com.example.vipers.ui.theme.GreenFill
import com.example.vipers.ui.theme.Primary
import com.example.vipers.ui.theme.TextMuted

/** One tab in the bottom bar. */
private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", R.drawable.ic_home),
    Tab(Routes.RESERVATIONS, "Reservations", R.drawable.ic_calendar),
    Tab(Routes.CAMPSITES, "Campsites", R.drawable.ic_campsite),
    Tab(Routes.NOTIFICATIONS, "Notifications", R.drawable.ic_notifications),
    Tab(Routes.PROFILE, "Profile", R.drawable.ic_person),
)
private val adminTab = Tab(Routes.ADMIN, "Admin", R.drawable.ic_admin)

/**
 * The bottom bar. Highlights the tab for [currentRoute], shows the Admin tab only when
 * [isAdmin] is true, and calls [onTabClick] with the route of the tapped tab.
 */
@Composable
fun BottomBar(currentRoute: String?, isAdmin: Boolean, onTabClick: (String) -> Unit) {
    val shownTabs = if (isAdmin) tabs + adminTab else tabs

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        for (tab in shownTabs) {
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onTabClick(tab.route) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary,
                    selectedTextColor = Primary,
                    indicatorColor = GreenFill,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                ),
                // lets the intent test tap a tab even when the same word is also a screen title
                modifier = Modifier.testTag("tab_${tab.route}"),
            )
        }
    }
}
