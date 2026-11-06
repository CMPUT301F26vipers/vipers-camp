/*
 * NotificationService.kt
 * The app's notification logic, between the screens and NotificationRepository. It passes the
 * inbox and marking read through to the repository (T-13), and builds and saves the "You won"
 * notices after a draw (US 01.03.01).
 * Known issues: the "Not selected" notices are still sent by LotteryDrawer until US 01.03.02
 * moves them here. Inbox items are written even for users who turned notifications off; showing
 * them on the phone, and skipping those users, is left to the device-notification work.
 */
package com.example.vipers.domain.service

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
 * (US 01.03.01), using [notificationRepository]. [campsiteRepository] looks up campsite names, and
 * [formatDate] turns a time into text for a message; tests pass a fixed one.
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
     * Adds a "You won" notification to each winner's inbox in [result] (US 01.03.01). It names the
     * lottery's campsites and says when booking ends, and tapping it opens Reserve for the lottery.
     * Each winner's notice has the ID "draw_<lotteryId>_<userId>", so sending the same draw again
     * replaces it instead of adding a second one. The notices are dated when the draw ran, or
     * [now] (milliseconds since 1970) if the lottery has no draw time. The losers' notices are
     * added by US 01.03.02.
     */
    suspend fun sendDrawResults(result: DrawResult, now: Long = System.currentTimeMillis()) {
        val lottery = result.lottery
        val winnerIds = result.winnerIds.distinct()
        if (winnerIds.isEmpty()) return
        // Notification has no campsite name field, so the names go in the message
        val names = lottery.campsiteIds.mapNotNull { campsiteRepository.getCampsite(it)?.name }
        val where = if (names.isEmpty()) "a campsite" else names.joinToString(", ")
        val message = "You won a spot at $where. " +
            "Reserve your nights before ${formatDate(lottery.bookingEnd)}."
        // only a single-campsite lottery has one campsite to point to
        val campsiteId = lottery.campsiteIds.singleOrNull()
        val sentAt = lottery.drawnAt ?: now
        val notices = winnerIds.map { userId ->
            Notification(
                id = "draw_${lottery.id}_$userId",
                recipientId = userId,
                senderId = "system",
                campsiteId = campsiteId,
                lotteryId = lottery.id,
                type = NotificationType.WON,
                title = "You won the lottery!",
                message = message,
                createdAt = sentAt,
            )
        }
        notificationRepository.addAll(notices)
    }
}

// The default formatDate: date and time in the phone's own style, e.g. "Apr 9, 2027, 11:59 PM",
// since booking ends at a set time, not just on a day.
private fun dateAndTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
