/*
 * AdminController.kt
 * Loads campsites, user profiles, images, and owner-entrant message logs for admin browsing (US 03.04.01, US 03.05.01, US 03.06.01, US 03.12.01).
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
import com.example.vipers.domain.model.Message
import com.example.vipers.domain.model.Reservation
import com.example.vipers.domain.model.UserProfile
import com.example.vipers.domain.repository.CampsiteRepository
import com.example.vipers.domain.repository.ImageRepository
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.ProfileRepository
import com.example.vipers.domain.repository.ReservationRepository
import kotlinx.coroutines.CancellationException
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

/** Loads campsites, user profiles, images, and owner-entrant message logs for admin browsing. */
class AdminController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository? = null,
    private val messageRepository: MessageRepository? = null,
    private val reservationRepository: ReservationRepository? = null,
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
        viewModelScope.launch { fetchMessageLogs() }
    }

    /**
     * Loads all owner-entrant message logs and resolves their reservation and profile details.
     * Can be called directly from unit tests using runBlocking or runTest.
     */
    suspend fun fetchMessageLogs() {
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
            for (thread in threads) {
                if (thread.isEmpty()) continue
                val reservationId = thread.first().reservationId
                val lastMessageTime = thread.last().createdAt

                val reservation = try {
                    reservationRepository?.getReservation(reservationId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

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

            messageLogs = logs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageLogsLoadFailed = true
            messageLogsError = "Unable to load message logs."
        } finally {
            isMessageLogsLoading = false
        }
    }

    companion object {
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
                )
            }
        }
    }
}
