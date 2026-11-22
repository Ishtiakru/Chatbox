package com.example.notifications

import android.content.Context
import com.example.R
import com.example.data.repository.OperationType
import com.example.data.repository.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class FcmTokenRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun syncCurrentToken(): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in before registering FCM token."))
        return try {
            val token = FirebaseMessaging.getInstance().token.await().trim()
            saveTokenInternal(uid, token)
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "userFcmTokens/$uid/devices")
            Result.failure(e)
        }
    }

    suspend fun saveTokenForCurrentUser(rawToken: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val token = rawToken.trim()
        if (token.isEmpty() || token.length > 512) {
            return Result.failure(IllegalArgumentException("Invalid FCM token."))
        }
        return try {
            saveTokenInternal(uid, token)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "userFcmTokens/$uid/devices")
            Result.failure(e)
        }
    }

    suspend fun unregisterCurrentToken(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val token = FirebaseMessaging.getInstance().token.await().trim()
            if (token.isEmpty()) return Result.success(Unit)
            val deviceId = sha256Hex(token)
            val path = "userFcmTokens/$uid/devices/$deviceId"
            try {
                db.collection("userFcmTokens").document(uid)
                    .collection("devices").document(deviceId)
                    .delete()
                    .await()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, path)
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveTokenInternal(uid: String, token: String) {
        require(token.isNotEmpty() && token.length <= 512) { "Invalid FCM token length." }
        val deviceId = sha256Hex(token)
        val path = "userFcmTokens/$uid/devices/$deviceId"
        try {
            db.collection("userFcmTokens").document(uid)
                .collection("devices").document(deviceId)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
