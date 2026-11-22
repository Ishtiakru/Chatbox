package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class MessagesSnapshot(
    val messages: List<LiveMessage>,
    val fromCache: Boolean,
    val oldestCursor: DocumentSnapshot?
)

class LiveChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun registerProfile(uid: String, displayNameRaw: String?): Result<Unit> {
        val path = "profiles/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed; refusing cross-user profile write." }
            val displayName = (displayNameRaw ?: "Chatbox user").trim().take(80).ifEmpty { "Chatbox user" }
            db.collection("profiles").document(uid)
                .set(mapOf("uid" to uid, "displayName" to displayName))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observePeerProfile(peerUid: String): Flow<UserProfile?> {
        require(peerUid.isNotBlank()) { "Peer UID must not be blank" }
        return db.collection("profiles").document(peerUid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists() || snapshot.metadata.isFromCache) null
                else snapshot.toObject(UserProfile::class.java)
            }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        return db.collection("conversations")
            .whereArrayContains("participants", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    fun observeRecentMessages(conversationId: String, pageSize: Long = 100L): Flow<MessagesSnapshot> {
        require(conversationId.isNotBlank()) { "Conversation ID must not be blank" }
        return db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(pageSize)
            .snapshots()
            .map { snapshot ->
                val items = snapshot.documents.map { doc ->
                    val base = doc.toObject(LiveMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: LiveMessage(id = doc.id)
                    base.copy(pendingWrites = doc.metadata.hasPendingWrites())
                }
                MessagesSnapshot(
                    messages = items,
                    fromCache = snapshot.metadata.isFromCache,
                    oldestCursor = snapshot.documents.firstOrNull()
                )
            }
    }

    suspend fun loadOlderMessages(
        conversationId: String,
        cursor: DocumentSnapshot,
        pageSize: Long = 100L
    ): Result<Pair<List<LiveMessage>, DocumentSnapshot?>> {
        val path = "conversations/$conversationId/messages"
        return try {
            requireUserId()
            val snapshot = db.collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .endBefore(cursor)
                .limitToLast(pageSize)
                .get(Source.SERVER)
                .await()
            val items = snapshot.documents.mapNotNull { doc ->
                doc.toObject(LiveMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(items to snapshot.documents.firstOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createConversation(uid: String, rawPeerUid: String): Result<String> {
        val peerUid = rawPeerUid.trim()
        val path = "conversations"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            require(peerUid.isNotEmpty() && peerUid.length <= 128 && !peerUid.contains('/')) {
                "Enter a valid Chatbox ID."
            }
            require(peerUid != uid) { "You cannot start a conversation with yourself." }
            val peerSnap = db.collection("profiles").document(peerUid).get(Source.SERVER).await()
            if (!peerSnap.exists()) {
                return Result.failure(IllegalArgumentException("No Chatbox user found for that ID. They must sign in to Live chats first."))
            }
            val docRef = db.collection("conversations").document()
            val payload = mapOf(
                "participants" to listOf(uid, peerUid),
                "createdBy" to uid,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToConversation(conversationId: String, status: String): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            requireUserId()
            require(conversationId.isNotBlank()) { "Invalid conversation ID." }
            require(status == "active" || status == "closed") { "Invalid conversation status." }
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        uid: String,
        rawBody: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationMs: Long? = null
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            val body = rawBody.trim()
            val cleanImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
            val cleanAudioUrl = audioUrl?.trim()?.takeIf { it.isNotEmpty() }
            val cleanAudioDurationMs = if (cleanAudioUrl != null) {
                audioDurationMs?.coerceIn(0L, 300_000L)
            } else {
                null
            }
            require(body.length <= 2000) {
                "Message must be 2000 characters or fewer."
            }
            require(body.isNotEmpty() || cleanImageUrl != null || cleanAudioUrl != null) {
                "Enter a message, attach an image, or record a voice note."
            }
            if (cleanImageUrl != null) {
                require(
                    cleanImageUrl.length <= 2048 &&
                        (cleanImageUrl.startsWith("https://firebasestorage.googleapis.com/") ||
                            cleanImageUrl.startsWith("https://storage.googleapis.com/"))
                ) {
                    "Attached image URL must be a valid Firebase Storage HTTPS URL."
                }
            }
            if (cleanAudioUrl != null) {
                require(
                    cleanAudioUrl.length <= 2048 &&
                        (cleanAudioUrl.startsWith("https://firebasestorage.googleapis.com/") ||
                            cleanAudioUrl.startsWith("https://storage.googleapis.com/"))
                ) {
                    "Attached voice note URL must be a valid Firebase Storage HTTPS URL."
                }
            }
            val payload = buildMap<String, Any> {
                put("senderId", uid)
                put("body", body)
                if (cleanImageUrl != null) {
                    put("imageUrl", cleanImageUrl)
                }
                if (cleanAudioUrl != null) {
                    put("audioUrl", cleanAudioUrl)
                    if (cleanAudioDurationMs != null) {
                        put("audioDurationMs", cleanAudioDurationMs)
                    }
                }
                put("sentAt", FieldValue.serverTimestamp())
            }
            val docRef = db.collection("conversations").document(conversationId)
                .collection("messages").document()
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeBlockedIds(uid: String): Flow<List<String>> {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        return db.collection("userBlocks").document(uid).collection("blocked")
            .snapshots()
            .map { snapshot -> snapshot.documents.map { it.id } }
    }

    suspend fun blockAndClose(uid: String, peerUid: String, conversation: Conversation): Result<Unit> {
        val path = "userBlocks/$uid/blocked/$peerUid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            require(peerUid.isNotBlank() && peerUid != uid && !peerUid.contains('/')) { "Invalid peer ID." }
            require(conversation.id.isNotBlank()) { "Invalid conversation." }
            val batch = db.batch()
            val blockRef = db.collection("userBlocks").document(uid).collection("blocked").document(peerUid)
            batch.set(blockRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
            if (conversation.status != "closed") {
                val convRef = db.collection("conversations").document(conversation.id)
                batch.update(
                    convRef,
                    mapOf("status" to "closed", "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun unblock(uid: String, peerUid: String): Result<Unit> {
        val path = "userBlocks/$uid/blocked/$peerUid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "Signed-in user changed." }
            require(peerUid.isNotBlank() && peerUid != uid && !peerUid.contains('/')) { "Invalid peer ID." }
            db.collection("userBlocks").document(uid).collection("blocked").document(peerUid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
