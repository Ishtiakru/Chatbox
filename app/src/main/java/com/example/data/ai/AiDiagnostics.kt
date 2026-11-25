package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.repository.FirebaseProviders
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Records the last reason Saira fell back to a friendly safety-net message into
 * `aiDiagnostics/{uid}`, so the maintainer can see the real cause from CI
 * (`firestore-rules.yml -f mode=diag`) without the user ever seeing an error.
 * Fire-and-forget, throttled, and never throws.
 */
object AiDiagnostics {
    private const val TAG = "AiDiagnostics"
    private const val MIN_INTERVAL_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var lastKeyCount: Int = -1

    @Volatile
    private var lastWriteAt = 0L

    fun report(stage: String, error: Throwable?) {
        val now = System.currentTimeMillis()
        if (now - lastWriteAt < MIN_INTERVAL_MS) return
        lastWriteAt = now
        scope.launch {
            runCatching {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching
                val ctx = FirebaseApp.getInstance().applicationContext
                val root = generateSequence(error) { it.cause }.take(4).toList()
                val classes = root.joinToString(" <- ") { it.javaClass.simpleName }.ifBlank { "none" }
                val msg = root.joinToString(" | ") { it.message.orEmpty() }.take(600)
                FirebaseProviders.firestore(ctx)
                    .collection("aiDiagnostics").document(uid)
                    .set(
                        mapOf(
                            "stage" to stage.take(40),
                            "errorClass" to classes.take(120),
                            "message" to msg,
                            "keyCount" to lastKeyCount,
                            "appVersion" to BuildConfig.VERSION_NAME.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }.onFailure { Log.w(TAG, "diagnostic write failed", it) }
        }
    }
}
