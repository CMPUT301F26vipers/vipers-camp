/*
 * AppNavigation.kt
 * Puts the bottom bar and the screens together, and decides which screen each route shows (T-02).
 * The app always opens on Home, and Back returns to the previous screen.
 * Known issues: the Welcome screen on first launch is added in T-01.
 */
package com.example.vipers.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vipers.ui.admin.AdminHomeScreen
import com.example.vipers.ui.campsites.CampsiteDetailsScreen
import com.example.vipers.ui.campsites.CampsiteFormScreen
import com.example.vipers.ui.campsites.MyCampsitesScreen
import com.example.vipers.ui.home.HomeScreen
import com.example.vipers.ui.notifications.NotificationsScreen
import com.example.vipers.ui.notifications.SentScreen
import com.example.vipers.ui.profile.PolicyScreen
import com.example.vipers.ui.profile.ProfileScreen
import com.example.vipers.ui.profile.SettingsScreen
import com.example.vipers.ui.reservations.MyReservationsScreen
import com.example.vipers.ui.reservations.OwnerReservationsScreen

// The bottom bar only shows on these screens, like in the mockups.
private val tabRoutes = listOf(
    Routes.HOME, Routes.RESERVATIONS, Routes.CAMPSITES, Routes.NOTIFICATIONS, Routes.PROFILE, Routes.ADMIN,
)

/**
 * The whole app's navigation. [isAdmin] shows the Admin tab, [userId] is the ID of current user
 * to be used when trying to load user's campsites, and [onLoadDemoData] is called
 * by the button on Settings.
 */
@Composable
fun AppNavigation(isAdmin: Boolean, onLoadDemoData: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // each screen's top bar already leaves room for the status bar
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in tabRoutes) {
                BottomBar(currentRoute, isAdmin, onTabClick = { route -> navController.openTab(route) })
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.RESERVATIONS) { MyReservationsScreen() }
            composable(Routes.CAMPSITES) {
                MyCampsitesScreen(
                    onAddCampsite = { navController.navigate(Routes.ADD_CAMPSITE) },
                    onOpenReservations = { navController.navigate(Routes.OWNER_RESERVATIONS) },
                    onOpenSent = { navController.navigate(Routes.OWNER_SENT) },
                    onOpenCampsite = { campsiteId -> // Updated name here!
                        val route = Routes.CAMPSITE_DETAILS.replace("{campsiteId}", campsiteId)
                        navController.navigate(route)
                    })
            }
            composable(Routes.ADD_CAMPSITE) {
                CampsiteFormScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.OWNER_RESERVATIONS) {
                OwnerReservationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.OWNER_SENT) {
                SentScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CAMPSITE_DETAILS,
                // You MUST declare the argument here for Compose to extract it from the route
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                CampsiteDetailsScreen(
                    campsiteId = campsiteId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPolicy = { navController.navigate(Routes.POLICY) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPolicy = { navController.navigate(Routes.POLICY) },
                    onLoadDemoData = onLoadDemoData,
                )
            }
            composable(Routes.POLICY) { PolicyScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ADMIN) { AdminHomeScreen() }
        }
    }
}

/**
 * Switches to a bottom bar tab. Going back from any tab returns to Home, and tapping the tab
 * you're already on doesn't stack another copy of it.
 */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
