/*
 * NotificationService.kt
 * The app's notification logic, between the screens and NotificationRepository. It passes the
 * inbox and marking read through to the repository (T-13), builds and saves a draw's "You won"
 * (US 01.03.01) and "Not selected" (US 01.03.02) notices and the "New message" notice
 * (US 02.12.01), and skips users who turned notifications off in Settings (US 01.10.01).
 * Known issues: showing notifications on the phone is left to T-14.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.Subscription
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

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
        val names = lottery.campsiteIds.mapNotNull { campsiteRepository.getCampsite(it)?.name }
        val where = if (names.isEmpty()) "a campsite" else names.joinToString(", ")
        val won = drawNotices(
            lottery, winnerIds, now,
            type = NotificationType.WON,
            title = "You won the lottery!",
            message = "You won a spot at $where. " +
                "Reserve your nights before ${formatDate(lottery.bookingEnd)}.",
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
}

// The default formatDate: date and time in the phone's own style, e.g. "Apr 9, 2027, 11:59 PM",
// since booking ends at a set time, not just on a day.
private fun dateAndTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
