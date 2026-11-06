/*
 * NotificationService.kt
 * The app's notification logic, between the screens and NotificationRepository. It passes the
 * inbox and marking read through to the repository (T-13), and builds and saves a draw's "You won"
 * (US 01.03.01) and "Not selected" (US 01.03.02) notices.
 * Known issues: LotteryDrawer still saves its own "Not selected" notices after calling
 * sendDrawResults, under the same IDs, so its text replaces these until it stops (US 01.03.02).
 * Inbox items are written even for users who turned notifications off; showing them on the
 * phone, and skipping those users, is left to the device-notification work.
 */
package com.example.vipers.domain.service

import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.Subscription
import java.text.DateFormat
import java.util.Date

/**
 * Reads a user's inbox and marks notifications read (T-13), and sends a draw's results
 * (US 01.03.01, 01.03.02), using [notificationRepository]. [campsiteRepository] looks up campsite
 * names, and [formatDate] turns a time into text for a message; tests pass a fixed one.
 */
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val campsiteRepository: CampsiteRepository,
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
     * Adds a draw's results to the inboxes of everyone in [result] (US 01.03.01, 01.03.02): a "You
     * won" notice to each winner, naming the lottery's campsites and when booking ends, which opens
     * Reserve for the lottery; and a "Not selected" notice to each loser, naming the campsites.
     * A user in both lists only gets the win. Each notice has the ID "draw_<lotteryId>_<userId>",
     * so sending the same draw again replaces it instead of adding a second one. All of them are
     * saved in one [NotificationRepository.addAll] call, dated when the draw ran, or [now]
     * (milliseconds since 1970) if the lottery has no draw time.
     */
    suspend fun sendDrawResults(result: DrawResult, now: Long = System.currentTimeMillis()) {
        val lottery = result.lottery
        val winnerIds = result.winnerIds.distinct()
        // a winner never gets a "Not selected" notice, even if a list has them in both
        val loserIds = result.loserIds.distinct() - winnerIds.toSet()
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
