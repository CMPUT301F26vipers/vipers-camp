/*
 * AdminController.kt
 * Loads campsites, user profiles, images, the notification log, and owner-entrant message logs for admin browsing (US 03.04.01, US 03.05.01, US 03.06.01, US 03.08.01, US 03.12.01).
 * Known issues: none.
 */
package com.example.vipers.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vipers.CampApp
import com.example.vipers.domain.model.Campsite
import com.example.vipers.domain.model.ImageAsset
import com.example.vipers.domain.model.Lottery
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.NotificationLogEntry
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.LotteryRepository
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import com.example.vipers.ui.messages.messageTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A conversation thread for the admin message log (US 03.12.01).
 *
 * @property reservationId The reservation this conversation belongs to.
 * @property campsiteName The name of the campsite booked.
 * @property ownerName The resolved display name of the campsite owner.
 * @property entrantName The resolved display name of the entrant.
 * @property lastMessageTime Timestamp of the most recent message in the conversation.
 * @property messages The full list of messages in this conversation, ordered oldest first.
 */
data class MessageLogConversation(
    val reservationId: String,
    val campsiteName: String,
    val ownerName: String,
    val entrantName: String,
    val lastMessageTime: Long,
    val messages: List<Message>,
    val ownerId: String = "",
    val entrantId: String = "",
) {
    /** Alias for [lastMessageTime]. */
    val lastMessageAt: Long
        get() = lastMessageTime

    /** Alias for [messages]. */
    val thread: List<Message>
        get() = messages

    /** Returns the resolved display name for the sender of [message]. */
    fun senderName(message: Message): String {
        return when {
            ownerId.isNotBlank() && message.senderId == ownerId -> ownerName
            entrantId.isNotBlank() && message.senderId == entrantId -> entrantName
            else -> if (message.senderId.isNotBlank()) message.senderId else "Deleted user"
        }
    }
}

/**
 * One row of the admin Notification Log (US 03.08.01, mockup A09), ready to show.
 *
 * @property key The row's [NotificationLogEntry.key], unique within the log.
 * @property typeLabel What kind of notification it is, e.g. "Won" or "Owner message".
 * @property senderName "System" for automatic notifications, otherwise the sender's current name.
 * @property recipientLabel The recipient's name when there is one, otherwise "Waiting list" for an
 * owner message or "<n> entrants".
 * @property campsiteLabel The campsite's name, or the lottery's campsite names joined with ", ";
 * null when the notification isn't about a campsite or lottery.
 * @property time When it was sent, e.g. "Apr 2 · 9:15 AM".
 * @property text The notification's message.
 * @property entry The grouped notifications this row shows.
 */
data class NotificationLogRow(
    val key: String,
    val typeLabel: String,
    val senderName: String,
    val recipientLabel: String,
    val campsiteLabel: String?,
    val time: String,
    val text: String,
    val entry: NotificationLogEntry,
)

