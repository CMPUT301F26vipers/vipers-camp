/*
 * FirestoreNotificationRepository.kt
 * The real NotificationRepository: saves and loads inbox items in the Firestore "notifications"
 * collection (Repository pattern).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 * The inbox is sorted in the app instead of with orderBy, which would need an extra Firestore index.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Notification
import com.example.vipers.domain.model.NotificationType
import com.example.vipers.domain.repository.NotificationRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores notifications in Firestore. */
class FirestoreNotificationRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : NotificationRepository {

    private val collection = db.collection("notifications")

    override suspend fun addNotification(notification: Notification): String {
        val doc = collection.document()
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
}

/** Turns a notification into the fields saved in its Firestore document. */
fun Notification.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "recipientId" to recipientId,
    "senderId" to senderId,
    "campsiteId" to campsiteId,
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
    type = getString("type")?.let { NotificationType.valueOf(it) } ?: NotificationType.WON,
    title = getString("title") ?: "",
    message = getString("message") ?: "",
    read = getBoolean("read") ?: false,
    shownOnDevice = getBoolean("shownOnDevice") ?: false,
    batchId = getString("batchId"),
    createdAt = getLong("createdAt") ?: 0,
)
