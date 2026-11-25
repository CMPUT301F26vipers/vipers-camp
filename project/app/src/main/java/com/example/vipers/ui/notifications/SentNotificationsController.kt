/*
 * SentNotificationsController.kt
 * The controller (a ViewModel) behind the owner's Sent Notifications screen (mockup O13,
 * US 02.10.02). It loads every notification this owner has sent through NotificationService,
 * newest first, and turns each send into the three lines a row shows: the message, the campsite
 * and lottery, and the time with the number of recipients.
 * Known issues: none. The times are worked out when the list loads, so the year only appears or
 * disappears on the next load after New Year.
 */
package com.example.vipers.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.service.NotificationService
import com.example.vipers.domain.service.SentNotificationItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val thisYearFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
private val otherYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)

/**
 * What one row of the Sent Notifications list shows (O13, US 02.10.02).
 *
 * @property batchId The send's batch ID, used as the row's key in the list.
 * @property message The text that was sent, e.g. "Reminder to winners".
 * @property subtitle The campsite and lottery, e.g. "Hinton Ridge Campground · May weekends".
 * @property detail When it was sent and to how many, e.g. "Apr 5, 9:00 AM · sent to 36".
 */
data class SentNotificationRow(
    val batchId: String,
    val message: String,
    val subtitle: String,
    val detail: String,
)

/**
 * Holds the owner [ownerId]'s sent notifications for the Sent Notifications screen (O13,
 * US 02.10.02), loaded with [notificationService]. Takes the service in its constructor so tests
 * can build it on fake repositories.
 *
 * @param notificationService Loads the owner's sends with their campsite and lottery labels.
 * @param ownerId Device ID of the owner whose sends are shown.
 */
class SentNotificationsController(
    private val notificationService: NotificationService,
    private val ownerId: String,
) : ViewModel() {

    /** True while the sent notifications are loading. */
    var loading by mutableStateOf(true)
        private set

    /** A message to show if the sent notifications couldn't be loaded, or null. */
    var error by mutableStateOf<String?>(null)
        private set

    /** One row per send, newest first. */
    var items by mutableStateOf(emptyList<SentNotificationRow>())
        private set

    /** True when the list has loaded and the owner hasn't sent anything. */
    val isEmpty: Boolean
        get() = !loading && error == null && items.isEmpty()

    init {
        viewModelScope.launch { load() }
    }

    /**
     * Loads the owner's sends, newest first, and builds their rows. Times are shown in [zone],
     * with the year only when it isn't [now]'s year (milliseconds since 1970). On failure [items]
     * is emptied and [error] is set. The screen uses [retry]; unit tests call this directly.
     */
    suspend fun load(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        loading = true
        error = null
        try {
            items = notificationService.sentNotifications(ownerId).map { toRow(it, now, zone) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            items = emptyList()
            error = "Couldn't load your sent notifications. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    /** Clears [error] and loads the sent notifications again in the background. */
    fun retry() {
        error = null
        viewModelScope.launch { load() }
    }

    private fun toRow(item: SentNotificationItem, now: Long, zone: ZoneId) = SentNotificationRow(
        batchId = item.sent.batchId,
        message = item.sent.message,
        subtitle = "${item.campsiteLabel} · ${item.lotteryLabel}",
        detail = "${sentTime(item.sent.sentAt, now, zone)} · sent to ${item.sent.recipientCount}",
    )

    private fun sentTime(sentAt: Long, now: Long, zone: ZoneId): String {
        val sent = Instant.ofEpochMilli(sentAt).atZone(zone)
        val thisYear = Instant.ofEpochMilli(now).atZone(zone).year
        return sent.format(if (sent.year == thisYear) thisYearFormat else otherYearFormat)
    }

    companion object {
        /** Makes the controller with the app's real repositories and this device's ID. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CampApp
                SentNotificationsController(
                    NotificationService(
                        app.container.notificationRepository,
                        app.container.campsiteRepository,
                        app.container.profileRepository,
                        app.container.lotteryRepository,
                    ),
                    app.container.deviceId,
                )
            }
        }
    }
}
