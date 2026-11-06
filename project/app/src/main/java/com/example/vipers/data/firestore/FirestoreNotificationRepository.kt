/*
 * FirestoreNotificationRepository.kt
 * The real NotificationRepository: saves and loads inbox items in the Firestore "notifications"
 * collection (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 * The inbox is sorted in the app instead of with orderBy, which would need an extra Firestore index.
 * addAll uses transactions (to keep read items read), so unlike the other writes it fails offline
 * instead of waiting to upload; the draw it's used by needs a connection anyway.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.InboxListener
import com.example.vipers.domain.repository.NotificationRepository
import com.example.vipers.domain.repository.Subscription
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores notifications in Firestore. */
class FirestoreNotificationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : NotificationRepository {

    private val collection = db.collection("notifications")

    override suspend fun addAll(notifications: List<Notification>) {
        // check every ID first, so a bad one means nothing is written
        require(notifications.none { it.id.isBlank() }) { "Every notification needs an id" }
        // Firestore allows at most 500 writes in one transaction, so a big draw goes in several
        for (chunk in notifications.chunked(MAX_WRITES)) {
            db.runTransaction { tx ->
                // a transaction must do all its reads before any write
                val saved = chunk.map { tx.get(collection.document(it.id)) }
                for ((notification, snapshot) in chunk.zip(saved)) {
                    tx.set(collection.document(notification.id), notification.keepingStateOf(snapshot).toMap())
                }
            }.await()
        }
    }

    // This notification, but if it was already saved, with the saved read and shownOnDevice, so
    // sending it again doesn't make a read item unread or show it on the phone twice (US 01.03.01).
    private fun Notification.keepingStateOf(saved: DocumentSnapshot): Notification {
        if (!saved.exists()) return this
        return copy(
            read = saved.getBoolean("read") ?: false,
            shownOnDevice = saved.getBoolean("shownOnDevice") ?: false,
        )
    }

    private companion object {
        // the most writes Firestore allows in one transaction
        const val MAX_WRITES = 500
    }

    override suspend fun addNotification(notification: Notification): String {
        val doc = collection.document()
        doc.set(notification.copy(id = doc.id).toMap()).await()
        return doc.id
    }

    override suspend fun saveNotification(notification: Notification): String {
        val doc = if (notification.id.isNotBlank()) collection.document(notification.id) else collection.document()
        doc.set(notification.copy(id = doc.id).toMap()).await()
        return doc.id
    }

    override suspend fun getNotificationsFor(recipientId: String): List<Notification> =
        collection
            .whereEqualTo("recipientId", recipientId)
            .get().await()
            .documents.map { it.toNotification() }
            .sortedByDescending { it.createdAt }

    override suspend fun markRead(id: String) {
        collection.document(id).update("read", true).await()
    }

    override fun listenToInbox(recipientId: String, listener: InboxListener): Subscription {
        // Only filters by recipientId and sorts on the device: adding orderBy("createdAt") to a
        // whereEqualTo query would need a composite Firestore index.
        val registration = collection
            .whereEqualTo("recipientId", recipientId)
            .addSnapshotListener { snapshot, error ->
                // e.g. permission denied; Firestore stops this listener after an error
                if (error != null || snapshot == null) {
                    listener.onInboxError()
                    return@addSnapshotListener
                }
                listener.onInboxChanged(
                    snapshot.documents.map { it.toNotification() }.sortedByDescending { it.createdAt },
                )
            }
        return Subscription { registration.remove() }
    }
}

/** Turns a notification into the fields saved in its Firestore document. */
fun Notification.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "recipientId" to recipientId,
    "senderId" to senderId,
    "campsiteId" to campsiteId,
    "lotteryId" to lotteryId,
    "reservationId" to reservationId,
    "type" to type.name,
    "title" to title,
    "message" to message,
    "read" to read,
    "shownOnDevice" to shownOnDevice,
    "batchId" to batchId,
    "createdAt" to createdAt,
)

/** Builds a notification from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toNotification(): Notification = Notification(
    id = id,
    recipientId = getString("recipientId") ?: "",
    senderId = getString("senderId") ?: "system",
    campsiteId = getString("campsiteId"),
    lotteryId = getString("lotteryId"),
    reservationId = getString("reservationId"),
    type = getString("type")?.let { NotificationType.valueOf(it) } ?: NotificationType.WON,
    title = getString("title") ?: "",
    message = getString("message") ?: "",
    read = getBoolean("read") ?: false,
    shownOnDevice = getBoolean("shownOnDevice") ?: false,
    batchId = getString("batchId"),
    createdAt = getLong("createdAt") ?: 0,
)
