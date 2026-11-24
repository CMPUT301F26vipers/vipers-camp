/*
 * AppNavigation.kt
 * Puts the bottom bar and the screens together, and decides which screen each route shows (T-02).
 * The app always opens on Home, and Back returns to the previous screen. A scanned campsite QR
 * code opens that campsite's details, like tapping it on Home (US 01.12.01).
 * Known issues: none.
 */
package com.example.vipers.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.vipers.domain.model.NotificationTarget
import com.example.vipers.ui.admin.AdminHomeScreen
import com.example.vipers.ui.admin.BrowseCampsitesScreen
import com.example.vipers.ui.admin.BrowseImagesScreen
import com.example.vipers.ui.admin.BrowseProfilesScreen
import com.example.vipers.ui.admin.ImageDetailsScreen
import com.example.vipers.ui.admin.LogsScreen
import com.example.vipers.ui.admin.LogsTab
import com.example.vipers.ui.admin.MessageLogConversationScreen
import com.example.vipers.ui.admin.ProfileDetailsScreen
import com.example.vipers.ui.campsites.CampsiteDetailsScreen
import com.example.vipers.ui.campsites.CampsiteFormScreen
import com.example.vipers.ui.campsites.MyCampsitesScreen
import com.example.vipers.ui.campsites.OwnerCampsiteScreen
import com.example.vipers.ui.campsites.AvailabilityScreen
import com.example.vipers.ui.home.HomeScreen
import com.example.vipers.ui.home.QrScanResult
import com.example.vipers.ui.messages.MessagesScreen
import com.example.vipers.ui.notifications.NotificationsScreen
import com.example.vipers.ui.notifications.SentScreen
import com.example.vipers.ui.profile.PolicyScreen
import com.example.vipers.ui.profile.ProfileScreen
import com.example.vipers.ui.profile.SettingsScreen
import com.example.vipers.ui.reservations.MyReservationsScreen
import com.example.vipers.ui.reservations.OwnerReservationDetailsScreen
import com.example.vipers.ui.reservations.OwnerReservationsScreen
import com.example.vipers.ui.reservations.ReservationDetailsScreen
import com.example.vipers.ui.reservations.ReserveScreen
import com.example.vipers.ui.lottery.LotteryFormScreen
import com.example.vipers.ui.lottery.LotteryDetailsScreen
import com.example.vipers.ui.lottery.WaitingListScreen

// The bottom bar only shows on these screens, like in the mockups.
private val tabRoutes = listOf(
    Routes.HOME, Routes.RESERVATIONS, Routes.CAMPSITES, Routes.NOTIFICATIONS, Routes.PROFILE, Routes.ADMIN,
)

/**
 * The whole app's navigation. [isAdmin] shows the Admin tab, [onLoadDemoData] is called
 * by the button on Settings, and [onScanQr] opens the QR scanner from Home. When [pendingTarget]
 * is set by a tapped phone notification (T-14), it opens that screen on top of the Notifications
 * tab, as if the item had been tapped in the inbox, then calls [onPendingTargetOpened].
 */
