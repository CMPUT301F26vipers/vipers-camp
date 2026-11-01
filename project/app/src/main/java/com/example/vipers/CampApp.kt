/*
 * CampApp.kt
 * The Application class: starts once when the app launches and creates the AppContainer.
 * Known issues: none.
 */
package com.example.vipers

import android.app.Application
import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.data.firestore.FirestoreCampsiteRepository
import com.example.vipers.data.firestore.FirestoreLotteryRepository
import com.example.vipers.data.firestore.FirestoreNotificationRepository
import com.example.vipers.data.firestore.FirestoreProfileRepository
import com.example.vipers.data.firestore.FirestoreReservationRepository

/** The app's Application class. Registered in AndroidManifest.xml. */
class CampApp : Application() {

    /** Repositories and the current user's device ID. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            deviceId = DeviceIdentityManager.androidId(this),
            profileRepository = FirestoreProfileRepository(),
            campsiteRepository = FirestoreCampsiteRepository(),
            lotteryRepository = FirestoreLotteryRepository(),
            reservationRepository = FirestoreReservationRepository(),
            notificationRepository = FirestoreNotificationRepository(),
        )
    }
}
