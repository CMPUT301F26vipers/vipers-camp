/*
 * AppContainer.kt
 * Creates the repositories and the DeviceIdentityManager in one place, so the app uses
 * Firestore and tests can use the fake repositories instead.
 * Known issues: none.
 */
package com.example.vipers

import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository

/**
 * Holds everything the screens need. CampApp makes one with the Firestore repositories;
 * tests make one with the fake repositories.
 */
class AppContainer(
    val deviceId: String,
    val profileRepository: ProfileRepository,
    val campsiteRepository: CampsiteRepository,
    val lotteryRepository: LotteryRepository,
    val reservationRepository: ReservationRepository,
    val notificationRepository: NotificationRepository,
) {
    /** Who the current user is. */
    val deviceIdentityManager = DeviceIdentityManager(deviceId, profileRepository)
}
