/*
 * NotificationService.kt
 * The app's notification logic, between the screens and NotificationRepository. It passes the
 * inbox and marking read through to the repository (T-13), builds and saves a draw's "You won"
 * (US 01.03.01), "Not selected" (US 01.03.02), and replacement win (US 01.04.03, T-12) notices,
 * the "New message" notice (US 02.12.01) and the "Reservation rejected" notice with the owner's
 * reason (US 02.08.01), lists an owner's sent notifications (US 02.10.02), and skips users who
 * turned notifications off in Settings (US 01.10.01).
 * It also picks which new inbox items to show as phone notifications, at most once each, through
 * a DeviceNotifier (T-14).
 * Known issues: none.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.EntryStatus
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.ReservationStatus
import com.example.vipers.domain.model.SentNotification
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.Subscription
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * Reads a user's inbox and marks notifications read (T-13), and sends a draw's results
 * (US 01.03.01, 01.03.02), using [notificationRepository]. [campsiteRepository] looks up campsite
 * names, [profileRepository] tells who turned notifications off (US 01.10.01), and [formatDate]
 * turns a time into text for a message; tests pass a fixed one.
 */
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryRepository: LotteryRepository,
    private val formatDate: (Long) -> String = ::dateAndTime,
) {

    /**
     * Sends [userId]'s inbox, newest first, to [listener] straight away and on every change, until
     * the returned [Subscription] is cancelled (T-13).
     */
    fun listenToInbox(userId: String, listener: InboxListener): Subscription =
        notificationRepository.listenToInbox(userId, listener)

    /** Marks the notification with this [id] as read (T-13). */
    suspend fun markRead(id: String) = notificationRepository.markRead(id)

    /**
     * The users in [userIds] who still want notifications, in the same order and without
     * duplicates (US 01.10.01). A user is dropped only when their profile says notifications are
     * off. A user with no profile, or whose profile can't be read, is kept, so one failed read
     * doesn't stop everyone else's notifications. Every place that sends a notification to a user
     * calls this first, so the Settings switch works everywhere.
     */
    suspend fun optedIn(userIds: List<String>): List<String> =
        userIds.distinct().filter { userId -> wantsNotifications(userId) }

    // False only when the user's saved profile has notifications turned off.
    private suspend fun wantsNotifications(userId: String): Boolean =
        try {
            profileRepository.getProfile(userId)?.notificationsEnabled ?: true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }

    /**
     * Adds a draw's results to the inboxes of everyone in [result] (US 01.03.01, 01.03.02): a "You
     * won" notice to each winner, naming the lottery's campsites and when booking ends, which opens
     * Reserve for the lottery; and a "Not selected" notice to each loser, naming the campsites.
     * A user in both lists only gets the win, and users who turned notifications off get neither
     * ([optedIn], US 01.10.01). Each notice has the ID "draw_<lotteryId>_<userId>",
     * so sending the same draw again replaces it instead of adding a second one. All of them are
     * saved in one [NotificationRepository.addAll] call, dated when the draw ran, or [now]
     * (milliseconds since 1970) if the lottery has no draw time.
     */
    suspend fun sendDrawResults(result: DrawResult, now: Long = System.currentTimeMillis()) {
        val lottery = result.lottery
        val allWinners = result.winnerIds.distinct()
        // a winner never gets a "Not selected" notice, even if a list has them in both
        val allLosers = result.loserIds.distinct() - allWinners.toSet()
        val winnerIds = optedIn(allWinners)
        val loserIds = optedIn(allLosers)
        if (winnerIds.isEmpty() && loserIds.isEmpty()) return
        // Notification has no campsite name field, so the names go in the message
        val where = campsiteNamesFor(lottery)
        val won = drawNotices(
            lottery, winnerIds, now,
            type = NotificationType.WON,
            title = WON_TITLE,
            message = wonMessage(where, lottery.bookingEnd),
        )
        val lost = drawNotices(
            lottery, loserIds, now,
            type = NotificationType.NOT_SELECTED,
            title = "Not selected this time",
            message = "You weren't selected for $where this time.",
        )
        notificationRepository.addAll(won + lost)
    }

    /**
     * The entrants on [lotteryId]'s waiting list as the owner sees it on Waiting List (O11,
     * US 02.10.01): the user of every entry except [EntryStatus.REMOVED] ones, before or after
     * the draw, oldest entry first and without duplicates. Opt-outs are not removed here.
     */
    suspend fun waitingListRecipients(lotteryId: String): List<String> =
        lotteryRepository.getEntries(lotteryId)
            .filter { it.status != EntryStatus.REMOVED }
            .sortedBy { it.joinedAt }
            .map { it.userId }
            .distinct()

    /**
     * Sends the owner [senderId]'s [message] to everyone on [lotteryId]'s waiting list
     * (US 02.10.01): one [NotificationType.OWNER_BROADCAST] titled "Message from owner" per
     * entrant in [waitingListRecipients], skipping entrants who turned notifications off or no
     * longer have a profile (US 01.10.01); one whose profile can't be read is kept, like in
     * [optedIn]. The message is saved trimmed
     * ([Notification.ownerMessageText]). Every notification shares [batchId] and the time [now]
     * (milliseconds since 1970), has the ID "broadcast_<batchId>_<userId>", and points to the
     * lottery's campsite only when it covers one. All are saved in one
     * [NotificationRepository.addAll] call, so sending again with the same [batchId] finishes a
     * failed send instead of adding copies.
     *
     * Saves nothing and says why when the message is empty, the lottery is missing or removed,
     * [senderId] isn't its owner, or nobody is left to notify.
     *
     * @throws Exception if saving fails, e.g. offline; call again with the same [batchId] to finish.
     */
    suspend fun sendOwnerBroadcast(
        senderId: String,
        lotteryId: String,
        message: String,
        batchId: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): OwnerBroadcastResult {
        val text = Notification.ownerMessageText(message) ?: return OwnerBroadcastResult.EmptyMessage
        val lottery = lotteryRepository.getLottery(lotteryId)
        if (lottery == null || lottery.removed) return OwnerBroadcastResult.LotteryNotFound
        if (lottery.ownerId != senderId) return OwnerBroadcastResult.NotOwner
        val recipientIds = waitingListRecipients(lotteryId).filter { wantsOwnerMessages(it) }
        if (recipientIds.isEmpty()) return OwnerBroadcastResult.NobodyToNotify
        val notices = recipientIds.map { userId ->
            Notification(
                id = "broadcast_${batchId}_$userId",
                recipientId = userId,
                senderId = senderId,
                campsiteId = lottery.campsiteIds.singleOrNull(),
                lotteryId = lotteryId,
                type = NotificationType.OWNER_BROADCAST,
                title = "Message from owner",
                message = text,
                read = false,
                shownOnDevice = false,
                batchId = batchId,
                createdAt = now,
            )
        }
        notificationRepository.addAll(notices)
        return OwnerBroadcastResult.Sent(batchId, notices.size)
    }

    /**
     * The owner [ownerId]'s sent notifications for the Sent Notifications list (O13, US 02.10.02):
     * one [SentNotificationItem] per send of an owner broadcast, newest first
     * ([SentNotification.fromNotifications]), with the number of entrants it was saved for.
     * Each item names the campsite, or all of the lottery's campsites in order when the send
     * isn't about one, and the lottery's nights description, or its first offered night when it
     * has none. A campsite or lottery that is missing, removed or fails to load shows as
     * "Removed campsite" or "Removed lottery" instead of hiding the send. Each lottery and
     * campsite is loaded once, however many sends share it.
     *
     * @throws Exception if the owner's broadcasts can't be loaded, e.g. offline.
     */
    suspend fun sentNotifications(ownerId: String): List<SentNotificationItem> {
        val sends = SentNotification.fromNotifications(notificationRepository.getOwnerBroadcasts(ownerId))
        val lotteries = sends.mapNotNull { it.lotteryId }.distinct()
            .associateWith { id -> loadOrNull { lotteryRepository.getLottery(id) }?.takeUnless { it.removed } }
        val campsiteIdsBySend = sends.associate { send ->
            send.batchId to (send.campsiteId?.let { listOf(it) }
                ?: send.lotteryId?.let { lotteries[it]?.campsiteIds }.orEmpty())
        }
        val campsiteNames = campsiteIdsBySend.values.flatten().distinct()
            .associateWith { id -> loadOrNull { campsiteRepository.getCampsite(id) }?.takeUnless { it.removed }?.name }
        return sends.map { send ->
            val campsiteIds = campsiteIdsBySend.getValue(send.batchId)
            val lottery = send.lotteryId?.let { lotteries[it] }
            SentNotificationItem(
                sent = send,
                campsiteLabel = if (campsiteIds.isEmpty()) {
                    REMOVED_CAMPSITE
                } else {
                    campsiteIds.joinToString(", ") { campsiteNames[it] ?: REMOVED_CAMPSITE }
                },
                lotteryLabel = lottery?.let { nightsLabel(it) } ?: REMOVED_LOTTERY,
            )
        }
    }

    private fun nightsLabel(lottery: Lottery): String =
        lottery.nightsDescription?.takeIf { it.isNotBlank() }
            ?: lottery.nightsOffered.firstOrNull()
            ?: "Unknown dates"

    private suspend fun <T> loadOrNull(load: suspend () -> T?): T? =
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun wantsOwnerMessages(userId: String): Boolean =
        try {
            profileRepository.getProfile(userId)?.notificationsEnabled ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }

    /**
     * Prepares the replacement "You won" notice for [recipientId] in [lottery] at promotion time
     * [promotedAt] (US 01.04.03, T-12), or returns null if [recipientId] turned notifications off
     * ([optedIn], US 01.10.01). Uses the separate deterministic ID
     * `replacement_<lotteryId>_<recipientId>` and established win wording so the repository can
     * commit the notice atomically with the decline and promotion without overwriting the original
     * draw's `draw_<lotteryId>_<recipientId>` record.
     */
    suspend fun prepareReplacementWinNotice(
        lottery: Lottery,
        recipientId: String,
        promotedAt: Long = System.currentTimeMillis(),
    ): Notification? {
        if (optedIn(listOf(recipientId)).isEmpty()) return null
        val where = campsiteNamesFor(lottery)
        return Notification(
            id = replacementNoticeId(lottery.id, recipientId),
            recipientId = recipientId,
            senderId = "system",
            campsiteId = lottery.campsiteIds.singleOrNull(),
            lotteryId = lottery.id,
            type = NotificationType.WON,
            title = WON_TITLE,
            message = wonMessage(where, lottery.bookingEnd),
            createdAt = promotedAt,
        )
    }

    /**
     * Tells the other person in [reservation]'s conversation about the saved [message]
     * (US 02.12.01): the entrant when the owner wrote, and the owner when the entrant wrote
     * ([Reservation.otherSide]). It adds one NEW_MESSAGE notice ([Message.notice]) that opens the
     * conversation. Nothing is sent if the other person turned notifications off ([optedIn],
     * US 01.10.01), or if there is nobody else in the conversation.
     */
    suspend fun sendNewMessage(reservation: Reservation, message: Message) {
        val recipientId = reservation.otherSide(message.senderId) ?: return
        if (optedIn(listOf(recipientId)).isEmpty()) return
        notificationRepository.saveNotification(message.notice(reservation, recipientId))
    }

    /**
     * Tells the entrant that the owner rejected [reservation], and why (US 02.08.01). It saves the
     * RESERVATION_REJECTED notice from [Reservation.statusNotice], dated [now]: ID
     * `rejected_<reservationId>`, so sending again replaces it instead of adding a copy, from the
     * owner, with the reason in the message, and it opens the reservation. Nothing is sent if
     * [reservation] isn't REJECTED, has no reason ([Reservation.isValidRejectionReason]), or its
     * entrant turned notifications off ([optedIn], US 01.10.01).
     *
     * @return true if the notice was saved.
     */
    suspend fun sendReservationRejected(
        reservation: Reservation,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (reservation.status != ReservationStatus.REJECTED) return false
        if (!Reservation.isValidRejectionReason(reservation.rejectionReason)) return false
        if (optedIn(listOf(reservation.userId)).isEmpty()) return false
        notificationRepository.saveNotification(reservation.statusNotice(now))
        return true
    }

    /**
     * Shows [userId]'s new inbox items as phone notifications through [notifier] and returns how
     * many it showed (T-14). Does nothing if the user has no profile or it can't be read. If the
     * user turned notifications off (US 01.10.01), shows nothing and marks every unshown item
     * shown, so turning them back on doesn't bring back old items. If [DeviceNotifier.canPost] is
     * false, shows and marks nothing, so the items can still be shown once permission is granted.
     * Otherwise it goes through [NotificationRepository.listUnshownOnDevice], oldest first, and
     * shows each item only if [NotificationRepository.markShownOnDevice] returns true, so each
     * item is shown at most once even when two checks run together. An item that fails is skipped
     * and the rest are still handled; this method doesn't throw for them.
     */
    suspend fun showNewOnDevice(userId: String, notifier: DeviceNotifier): Int {
        val profile = try {
            profileRepository.getProfile(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return 0
        if (!profile.notificationsEnabled) {
            for (item in unshownFor(userId)) {
                attempt { notificationRepository.markShownOnDevice(item.id) }
            }
            return 0
        }
        if (!notifier.canPost()) return 0
        var shown = 0
        for (item in unshownFor(userId)) {
            val posted = attempt {
                if (notificationRepository.markShownOnDevice(item.id)) {
                    notifier.show(item)
                    true
                } else {
                    false
                }
            }
            if (posted == true) shown++
        }
        return shown
    }

    private suspend fun unshownFor(userId: String): List<Notification> =
        attempt { notificationRepository.listUnshownOnDevice(userId) } ?: emptyList()

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun campsiteNamesFor(lottery: Lottery): String {
        val names = lottery.campsiteIds.mapNotNull { campsiteRepository.getCampsite(it)?.name }
        return if (names.isEmpty()) "a campsite" else names.joinToString(", ")
    }

    private fun wonMessage(where: String, bookingEnd: Long): String =
        "You won a spot at $where. Reserve your nights before ${formatDate(bookingEnd)}."

    // One notice per user in userIds with what every draw notice shares: the ID
    // "draw_<lotteryId>_<userId>", sent by "system" about the lottery, pointing to its campsite only
    // when it covers one, and dated when the draw ran (or now). batchId stays null (#16).
    private fun drawNotices(
        lottery: Lottery,
        userIds: List<String>,
        now: Long,
        type: NotificationType,
        title: String,
        message: String,
    ): List<Notification> = userIds.map { userId ->
        Notification(
            id = "draw_${lottery.id}_$userId",
            recipientId = userId,
            senderId = "system",
            campsiteId = lottery.campsiteIds.singleOrNull(),
            lotteryId = lottery.id,
            type = type,
            title = title,
            message = message,
            createdAt = lottery.drawnAt ?: now,
        )
    }

    companion object {
        private const val WON_TITLE = "You won the lottery!"
        private const val REMOVED_CAMPSITE = "Removed campsite"
        private const val REMOVED_LOTTERY = "Removed lottery"

        /** Deterministic ID for a replacement win notice, distinct from the original draw notice. */
        fun replacementNoticeId(lotteryId: String, userId: String): String =
            "replacement_${lotteryId}_$userId"
    }
}

// The default formatDate: date and time in the phone's own style, e.g. "Apr 9, 2027, 11:59 PM",
// since booking ends at a set time, not just on a day.
private fun dateAndTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