/** Loads campsites, user profiles, images, the notification log, and owner-entrant message logs for admin browsing. */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository? = null,
    private val messageRepository: MessageRepository? = null,
    private val reservationRepository: ReservationRepository? = null,
    private val notificationRepository: NotificationRepository? = null,
    private val lotteryRepository: LotteryRepository? = null,
    private val formatTime: (Long) -> String = { messageTime(it) },
) : ViewModel() {

    var campsites by mutableStateOf<List<Campsite>>(emptyList())
        private set

    var ownerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var profiles by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    var ownerProfileIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loadFailed by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Whether user profiles are currently being loaded. */
    var isProfilesLoading by mutableStateOf(false)
        private set

    val profilesLoading: Boolean
        get() = isProfilesLoading

    /** Whether the most recent attempt to load profiles failed. */
    var profilesLoadFailed by mutableStateOf(false)
        private set

    /** Error message if loading profiles failed. */
    var profilesError by mutableStateOf<String?>(null)
        private set

    val profileError: String?
        get() = profilesError

    var images by mutableStateOf<List<ImageAsset>>(emptyList())
        private set

    /** Maps image IDs to their resolved owner or fallback display names. */
    var imageOwnerNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isImagesLoading by mutableStateOf(false)
        private set

    val imagesLoading: Boolean
        get() = isImagesLoading

    var imagesLoadFailed by mutableStateOf(false)
        private set

    var imagesError by mutableStateOf<String?>(null)
        private set

    val imageError: String?
        get() = imagesError

    /** Loaded message log conversations, sorted newest message first. */
    var messageLogs by mutableStateOf<List<MessageLogConversation>>(emptyList())
        private set

    /** Alias for [messageLogs]. */
    val conversations: List<MessageLogConversation>
        get() = messageLogs

    /** Whether message logs are currently being loaded. */
    var isMessageLogsLoading by mutableStateOf(false)
        private set

    /** Alias for [isMessageLogsLoading]. */
    val messageLogsLoading: Boolean
        get() = isMessageLogsLoading

    /** Alias for [isMessageLogsLoading]. */
    val conversationsLoading: Boolean
        get() = isMessageLogsLoading

    /** Whether the most recent attempt to load message logs failed. */
    var messageLogsLoadFailed by mutableStateOf(false)
        private set

    /** Alias for [messageLogsLoadFailed]. */
    val conversationsLoadFailed: Boolean
        get() = messageLogsLoadFailed

    /** Error message if loading message logs failed. */
    var messageLogsError by mutableStateOf<String?>(null)
        private set

    /** Alias for [messageLogsError]. */
    val messageLogError: String?
        get() = messageLogsError

    /** Alias for [messageLogsError]. */
    val conversationsError: String?
        get() = messageLogsError

    /** The admin Notification Log (US 03.08.01): one row per send, newest first. */
    var notificationLog by mutableStateOf<List<NotificationLogRow>>(emptyList())
        private set

    /** Whether the notification log is being loaded. */
    var isNotificationLogLoading by mutableStateOf(false)
        private set

    /** Whether the most recent attempt to load the notification log failed. */
    var notificationLogLoadFailed by mutableStateOf(false)
        private set

    /** Error message if loading the notification log failed. */
    var notificationLogError by mutableStateOf<String?>(null)
        private set

    init {
        loadCampsites()
    }

    /** Returns the display name for the given [ownerId]. */
    fun getOwnerName(ownerId: String): String {
        return ownerNames[ownerId] ?: "Deleted user"
    }

    /** Returns whether the user with the given [profileId] owns at least one campsite. */
    fun isCampsiteOwner(profileId: String): Boolean {
        return profileId in ownerProfileIds
    }

    /** Returns the profile with the given [profileId] from the loaded profiles, or null if not found. */
    fun getProfile(profileId: String): UserProfile? {
        return profiles.firstOrNull { it.id == profileId }
    }

    /** Returns the active campsites owned by [ownerId] from the loaded campsites list. */
    fun getCampsitesForOwner(ownerId: String): List<Campsite> {
        return campsites.filter { it.ownerId == ownerId && !it.removed }
    }

    /** Returns the image asset with the given [imageId], or null if not found. */
    fun getImage(imageId: String): ImageAsset? {
        return images.firstOrNull { it.id == imageId }
    }

    /** Returns the resolved owner or fallback name for the given [image]. */
    fun getImageOwnerName(image: ImageAsset): String {
        return imageOwnerNames[image.id]
            ?: image.label.trim().ifBlank { null }
            ?: genericImageLabel(image.ownerType)
    }

    /** Returns the resolved owner or fallback name for the image with the given [imageId]. */
    fun getImageOwnerName(imageId: String): String {
        val image = getImage(imageId)
        return imageOwnerNames[imageId]
            ?: image?.let { getImageOwnerName(it) }
            ?: "Photo"
    }

    /** Loads all campsites that have not been removed, along with owner display names. */
    fun loadCampsites() {
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            error = null

            try {
                val loadedCampsites = campsiteRepository.getAllCampsites()
                val uniqueOwnerIds = loadedCampsites.map { it.ownerId }.distinct()
                val namesMap = uniqueOwnerIds.associateWith { ownerId ->
                    profileRepository.getDisplayName(ownerId)
                }
                ownerNames = namesMap
                campsites = loadedCampsites
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadFailed = true
                error = "Unable to load campsites."
            } finally {
                isLoading = false
            }
        }
    }

    /** Loads all user profiles and determines campsite ownership in the background. */
    fun loadProfiles() {
        isProfilesLoading = true
        profilesLoadFailed = false
        profilesError = null
        viewModelScope.launch { fetchProfiles() }
    }

    /** Loads all user profiles and determines campsite ownership. Can be called directly by unit tests. */
    internal suspend fun fetchProfiles() {
        isProfilesLoading = true
        profilesLoadFailed = false
        profilesError = null

        try {
            val loadedProfiles = profileRepository.getAllProfiles()
            val owners = loadedProfiles
                .filter { profile -> campsiteRepository.getCampsitesByOwner(profile.id).isNotEmpty() }
                .map { it.id }
                .toSet()
            ownerProfileIds = owners
            profiles = loadedProfiles
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            profilesLoadFailed = true
            profilesError = "Unable to load profiles."
        } finally {
            isProfilesLoading = false
        }
    }

    /** Loads all uploaded images in the background. */
    fun loadImages() {
        viewModelScope.launch { fetchImages() }
    }

    /** Loads all uploaded images, newest first. Can be called directly from unit tests using runBlocking. */
    suspend fun fetchImages() {
        isImagesLoading = true
        imagesLoadFailed = false
        imagesError = null

        try {
            val repo = imageRepository ?: throw IllegalStateException("ImageRepository not configured")
            val loadedImages = repo.getAllImages()

            val resolvedNames = mutableMapOf<String, String>()
            val ownerNameCache = mutableMapOf<Pair<String, String>, String?>()

            for (image in loadedImages) {
                val key = Pair(image.ownerType, image.ownerId)
                val resolvedOwner = ownerNameCache.getOrPut(key) {
                    try {
                        when (image.ownerType) {
                            ImageAsset.OWNER_CAMPSITE -> {
                                campsiteRepository.getCampsite(image.ownerId)?.name?.trim()?.ifBlank { null }
                            }
                            ImageAsset.OWNER_PROFILE -> {
                                profileRepository.getProfile(image.ownerId)?.name?.trim()?.ifBlank { null }
                            }
                            else -> null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalName = resolvedOwner
                    ?: image.label.trim().ifBlank { null }
                    ?: genericImageLabel(image.ownerType)

                resolvedNames[image.id] = finalName
            }

            imageOwnerNames = resolvedNames
            images = loadedImages
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            imagesLoadFailed = true
            imagesError = "Unable to load images."
        } finally {
            isImagesLoading = false
        }
    }

    private fun genericImageLabel(ownerType: String): String {
        return if (ownerType == ImageAsset.OWNER_CAMPSITE) "Campsite photo" else "Profile photo"
    }

    /** Returns the conversation with the given [reservationId], or null if not found. */
    fun getConversation(reservationId: String): MessageLogConversation? {
        return messageLogs.firstOrNull { it.reservationId == reservationId }
    }

    /** Returns the conversation with the given [reservationId], or null if not found. */
    fun getMessageLog(reservationId: String): MessageLogConversation? {
        return getConversation(reservationId)
    }

    /** Loads all owner-entrant message logs in the background. */
    fun loadMessageLogs() {
        messageLogJob?.cancel()
        messageLogJob = viewModelScope.launch { fetchMessageLogs() }
    }

    private var messageLogJob: Job? = null
    private var messageLogRequestId = 0

    /**
     * Loads all owner-entrant message logs and resolves their reservation and profile details.
     * Can be called directly from unit tests using runBlocking or runTest.
     */
    suspend fun fetchMessageLogs() {
        val requestId = ++messageLogRequestId
        isMessageLogsLoading = true
        messageLogsLoadFailed = false
        messageLogsError = null

        try {
            val repo = messageRepository ?: throw IllegalStateException("MessageRepository not configured")
            val threads = repo.getAllThreads()

            val profileNameCache = mutableMapOf<String, String>()
            suspend fun resolveName(id: String): String {
                if (id.isBlank()) return "Deleted user"
                return profileNameCache.getOrPut(id) {
                    try {
                        profileRepository.getDisplayName(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Deleted user"
                    }
                }
            }

            val logs = mutableListOf<MessageLogConversation>()
            for (rawThread in threads) {
                if (rawThread.isEmpty()) continue
                val thread = Message.inOrder(rawThread)
                val reservationId = thread.first().reservationId
                val lastMessageTime = thread.last().createdAt

                val reservation = reservationRepository?.getReservation(reservationId)

                val ownerName = if (reservation != null) resolveName(reservation.ownerId) else "Deleted user"
                val entrantName = if (reservation != null) resolveName(reservation.userId) else "Deleted user"
                val campsiteName = reservation?.campsiteName?.trim()?.ifBlank { null } ?: "Unknown campsite"

                logs.add(
                    MessageLogConversation(
                        reservationId = reservationId,
                        campsiteName = campsiteName,
                        ownerName = ownerName,
                        entrantName = entrantName,
                        lastMessageTime = lastMessageTime,
                        messages = thread,
                        ownerId = reservation?.ownerId.orEmpty(),
                        entrantId = reservation?.userId.orEmpty(),
                    )
                )
            }

            logs.sortWith(
                compareByDescending<MessageLogConversation> { it.lastMessageTime }
                    .thenBy { it.reservationId }
            )

            if (requestId == messageLogRequestId) {
                messageLogs = logs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (requestId == messageLogRequestId) {
                messageLogsLoadFailed = true
                messageLogsError = "Unable to load message logs."
            }
        } finally {
            if (requestId == messageLogRequestId) {
                isMessageLogsLoading = false
            }
        }
    }

    /** Loads the admin Notification Log in the background (US 03.08.01). */
    fun loadNotificationLog() {
        notificationLogJob?.cancel()
        notificationLogJob = viewModelScope.launch { fetchNotificationLog() }
    }

    private var notificationLogJob: Job? = null
    private var notificationLogRequestId = 0

    /**
     * Loads every notification, groups it into one row per send ([NotificationLogEntry.fromNotifications]),
     * and looks up the names each row shows (US 03.08.01). Each user, campsite and lottery is loaded
     * once however many rows share it, and removed campsites still show their names. A name that
     * can't be loaded shows as "Deleted user" or "Unknown campsite" instead of failing the log.
     * Can be called directly from unit tests using runBlocking or runTest.
     */
    suspend fun fetchNotificationLog() {
        val requestId = ++notificationLogRequestId
        isNotificationLogLoading = true
        notificationLogLoadFailed = false
        notificationLogError = null

        try {
            val repo = notificationRepository
                ?: throw IllegalStateException("NotificationRepository not configured")
            val entries = NotificationLogEntry.fromNotifications(repo.getAllNotifications())

            val userIds = entries.flatMap { entry ->
                listOfNotNull(
                    entry.senderId.takeUnless { it == SYSTEM_SENDER },
                    entry.recipientIds.singleOrNull(),
                )
            }.distinct()
            val names = userIds.associateWith { id -> displayNameOrDeleted(id) }

            val lotteries = entries.filter { it.campsiteId == null }.mapNotNull { it.lotteryId }.distinct()
                .associateWith { id -> loadOrNull { lotteryRepository?.getLottery(id) } }
            val campsiteIds = (entries.mapNotNull { it.campsiteId } +
                lotteries.values.filterNotNull().flatMap { it.campsiteIds }).distinct()
            val campsiteNames = campsiteIds.associateWith { id ->
                loadOrNull { campsiteRepository.getCampsite(id) }?.name?.trim()?.ifBlank { null }
            }

            val rows = entries.map { entry ->
                NotificationLogRow(
                    key = entry.key,
                    typeLabel = entry.typeLabel,
                    senderName = if (entry.senderId == SYSTEM_SENDER) "System" else names.getValue(entry.senderId),
                    recipientLabel = recipientLabel(entry, names),
                    campsiteLabel = campsiteLabel(entry, lotteries, campsiteNames),
                    time = formatTime(entry.time),
                    text = entry.message,
                    entry = entry,
                )
            }

            if (requestId == notificationLogRequestId) {
                notificationLog = rows
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (requestId == notificationLogRequestId) {
                notificationLogLoadFailed = true
                notificationLogError = "Unable to load notification log."
            }
        } finally {
            if (requestId == notificationLogRequestId) {
                isNotificationLogLoading = false
            }
        }
    }

    private fun recipientLabel(entry: NotificationLogEntry, names: Map<String, String>): String {
        val only = entry.recipientIds.singleOrNull()
        return when {
            only != null -> names.getValue(only)
            entry.type == NotificationType.OWNER_BROADCAST -> "Waiting list"
            else -> "${entry.recipientCount} entrants"
        }
    }

    private fun campsiteLabel(
        entry: NotificationLogEntry,
        lotteries: Map<String, Lottery?>,
        campsiteNames: Map<String, String?>,
    ): String? {
        entry.campsiteId?.let { return campsiteNames[it] ?: UNKNOWN_CAMPSITE }
        val lotteryId = entry.lotteryId ?: return null
        val ids = lotteries[lotteryId]?.campsiteIds.orEmpty()
        if (ids.isEmpty()) return UNKNOWN_CAMPSITE
        return ids.joinToString(", ") { campsiteNames[it] ?: UNKNOWN_CAMPSITE }
    }

    private suspend fun displayNameOrDeleted(id: String): String =
        loadOrNull { profileRepository.getDisplayName(id) } ?: "Deleted user"

    private suspend fun <T> loadOrNull(load: suspend () -> T?): T? =
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        private const val SYSTEM_SENDER = "system"
        private const val UNKNOWN_CAMPSITE = "Unknown campsite"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampApp

                AdminController(
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                    imageRepository = application.container.imageRepository,
                    messageRepository = application.container.messageRepository,
                    reservationRepository = application.container.reservationRepository,
                    notificationRepository = application.container.notificationRepository,
                    lotteryRepository = application.container.lotteryRepository,
                )
            }
        }
    }
}
