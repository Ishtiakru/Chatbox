package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.example.data.model.ChatboxConstants
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.MessagesPage
import com.example.data.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class LiveChatRepository(private val context: Context) {
    private val db: FirebaseFirestore = FirebaseProviders.firestore(context)
    private val storage = FirebaseProviders.storage()

    companion object {
        const val DEFAULT_ABOUT_TEXT = "Hey there! I am using Chatbox."
        private const val MAX_IMAGE_DIMENSION = 1600
        private const val JPEG_QUALITY = 85

        fun buildConversationId(uidA: String, uidB: String): String =
            if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"

        fun normalizePhoneDigits(raw: String): String =
            raw.filter { it.isDigit() }.take(15)

        fun buildPhoneLookupCandidates(raw: String): List<String> {
            val digits = normalizePhoneDigits(raw)
            if (digits.isEmpty()) return emptyList()
            val list = linkedSetOf(digits)
            if (digits.startsWith("0") && digits.length == 11) {
                list.add("88$digits")
            } else if (digits.startsWith("880") && digits.length == 13) {
                list.add(digits.drop(2))
            }
            return list.filter { ChatboxConstants.VALID_PHONE_KEY_REGEX.matches(it) }
        }

        fun deterministicSixDigitSeed(uid: String): String {
            var hash = 0x811c9dc5.toInt()
            for (ch in uid) {
                hash = hash xor ch.code
                hash *= 0x01000193
            }
            val positive = if (hash == Int.MIN_VALUE) 0 else abs(hash)
            return (100000 + (positive % 900000)).toString()
        }
    }

    private suspend fun allocateUniqueShortId(uid: String, displayName: String): String {
        val firstCandidate = deterministicSixDigitSeed(uid)
        val candidates = buildList {
            add(firstCandidate)
            repeat(6) {
                add(Random.nextInt(100000, 1000000).toString())
            }
        }
        for (candidate in candidates) {
            try {
                val ref = db.collection("shortIds").document(candidate)
                val snap = ref.get(Source.SERVER).await()
                if (!snap.exists()) {
                    ref.set(mapOf("uid" to uid, "displayName" to displayName)).await()
                    return candidate
                }
                if (snap.getString("uid") == uid) {
                    if (snap.getString("displayName") != displayName) {
                        ref.update("displayName", displayName).await()
                    }
                    return candidate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Retry next candidate on collision
            }
        }
        return firstCandidate
    }

    suspend fun ensureOwnProfile(uid: String, rawDisplayName: String?): Result<UserProfile> {
        if (!ChatboxConstants.VALID_ID_REGEX.matches(uid)) {
            return Result.failure(IllegalArgumentException("Invalid Chatbox user ID."))
        }
        val fallbackName = rawDisplayName?.trim().orEmpty()
            .take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty { "Chatbox User" }
        val path = "profiles/$uid"
        return try {
            val ref = db.collection("profiles").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val shortId = allocateUniqueShortId(uid, fallbackName)
                val profileData = mapOf(
                    "uid" to uid,
                    "displayName" to fallbackName,
                    "shortId" to shortId,
                    "about" to DEFAULT_ABOUT_TEXT
                )
                ref.set(profileData).await()
                Result.success(UserProfile(uid, fallbackName, shortId, DEFAULT_ABOUT_TEXT))
            } else {
                val existingName = snap.getString("displayName")?.trim()
                    .orEmpty().ifEmpty { fallbackName }
                val about = snap.getString("about") ?: DEFAULT_ABOUT_TEXT
                var shortId = snap.getString("shortId").orEmpty()
                if (!ChatboxConstants.VALID_SHORT_ID_REGEX.matches(shortId)) {
                    shortId = allocateUniqueShortId(uid, existingName)
                    ref.set(
                        mapOf(
                            "uid" to uid,
                            "displayName" to existingName,
                            "shortId" to shortId,
                            "about" to about
                        )
                    ).await()
                } else {
                    runCatching {
                        val idxRef = db.collection("shortIds").document(shortId)
                        if (!idxRef.get().await().exists()) {
                            idxRef.set(mapOf("uid" to uid, "displayName" to existingName)).await()
                        }
                    }
                }
                Result.success(UserProfile(uid, existingName, shortId, about))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun loadOwnProfileDetails(
        uid: String,
        rawDisplayName: String?
    ): Result<Pair<UserProfile, String>> {
        val profileResult = ensureOwnProfile(uid, rawDisplayName)
        val profile = profileResult.getOrElse { return Result.failure(it) }
        val phoneNumber = runCatching {
            val priv = db.collection("users").document(uid)
                .collection("private").document("profile")
                .get().await()
            priv.getString("phoneNumber").orEmpty()
        }.getOrDefault("")
        return Result.success(profile to phoneNumber)
    }

    suspend fun saveOwnWhatsAppProfile(
        uid: String,
        currentShortId: String,
        displayName: String,
        about: String,
        phoneNumber: String,
        previousPhoneNumber: String
    ): Result<Pair<UserProfile, String>> {
        val cleanName = displayName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        }
        val cleanAbout = about.trim().take(ChatboxConstants.MAX_ABOUT_LENGTH)
            .ifEmpty { DEFAULT_ABOUT_TEXT }
        val cleanPhone = phoneNumber.trim().take(ChatboxConstants.MAX_PHONE_LENGTH)
        val newDigits = normalizePhoneDigits(cleanPhone)
        val oldDigits = normalizePhoneDigits(previousPhoneNumber)

        if (cleanPhone.isNotEmpty() && !ChatboxConstants.VALID_PHONE_KEY_REGEX.matches(newDigits)) {
            return Result.failure(
                IllegalArgumentException("Enter a valid phone number (7 to 15 digits, e.g. +8801XXXXXXXXX).")
            )
        }

        return try {
            val existingPhoneSnap = if (newDigits.isNotEmpty() && ChatboxConstants.VALID_PHONE_KEY_REGEX.matches(newDigits)) {
                val phoneRef = db.collection("phoneDirectory").document(newDigits)
                val snap = phoneRef.get().await()
                if (snap.exists() && snap.getString("uid") != uid) {
                    return Result.failure(
                        IllegalStateException("This phone number is already linked to another Chatbox account.")
                    )
                }
                snap
            } else {
                null
            }

            val shortId = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(currentShortId)) {
                currentShortId
            } else {
                allocateUniqueShortId(uid, cleanName)
            }

            db.collection("profiles").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "displayName" to cleanName,
                    "shortId" to shortId,
                    "about" to cleanAbout
                )
            ).await()

            runCatching {
                val shortRef = db.collection("shortIds").document(shortId)
                val snap = shortRef.get().await()
                if (!snap.exists()) {
                    shortRef.set(mapOf("uid" to uid, "displayName" to cleanName)).await()
                } else if (snap.getString("uid") == uid) {
                    shortRef.update("displayName", cleanName).await()
                }
            }

            if (oldDigits.isNotEmpty() &&
                oldDigits != newDigits &&
                ChatboxConstants.VALID_PHONE_KEY_REGEX.matches(oldDigits)
            ) {
                runCatching {
                    val oldRef = db.collection("phoneDirectory").document(oldDigits)
                    val oldSnap = oldRef.get().await()
                    if (oldSnap.exists() && oldSnap.getString("uid") == uid) {
                        oldRef.delete().await()
                    }
                }
            }

            if (newDigits.isNotEmpty() && ChatboxConstants.VALID_PHONE_KEY_REGEX.matches(newDigits)) {
                val phoneRef = db.collection("phoneDirectory").document(newDigits)
                if (existingPhoneSnap?.exists() == true) {
                    phoneRef.update("displayName", cleanName).await()
                } else {
                    phoneRef.set(mapOf("uid" to uid, "displayName" to cleanName)).await()
                }
            }

            db.collection("users").document(uid)
                .collection("private").document("profile")
                .set(
                    mapOf(
                        "phoneNumber" to cleanPhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Result.success(UserProfile(uid, cleanName, shortId, cleanAbout) to cleanPhone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "profiles/$uid")
            Result.failure(e)
        }
    }

    fun observePeerProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (!ChatboxConstants.VALID_ID_REGEX.matches(uid)) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = db.collection("profiles").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, "profiles/$uid")
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    trySend(
                        UserProfile(
                            uid = snap.getString("uid") ?: uid,
                            displayName = snap.getString("displayName") ?: "Chatbox User",
                            shortId = snap.getString("shortId").orEmpty(),
                            about = snap.getString("about").orEmpty()
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "profiles/$uid")
    }

    fun observeUserConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val reg = db.collection("conversations")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().map { doc ->
                    val participants = (doc.get("participants") as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    Conversation(
                        id = doc.id,
                        participants = participants,
                        createdBy = doc.getString("createdBy").orEmpty(),
                        status = doc.getString("status") ?: "pending",
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { conv ->
                    conv.updatedAt?.toDate()?.time ?: conv.createdAt?.toDate()?.time ?: 0L
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "conversations")
        throw e
    }

    fun observeBlockedPeerIds(uid: String): Flow<Set<String>> = callbackFlow {
        val reg = db.collection("userBlocks").document(uid).collection("blocked")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "userBlocks/$uid/blocked")
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { it.id }?.toSet().orEmpty())
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "userBlocks/$uid/blocked")
    }

    fun observeConversationMessages(
        conversationId: String,
        messageLimit: Long = 100L
    ): Flow<Pair<List<LiveMessage>, DocumentSnapshot?>> = callbackFlow {
        val path = "conversations/$conversationId/messages"
        val q = db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(messageLimit)

        val reg = q.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val docs = snap?.documents.orEmpty()
            val items = docs.map { d -> d.toLiveMessage() }
            trySend(items to docs.firstOrNull())
        }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "conversations/$conversationId/messages")
        throw e
    }

    suspend fun fetchOlderMessages(
        conversationId: String,
        beforeSnapshot: DocumentSnapshot,
        pageSize: Long = 50L
    ): Result<Pair<List<LiveMessage>, DocumentSnapshot?>> {
        val path = "conversations/$conversationId/messages"
        return try {
            val snap = db.collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .endBefore(beforeSnapshot)
                .limitToLast(pageSize)
                .get()
                .await()
            val docs = snap.documents
            Result.success(docs.map { it.toLiveMessage() } to docs.firstOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun lookupPeerByShortId(shortId: String): UserProfile? {
        val compact = shortId.trim().removePrefix("#").replace(Regex("\\s+"), "")
        if (!ChatboxConstants.VALID_SHORT_ID_REGEX.matches(compact)) return null
        return try {
            val shortSnap = db.collection("shortIds").document(compact).get(Source.SERVER).await()
            if (!shortSnap.exists()) return null
            val peerUid = shortSnap.getString("uid").orEmpty()
            val fallbackName = shortSnap.getString("displayName").orEmpty()
            if (!ChatboxConstants.VALID_ID_REGEX.matches(peerUid)) return null
            val profileSnap = runCatching {
                db.collection("profiles").document(peerUid).get(Source.SERVER).await()
            }.getOrNull()
            val name = profileSnap?.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: fallbackName.ifBlank { "Chatbox Friend ($compact)" }
            val about = profileSnap?.getString("about").orEmpty()
            UserProfile(uid = peerUid, displayName = name, shortId = compact, about = about)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun startOrOpenConversation(currentUid: String, rawIdentifier: String): Result<String> {
        val trimmed = rawIdentifier.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a 6-digit Chatbox ID or phone number."))
        }
        val compactShortCandidate = trimmed.removePrefix("#").replace(Regex("\\s+"), "")

        return try {
            var peerUid = ""
            if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(compactShortCandidate)) {
                val shortSnap = db.collection("shortIds").document(compactShortCandidate).get(Source.SERVER).await()
                if (!shortSnap.exists()) {
                    return Result.failure(IllegalArgumentException("No user found with 6-digit ID \"$compactShortCandidate\"."))
                }
                peerUid = shortSnap.getString("uid").orEmpty()
            } else {
                val looksLikePhone = Regex("^[\\d+\\-\\s()]{7,24}$").matches(trimmed)
                val phoneCandidates = if (looksLikePhone) buildPhoneLookupCandidates(trimmed) else emptyList()
                if (phoneCandidates.isNotEmpty()) {
                    for (candidate in phoneCandidates) {
                        val phoneSnap = db.collection("phoneDirectory").document(candidate).get(Source.SERVER).await()
                        if (phoneSnap.exists()) {
                            peerUid = phoneSnap.getString("uid").orEmpty()
                            break
                        }
                    }
                    if (peerUid.isEmpty()) {
                        return Result.failure(
                            IllegalArgumentException("No Chatbox account is linked to phone number \"$trimmed\".")
                        )
                    }
                } else if (ChatboxConstants.VALID_ID_REGEX.matches(trimmed)) {
                    peerUid = trimmed
                } else {
                    return Result.failure(
                        IllegalArgumentException("Enter a valid 6-digit ID code (e.g. 482910) or phone number.")
                    )
                }
            }

            if (peerUid == currentUid) {
                return Result.failure(IllegalArgumentException("You cannot start a conversation with yourself."))
            }

            val peerSnap = db.collection("profiles").document(peerUid).get(Source.SERVER).await()
            if (!peerSnap.exists()) {
                return Result.failure(IllegalArgumentException("No Chatbox account was found for that ID yet."))
            }

            val conversationId = buildConversationId(currentUid, peerUid)
            val convRef = db.collection("conversations").document(conversationId)
            val existing = convRef.get().await()
            if (!existing.exists()) {
                val participants = listOf(currentUid, peerUid).sorted()
                convRef.set(
                    mapOf(
                        "participants" to participants,
                        "createdBy" to currentUid,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "conversations")
            Result.failure(e)
        }
    }

    suspend fun updateConversationStatus(conversationId: String, status: String): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendLiveMessage(
        conversationId: String,
        senderId: String,
        rawBody: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationMs: Long? = null
    ): Result<Unit> {
        val body = rawBody.trim().take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        val cleanImage = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val cleanAudio = audioUrl?.trim()?.takeIf { it.isNotEmpty() }
        val cleanDuration = if (cleanAudio != null && audioDurationMs != null) {
            audioDurationMs.coerceIn(0L, 300_000L)
        } else null

        if (body.isEmpty() && cleanImage == null && cleanAudio == null) {
            return Result.failure(IllegalArgumentException("Cannot send an empty message."))
        }

        val payload = buildMap<String, Any> {
            put("senderId", senderId)
            put("body", body)
            put("sentAt", FieldValue.serverTimestamp())
            if (cleanImage != null) put("imageUrl", cleanImage)
            if (cleanAudio != null) {
                put("audioUrl", cleanAudio)
                if (cleanDuration != null) put("audioDurationMs", cleanDuration)
            }
        }

        val path = "conversations/$conversationId/messages"
        return try {
            db.collection("conversations").document(conversationId)
                .collection("messages")
                .add(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun uploadChatImage(
        conversationId: String,
        senderId: String,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = compressUriToJpeg(imageUri)
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected image."))
            if (bytes.size > 9.5 * 1024 * 1024) {
                return@withContext Result.failure(IllegalArgumentException("Compressed image exceeds 10 MB limit."))
            }
            val fileName = "img_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.jpg"
            val ref = storage.reference.child("chat_media/$conversationId/$senderId/$fileName")
            val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(bytes, meta).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatVoiceNote(
        conversationId: String,
        senderId: String,
        audioFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Recorded audio file is empty."))
            }
            if (audioFile.length() > 9.5 * 1024 * 1024) {
                return@withContext Result.failure(IllegalArgumentException("Voice note exceeds 10 MB limit."))
            }
            val fileName = "voice_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.m4a"
            val ref = storage.reference.child("chat_media/$conversationId/$senderId/$fileName")
            val meta = StorageMetadata.Builder().setContentType("audio/mp4").build()
            ref.putFile(Uri.fromFile(audioFile), meta).await()
            val url = ref.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockPeerUser(
        currentUid: String,
        peerUid: String,
        conversation: Conversation?
    ): Result<Unit> {
        val path = "userBlocks/$currentUid/blocked/$peerUid"
        return try {
            val batch = db.batch()
            batch.set(
                db.collection("userBlocks").document(currentUid)
                    .collection("blocked").document(peerUid),
                mapOf("createdAt" to FieldValue.serverTimestamp())
            )
            if (conversation != null && (conversation.status == "pending" || conversation.status == "active")) {
                batch.update(
                    db.collection("conversations").document(conversation.id),
                    mapOf(
                        "status" to "closed",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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

    suspend fun unblockPeerUser(currentUid: String, peerUid: String): Result<Unit> {
        val path = "userBlocks/$currentUid/blocked/$peerUid"
        return try {
            db.collection("userBlocks").document(currentUid)
                .collection("blocked").document(peerUid)
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

    private fun compressUriToJpeg(uri: Uri): ByteArray? {
        val cr = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sample = 1
        while (maxSide / sample > MAX_IMAGE_DIMENSION * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return null

        val scale = min(1f, MAX_IMAGE_DIMENSION.toFloat() / max(decoded.width, decoded.height))
        val targetW = max(1, (decoded.width * scale).roundToInt())
        val targetH = max(1, (decoded.height * scale).roundToInt())
        val scaled = if (targetW != decoded.width || targetH != decoded.height) {
            decoded.scale(targetW, targetH).also {
                if (it != decoded) decoded.recycle()
            }
        } else decoded

        return ByteArrayOutputStream().use { baos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            scaled.recycle()
            baos.toByteArray()
        }
    }

    private fun DocumentSnapshot.toLiveMessage(): LiveMessage =
        LiveMessage(
            id = id,
            senderId = getString("senderId").orEmpty(),
            body = getString("body").orEmpty(),
            imageUrl = getString("imageUrl"),
            audioUrl = getString("audioUrl"),
            audioDurationMs = getLong("audioDurationMs"),
            sentAt = getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            pendingWrites = metadata.hasPendingWrites()
        )
}
