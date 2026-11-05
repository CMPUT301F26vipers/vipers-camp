/*
 * Routes.kt
 * The route name of every screen, used by the navigation graph and the bottom bar.
 * Known issues: none.
 */
package com.example.vipers.ui.navigation

/** Route names for every screen in the app. */
object Routes {
    const val HOME = "home"
    const val RESERVATIONS = "reservations"
    const val CAMPSITES = "campsites"
    const val ADD_CAMPSITE = "add_campsite"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val POLICY = "policy"
    const val ADMIN = "admin"

    const val OWNER_RESERVATIONS = "owner_reservations"

    const val OWNER_SENT = "owner_sent"

    const val CAMPSITE_DETAILS = "campsite_details/{campsiteId}"
    const val EDIT_CAMPSITE = "edit_campsite/{campsiteId}"
    const val CAMPSITE_QR = "campsite_qr/{campsiteId}"
    const val CAMPSITE_AVAILABILITY = "campsite_availability/{campsiteId}"
    const val LOTTERY_DETAILS = "lottery_details/{lotteryId}"
    const val CREATE_LOTTERY = "create_lottery/{campsiteId}"
}
