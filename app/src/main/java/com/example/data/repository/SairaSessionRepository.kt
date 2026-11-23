package com.example.data.repository

import android.content.Context
import com.example.data.model.AiExchange
import com.example.data.model.SairaChatSession
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class SairaSessionRepository(context: Context) {
    private val db = FirebaseProviders.firestore(context)

    fun observeSessions(uid: String): Flow<List<SairaChatSession>> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .collection("sairaSessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "users/$uid/sairaSessions")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val sessions = snap?.documents.orEmpty().map { d ->
                    SairaChatSession(
                        id = d.id,
                        title = d.getString("title") ?: "Saira Chat",
                        lastPreview = d.getString("lastPreview").orEmpty(),
                        exchangeCount = d.getLong("exchangeCount")?.toInt() ?: 0,
                        createdAt = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                        updatedAt = d.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }
                trySend(sessions)
            }
        awaitClose { reg.remove() }
    }.catch { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users/$uid/sairaSessions")
    }

    suspend fun loadExchanges(uid: String, sessionId: String): Result<List<AiExchange>> {
        val path = "users/$uid/sairaSessions/$sessionId/exchanges"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("sairaSessions").document(sessionId)
                .collection("exchanges")
                .orderBy("sequence", Query.Direction.ASCENDING)
                .get()
                .await()
            val list = snap.documents.map { d ->
                AiExchange(
                    id = d.id,
                    question = d.getString("question").orEmpty(),
                    answer = d.getString("answer").orEmpty(),
                    isStreaming = false
                )
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun persistExchange(
        uid: String,
        sessionId: String,
        isNewSession: Boolean,
        title: String,
        question: String,
        answer: String,
        sequence: Int
    ): Result<Unit> {
        val sessionPath = "users/$uid/sairaSessions/$sessionId"
        return try {
            val batch = db.batch()
            val sessionRef = db.collection("users").document(uid)
                .collection("sairaSessions").document(sessionId)
            val cleanTitle = title.trim().take(120).ifEmpty { "Saira Chat" }
            val preview = answer.trim().take(240)

            if (isNewSession) {
                batch.set(
                    sessionRef,
                    mapOf(
                        "title" to cleanTitle,
                        "lastPreview" to preview,
                        "exchangeCount" to sequence + 1,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    sessionRef,
                    mapOf(
                        "title" to cleanTitle,
                        "lastPreview" to preview,
                        "exchangeCount" to sequence + 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val exId = "ex_${sequence}_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
            val exRef = sessionRef.collection("exchanges").document(exId)
            batch.set(
                exRef,
                mapOf(
                    "question" to question.trim().take(2000),
                    "answer" to answer.trim().take(12000),
                    "sequence" to sequence,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sessionPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(uid: String, sessionId: String): Result<Unit> {
        val path = "users/$uid/sairaSessions/$sessionId"
        return try {
            val sessionRef = db.collection("users").document(uid)
                .collection("sairaSessions").document(sessionId)
            val exchanges = sessionRef.collection("exchanges").get().await()
            val batch = db.batch()
            exchanges.documents.forEach { batch.delete(it.reference) }
            batch.delete(sessionRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
