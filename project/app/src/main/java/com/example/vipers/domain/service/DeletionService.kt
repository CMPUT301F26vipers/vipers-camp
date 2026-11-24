/*
 * DeletionService.kt
 * Deletes a user's profile and cleans up everything that points to it (US 01.08.01): their
 * waiting-list entries are marked Removed, their reservations are cancelled (giving the nights
 * back), and the campsites they own are removed, with the entries and reservations at them.
 * Known issues: these are separate writes, not one transaction, so the profile is deleted last:
 * if a step fails the user still has a profile and can try again, and the steps already done are
 * skipped. Removing a winner's entry doesn't draw a replacement winner (T-12 only runs when a
 * winner declines). The profile ID is the device ID, so a new profile made on the same phone
 * keeps its old Removed entries and can't join those lotteries again. removeCampsite doesn't
 * mark the campsite's lotteries removed; the admin story (US 03.01.01) can add that.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository

/** Deletes profiles and removes campsites, along with the data that points to them. */
class DeletionService(
    private val profileRepository: ProfileRepository,
    private val campsiteRepository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository,
    private val reservationRepository: ReservationRepository,
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
     * Removes [campsite] (US 01.08.01, 03.01.01): cancels the confirmed reservations at it, with
     * a notice to entrants who get notifications, takes everyone off the lotteries its owner made
     * for it, and marks it removed. Lotteries made by another owner that also list it are kept,
     * since their other campsites are still there.
     */
    suspend fun removeCampsite(campsite: Campsite, now: Long = System.currentTimeMillis()) {
        val reservations = reservationRepository.getReservationsForOwner(campsite.ownerId)
        for (reservation in reservations) {
            if (reservation.campsiteId != campsite.id) continue
            if (reservation.status != ReservationStatus.CONFIRMED) continue
            val wantsNotices =
                profileRepository.getProfile(reservation.userId)?.notificationsEnabled ?: false
            reservationRepository.changeStatus(
                reservation.id, ReservationStatus.CANCELLED, now = now, sendNotice = wantsNotices,
            )
        }
        for (lottery in lotteryRepository.getLotteriesForCampsite(campsite.id)) {
            if (lottery.ownerId != campsite.ownerId) continue
            for (entry in lotteryRepository.getEntries(lottery.id)) {
                if (entry.status in STILL_IN_LOTTERY) {
                    lotteryRepository.removeEntry(lottery.id, entry.userId)
                }
            }
        }
        campsiteRepository.removeCampsite(campsite.id)
    }

    companion object {
        // Entries that still count in a lottery; the others are already finished
        // (RESERVED keeps its reservation, which is cancelled on its own).
        private val STILL_IN_LOTTERY = setOf(EntryStatus.WAITING, EntryStatus.WON, EntryStatus.LOST)
    }
}
