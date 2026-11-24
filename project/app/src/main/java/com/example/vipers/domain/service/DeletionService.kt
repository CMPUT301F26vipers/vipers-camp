/*
 * DeletionService.kt
 * Deletes a user's profile and cleans up everything that points to it (US 01.08.01): their
 * waiting-list entries are marked Removed, their reservations are cancelled (giving the nights
 * back), and the campsites they own are removed, with their photos, the reservations at them and
 * the owner's lotteries for them.
 * Known issues: these are separate writes, not one transaction, so each part is marked done
 * last (the campsite after its lotteries, the profile after everything): if a step fails the
 * user still has a profile and can try again, and the steps already done are skipped. Removing
 * a winner's entry doesn't draw a replacement winner (T-12 runs when a winner declines). The
 * profile ID is the device ID, so a new profile made on the same phone keeps its old Removed
 * entries and can't join those lotteries again.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository

/**
 * Deletes profiles and removes campsites, along with the data that points to them.
 * [notificationService] decides who gets told that their reservation was cancelled.
 */
class DeletionService(
    private val profileRepository: ProfileRepository,
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val reservationRepository: ReservationRepository,
    private val imageRepository: ImageRepository,
    private val notificationService: NotificationService,
) {

    /**
     * Deletes the profile of [userId] and its photo (US 01.08.01). First takes them off every
     * lottery they're still in, cancels their confirmed reservations without a notice, and
     * removes the campsites they own. Does nothing if they have no profile.
     *
     * @throws Exception from the repositories if a step fails; the profile is then kept.
     */
    suspend fun deleteProfile(userId: String, now: Long = System.currentTimeMillis()) {
        val profile = profileRepository.getProfile(userId) ?: return

        for (entry in lotteryRepository.getEntriesForUser(userId)) {
            if (entry.status in STILL_IN_LOTTERY) {
                lotteryRepository.removeEntry(entry.lotteryId, userId)
            }
        }
        for (reservation in reservationRepository.getReservationsForUser(userId)) {
            if (reservation.status == ReservationStatus.CONFIRMED) {
                // they're deleting their profile, so there's no one to tell
                reservationRepository.changeStatus(
                    reservation.id, ReservationStatus.CANCELLED, now = now, sendNotice = false,
                )
            }
        }
        for (campsite in campsiteRepository.getCampsitesByOwner(userId)) {
            removeCampsite(campsite, now)
        }

        profileRepository.deleteProfile(userId, profile.imageId)
    }

    /**
     * Removes [campsite] (US 01.08.01, 03.01.01): cancels the confirmed reservations at it, with a
     * notice to entrants who get notifications, removes its owner's lotteries once all of their
     * campsites are gone (taking everyone off them), deletes its photo, and marks it removed.
     * Lotteries made by another owner that also list it are kept.
     */
    suspend fun removeCampsite(campsite: Campsite, now: Long = System.currentTimeMillis()) {
        val active = reservationRepository.getReservationsForOwner(campsite.ownerId).filter {
            it.campsiteId == campsite.id && it.status == ReservationStatus.CONFIRMED
        }
        val wantsNotices = notificationService.optedIn(active.map { it.userId }).toSet()
        for (reservation in active) {
            reservationRepository.changeStatus(
                reservation.id,
                ReservationStatus.CANCELLED,
                now = now,
                sendNotice = reservation.userId in wantsNotices,
            )
        }

        for (lottery in lotteryRepository.getLotteriesForCampsite(campsite.id)) {
            if (lottery.ownerId == campsite.ownerId && hasNoOtherCampsite(lottery, campsite.id)) {
                removeLottery(lottery)
            }
        }
        campsite.imageId?.let { imageRepository.deleteImage(it) }
        // last, so a retry still finds this campsite and finishes the steps above
        campsiteRepository.removeCampsite(campsite.id)
    }

    // True if every campsite [lottery] covers, other than [removingId], is already removed.
    private suspend fun hasNoOtherCampsite(lottery: Lottery, removingId: String): Boolean =
        lottery.campsiteIds.all { id ->
            id == removingId || campsiteRepository.getCampsite(id)?.removed != false
        }

    // Takes everyone off [lottery], then marks it removed so it's never drawn or shown as open.
    private suspend fun removeLottery(lottery: Lottery) {
        for (entry in lotteryRepository.getEntries(lottery.id)) {
            if (entry.status in STILL_IN_LOTTERY) {
                lotteryRepository.removeEntry(lottery.id, entry.userId)
            }
        }
        lotteryRepository.removeLottery(lottery.id)
    }

    companion object {
        // Entries that still count in a lottery; the others are already finished
        // (RESERVED keeps its reservation, which is cancelled on its own).
        private val STILL_IN_LOTTERY = setOf(EntryStatus.WAITING, EntryStatus.WON, EntryStatus.LOST)
    }
}
