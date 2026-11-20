/*
 * FirestoreMessageRepository.kt
 * The real MessageRepository: saves and loads owner-entrant messages in the Firestore "messages"
 * collection (Repository pattern, US 02.12.01).
 * Known issues: fields are mapped by hand because Firestore can't fill in Kotlin `val` properties.
 * A conversation is sorted in the app instead of with orderBy, which would need an extra
 * Firestore index. getAllThreads reads every message, which is fine for the admin log's size.
 */
package com.example.vipers.data.firestore

import com.example.vipers.domain.model.Message
import com.example.vipers.domain.repository.MessageRepository
import com.example.vipers.domain.repository.Subscription
import com.example.vipers.domain.repository.ThreadListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/** Stores messages in Firestore. */
class FirestoreMessageRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : MessageRepository {

    private val collection = db.collection("messages")

    override suspend fun addMessage(message: Message): String {
        val doc = collection.document()
        doc.set(message.copy(id = doc.id).toMap()).await()
        return doc.id
    }

    override fun listenToThread(reservationId: String, listener: ThreadListener): Subscription {
        // Only filters by reservationId and sorts on the device: adding orderBy("createdAt") to a
        // whereEqualTo query would need a composite Firestore index.
        val registration = collection
            .whereEqualTo("reservationId", reservationId)
            .addSnapshotListener { snapshot, error ->
                // e.g. permission denied; Firestore stops this listener after an error
                if (error != null || snapshot == null) {
                    listener.onThreadError()
                    return@addSnapshotListener
                }
                listener.onThreadChanged(Message.inOrder(snapshot.documents.map { it.toMessage() }))
            }
        return Subscription { registration.remove() }
    }

    override suspend fun getAllThreads(): List<List<Message>> =
        Message.threads(collection.get().await().documents.map { it.toMessage() })
}

/** Turns a message into the fields saved in its Firestore document. */
fun Message.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "reservationId" to reservationId,
    "senderId" to senderId,
    "text" to text,
    "createdAt" to createdAt,
)

/** Builds a message from its Firestore document. Missing fields get the model's defaults. */
fun DocumentSnapshot.toMessage(): Message = Message(
    id = id,
    reservationId = getString("reservationId") ?: "",
    senderId = getString("senderId") ?: "",
    text = getString("text") ?: "",
    createdAt = getLong("createdAt") ?: 0,
)
