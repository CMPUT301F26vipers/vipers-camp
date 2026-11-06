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
    const val RESERVATION_DETAILS = "reservation/{reservationId}"
    const val CAMPSITES = "campsites"
    const val ADD_CAMPSITE = "add_campsite"
    const val CREATE_LOTTERY = "create_lottery/{campsiteId}"
    const val LOTTERY_DETAILS = "lottery_details/{lotteryId}"
    const val WAITING_LIST = "waiting_list/{lotteryId}"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val POLICY = "policy"
    const val ADMIN = "admin"

    const val ADMIN_CAMPSITES = "admin_campsites"
    const val ADMIN_PROFILES = "admin_profiles"
    const val ADMIN_IMAGES = "admin_images"
    const val ADMIN_NOTIFICATION_LOG = "admin_notification_log"
    const val ADMIN_MESSAGE_LOG = "admin_message_log"


    const val OWNER_RESERVATIONS = "owner_reservations"
    const val OWNER_RESERVATION_DETAILS = "owner_reservation/{reservationId}"

    const val OWNER_SENT = "owner_sent"

    const val CAMPSITE_DETAILS = "campsite_details/{campsiteId}"
    // campsiteId is optional; without it the lottery's first campsite is used (01.04.01)
    const val RESERVE = "reserve/{lotteryId}?campsiteId={campsiteId}"
    const val OWNER_CAMPSITE = "owner_campsite/{campsiteId}"
    const val EDIT_CAMPSITE = "edit_campsite/{campsiteId}"
    const val AVAILABILITY = "availability/{campsiteId}"
}
