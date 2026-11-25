/*
 * AppContainer.kt
 * Creates the repositories and the DeviceIdentityManager in one place, so the app uses
 * Firestore and tests can use the fake repositories instead.
 * Known issues: none.
 */
package com.example.vipers

import com.example.vipers.data.device.DeviceIdentityManager
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.domain.service.DeletionService
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.ui.lottery.DrawAndNotifyWorker
import com.example.vipers.ui.lottery.LotteryDrawer

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
    val messageRepository: MessageRepository,
    val notificationRepository: NotificationRepository,
    val imageRepository: ImageRepository,
) {
    /** Who the current user is. */
    val deviceIdentityManager = DeviceIdentityManager(deviceId, profileRepository)

    /** Orchestrates lottery draws and recovery. */
    val lotteryDrawer = LotteryDrawer(
        lotteryRepository, notificationRepository, campsiteRepository, profileRepository,
    )

    /** Deletes profiles and removes campsites with everything that points to them (US 01.08.01). */
    val deletionService = DeletionService(
        profileRepository, campsiteRepository, lotteryRepository, reservationRepository,
        imageRepository,
        NotificationService(notificationRepository, campsiteRepository, profileRepository),
    )

    /** Starts the draws that are due whenever the app is opened (US 02.05.01b). */
    val drawAndNotifyWorker = DrawAndNotifyWorker(lotteryRepository, lotteryDrawer)
}