@Composable
fun AppNavigation(
    isAdmin: Boolean,
    onLoadDemoData: () -> Unit,
    onScanQr: suspend () -> QrScanResult = { QrScanResult.Failed },
    pendingTarget: NotificationTarget? = null,
    onPendingTargetOpened: () -> Unit = {},
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
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenCampsite = { campsiteId ->
                        val route = Routes.CAMPSITE_DETAILS.replace("{campsiteId}", campsiteId)
                        navController.navigate(route)
                    },
                    onScanQr = onScanQr,
                )
            }
            composable(
                route = Routes.CAMPSITE_DETAILS,
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                CampsiteDetailsScreen(
                    campsiteId = campsiteId,
                    onBack = { navController.popBackStack() },
                    onJoinClick = { lotteryId ->
                        val route = Routes.LOTTERY_DETAILS.replace("{lotteryId}", lotteryId)
                        navController.navigate(route)
                    },
                    onReserve = { lotteryId ->
                        val route = Routes.RESERVE
                            .replace("{lotteryId}", lotteryId)
                            .replace("{campsiteId}", campsiteId)
                        navController.navigate(route)
                    },
                )
            }
            composable(
                route = Routes.RESERVE,
                arguments = listOf(
                    navArgument("lotteryId") { type = NavType.StringType },
                    // optional: the win notification only knows the lottery (T-13)
                    navArgument("campsiteId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                ReserveScreen(
                    lotteryId = backStackEntry.arguments?.getString("lotteryId") ?: "",
                    campsiteId = backStackEntry.arguments?.getString("campsiteId"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RESERVATIONS) {
                MyReservationsScreen(
                    // the entrant's reservation details (E15, US 01.06.01)
                    onOpenReservation = { reservationId ->
                        val route = Routes.RESERVATION_DETAILS.replace(
                            "{reservationId}",
                            reservationId,
                        )
                        navController.navigate(route)
                    },
                )
            }
            composable(
                route = Routes.RESERVATION_DETAILS,
                arguments = listOf(navArgument("reservationId") { type = NavType.StringType })
            ) { backStackEntry ->
                val reservationId = backStackEntry.arguments?.getString("reservationId") ?: ""
                ReservationDetailsScreen(
                    reservationId = reservationId,
                    onBack = { navController.popBackStack() },
                    onMessage = { id ->
                        navController.navigate(Routes.MESSAGES.replace("{reservationId}", id))
                    },
                )
            }
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
                OwnerReservationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReservation = { reservationId ->
                        val route = Routes.OWNER_RESERVATION_DETAILS.replace("{reservationId}", reservationId)
                        navController.navigate(route)
                    },
                )
            }
            composable(
                route = Routes.OWNER_RESERVATION_DETAILS,
                arguments = listOf(navArgument("reservationId") { type = NavType.StringType })
            ) { backStackEntry ->
                OwnerReservationDetailsScreen(
                    reservationId = backStackEntry.arguments?.getString("reservationId") ?: "",
                    onBack = { navController.popBackStack() },
                    onMessage = { reservationId ->
                        val route = Routes.MESSAGES.replace("{reservationId}", reservationId)
                        navController.navigate(route)
                    },
                )
            }
            composable(
                route = Routes.MESSAGES,
                arguments = listOf(navArgument("reservationId") { type = NavType.StringType })
            ) { backStackEntry ->
                MessagesScreen(
                    reservationId = backStackEntry.arguments?.getString("reservationId") ?: "",
                    onBack = { navController.popBackStack() },
                )
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
            composable(
                route = Routes.EDIT_CAMPSITE,
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                CampsiteFormScreen(
                    campsiteId = campsiteId,
                    onBack = { navController.popBackStack() },
                    onPublished = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.AVAILABILITY,
                arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val campsiteId = backStackEntry.arguments?.getString("campsiteId") ?: ""
                AvailabilityScreen(campsiteId = campsiteId, onBack = { navController.popBackStack() })
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
                    onBack = { navController.popBackStack() },
                    onViewWaitingList = {
                        navController.navigate(Routes.WAITING_LIST.replace("{lotteryId}", lotteryId))
                    },
                    onEditLottery = {
                        navController.navigate(Routes.EDIT_LOTTERY.replace("{lotteryId}", lotteryId))
                    },
                )
            }
            composable(
                route = Routes.EDIT_LOTTERY,
                arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lotteryId = backStackEntry.arguments?.getString("lotteryId") ?: ""
                LotteryFormScreen(
                    lotteryId = lotteryId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.WAITING_LIST,
                arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lotteryId = backStackEntry.arguments?.getString("lotteryId") ?: ""
                WaitingListScreen(lotteryId = lotteryId, onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onOpenReserve = { lotteryId ->
                        navController.openNotificationTarget(NotificationTarget.OpenReserve(lotteryId))
                    },
                    onOpenCampsite = { campsiteId ->
                        navController.openNotificationTarget(NotificationTarget.OpenCampsite(campsiteId))
                    },
                    onOpenReservation = { reservationId ->
                        navController.openNotificationTarget(NotificationTarget.OpenReservation(reservationId))
                    },
                    onOpenThread = { reservationId ->
                        navController.openNotificationTarget(NotificationTarget.OpenThread(reservationId))
                    },
                )
            }
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
                    BrowseCampsitesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCampsite = { campsiteId ->
                            val route = Routes.OWNER_CAMPSITE.replace("{campsiteId}", campsiteId)
                            navController.navigate(route)
                        }
                    )
                }

                composable(Routes.ADMIN_PROFILES) {
                    BrowseProfilesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { profileId ->
                            val route = Routes.ADMIN_PROFILE_DETAILS.replace("{profileId}", profileId)
                            navController.navigate(route)
                        }
                    )
                }

                composable(
                    route = Routes.ADMIN_PROFILE_DETAILS,
                    arguments = listOf(navArgument("profileId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
                    ProfileDetailsScreen(
                        profileId = profileId,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_IMAGES) {
                    BrowseImagesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenImage = { imageId ->
                            val route = Routes.ADMIN_IMAGE_DETAILS.replace("{imageId}", imageId)
                            navController.navigate(route)
                        }
                    )
                }

                composable(
                    route = Routes.ADMIN_IMAGE_DETAILS,
                    arguments = listOf(navArgument("imageId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val imageId = backStackEntry.arguments?.getString("imageId") ?: ""
                    ImageDetailsScreen(
                        imageId = imageId,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ADMIN_NOTIFICATION_LOG) {
                    LogsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { reservationId ->
                            val route = Routes.ADMIN_MESSAGE_LOG_CONVERSATION.replace("{reservationId}", reservationId)
                            navController.navigate(route)
                        },
                        initialTab = LogsTab.NOTIFICATION_LOG,
                    )
                }

                composable(Routes.ADMIN_MESSAGE_LOG) {
                    LogsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { reservationId ->
                            val route = Routes.ADMIN_MESSAGE_LOG_CONVERSATION.replace("{reservationId}", reservationId)
                            navController.navigate(route)
                        },
                        initialTab = LogsTab.MESSAGE_LOG,
                    )
                }

                composable(
                    route = Routes.ADMIN_MESSAGE_LOG_CONVERSATION,
                    arguments = listOf(navArgument("reservationId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val reservationId = backStackEntry.arguments?.getString("reservationId") ?: ""
                    MessageLogConversationScreen(
                        reservationId = reservationId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

        }
    }

    val latestOnPendingTargetOpened by rememberUpdatedState(onPendingTargetOpened)
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        navController.openTab(Routes.NOTIFICATIONS)
        navController.openNotificationTarget(target)
        latestOnPendingTargetOpened()
    }
}

/**
 * Opens the screen a notification's [target] names (T-13): Reserve for a win, the entrant's
 * campsite details, the entrant's reservation details, or the reservation's conversation. Both a
 * tap in the inbox and a tap on a phone notification (T-14) use this, so they open the same screen.
 */
fun NavHostController.openNotificationTarget(target: NotificationTarget) {
    val route = when (target) {
        // a win opens Reserve (01.04.01); without a campsiteId it uses the lottery's first
        is NotificationTarget.OpenReserve -> Routes.RESERVE
            .replace("{lotteryId}", target.lotteryId)
            .replace("?campsiteId={campsiteId}", "")
        // the entrant's campsite details (E05), never OWNER_CAMPSITE
        is NotificationTarget.OpenCampsite ->
            Routes.CAMPSITE_DETAILS.replace("{campsiteId}", target.campsiteId)
        // the entrant's reservation details (US 01.05.01)
        is NotificationTarget.OpenReservation ->
            Routes.RESERVATION_DETAILS.replace("{reservationId}", target.reservationId)
        // the reservation's conversation (US 02.12.01)
        is NotificationTarget.OpenThread ->
            Routes.MESSAGES.replace("{reservationId}", target.reservationId)
    }
    navigate(route)
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

