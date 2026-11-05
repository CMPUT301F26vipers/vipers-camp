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
import com.example.vipers.ui.campsites.CampsiteFormScreen
import com.example.vipers.ui.campsites.MyCampsitesScreen
import com.example.vipers.ui.campsites.OwnerCampsiteScreen
import com.example.vipers.ui.home.HomeScreen
import com.example.vipers.ui.notifications.NotificationsScreen
import com.example.vipers.ui.notifications.SentScreen
import com.example.vipers.ui.profile.PolicyScreen
import com.example.vipers.ui.profile.ProfileScreen
import com.example.vipers.ui.profile.SettingsScreen
import com.example.vipers.ui.reservations.MyReservationsScreen
import com.example.vipers.ui.reservations.OwnerReservationsScreen
import com.example.vipers.ui.lottery.LotteryFormScreen
import com.example.vipers.ui.lottery.LotteryDetailsScreen

import androidx.compose.foundation.layout.Column
import com.example.vipers.ui.common.Placeholder
import com.example.vipers.ui.common.TopBar

// The bottom bar only shows on these screens, like in the mockups.
private val tabRoutes = listOf(
    Routes.HOME, Routes.RESERVATIONS, Routes.CAMPSITES, Routes.NOTIFICATIONS, Routes.PROFILE, Routes.ADMIN,
)

/**
 * The whole app's navigation. [isAdmin] shows the Admin tab, and [onLoadDemoData] is called
 * by the button on Settings.
 */
@Composable
fun AppNavigation(
    isAdmin: Boolean,
    onLoadDemoData: () -> Unit,
) {
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
                    onOpenCampsite = { campsiteId ->
                        val route = Routes.OWNER_CAMPSITE.replace("{campsiteId}", campsiteId)
                        navController.navigate(route)
                    })
            }
            composable(Routes.ADD_CAMPSITE) {
                CampsiteFormScreen(
                    onBack = { navController.popBackStack() },
                    onPublished = { navController.popBackStack() },
                )
            }
            composable(Routes.OWNER_RESERVATIONS) {
                OwnerReservationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.OWNER_SENT) {
                SentScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.OWNER_CAMPSITE,
                // You MUST declare the argument here for Compose to extract it from the route
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                OwnerCampsiteScreen(
                    campsiteId = campsiteId,
                    onBack = { navController.popBackStack() },
                    onEdit = {
                        navController.navigate(Routes.EDIT_CAMPSITE.replace("{campsiteId}", campsiteId))
                    },
                    onOpenAvailability = {
                        navController.navigate(Routes.AVAILABILITY.replace("{campsiteId}", campsiteId))
                    },
                    onOpenReservations = { navController.navigate(Routes.OWNER_RESERVATIONS) },
                    onCreateLottery = {
                        val route = Routes.CREATE_LOTTERY.replace("{campsiteId}", campsiteId)
                        navController.navigate(route)
                    },
                    onLotteryClick = { lotteryId ->
                        val route = Routes.LOTTERY_DETAILS.replace("{lotteryId}", lotteryId)
                        navController.navigate(route)
                    },
                )
            }
            // placeholders until 02.01.03 and T-10 build these screens
            composable(Routes.EDIT_CAMPSITE) {
                Column {
                    TopBar(title = "Edit Campsite", onBack = { navController.popBackStack() })
                    Placeholder("Editing a campsite is added in US 02.01.03.")
                }
            }
            composable(Routes.AVAILABILITY) {
                Column {
                    TopBar(title = "Availability", onBack = { navController.popBackStack() })
                    Placeholder("Setting available nights is added in T-10.")
                }
            }
            composable(
                route = Routes.CREATE_LOTTERY,
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                LotteryFormScreen(
                    campsiteId = campsiteId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.LOTTERY_DETAILS,
                arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lotteryId = backStackEntry.arguments?.getString("lotteryId") ?: ""
                LotteryDetailsScreen(
                    lotteryId = lotteryId,
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

            if (isAdmin) {
                composable(Routes.ADMIN) {
                    AdminHomeScreen(
                        onOpenCampsites = {
                            navController.navigate(Routes.ADMIN_CAMPSITES)
                        },
                        onOpenProfiles = {
                            navController.navigate(Routes.ADMIN_PROFILES)
                        },
                        onOpenImages = {
                            navController.navigate(Routes.ADMIN_IMAGES)
                        },
                        onOpenNotificationLog = {
                            navController.navigate(Routes.ADMIN_NOTIFICATION_LOG)
                        },
                        onOpenMessageLog = {
                            navController.navigate(Routes.ADMIN_MESSAGE_LOG)
                        }
                    )
                }

                composable(Routes.ADMIN_CAMPSITES) {
                    AdminPlaceholderScreen(
                        "All Campsites",
                        "Browse Campsites (03.04.01)",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_PROFILES) {
                    AdminPlaceholderScreen(
                        "All Profiles",
                        "Browse Profiles (03.05.01)",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_IMAGES) {
                    AdminPlaceholderScreen(
                        "All Images",
                        "Browse Images (03.06.01)",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_NOTIFICATION_LOG) {
                    AdminPlaceholderScreen(
                        "Logs",
                        "Notification Log (03.08.01)",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_MESSAGE_LOG) {
                    AdminPlaceholderScreen(
                        "Logs",
                        "Message Log (03.12.01)",
                        onBack = { navController.popBackStack() }
                    )
                }
            }

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


/** Temporary screen for admin features that are not implemented yet. */
@Composable
private fun AdminPlaceholderScreen(
    title: String,
    message: String,
    onBack: () -> Unit
) {
    Column {
        TopBar(title = title, onBack = onBack)
        Placeholder(message)
    }
}

