package com.example.data.ai

import android.content.Context
import com.google.firebase.FirebaseApp
import java.util.concurrent.ConcurrentHashMap

interface AiCooldownBackend {
    fun get(providerId: String): Long
    fun set(providerId: String, cooldownUntilMs: Long)
    fun clear()
}

class InMemoryAiCooldownBackend : AiCooldownBackend {
    private val values = ConcurrentHashMap<String, Long>()

    override fun get(providerId: String): Long = values[providerId] ?: 0L

    override fun set(providerId: String, cooldownUntilMs: Long) {
        values[providerId] = cooldownUntilMs
    }

    override fun clear() {
        values.clear()
    }
}

object SharedPreferencesAiCooldownBackend : AiCooldownBackend {
    private const val PREFS_NAME = "chatbox_ai_cooldowns"

    private fun prefs() = runCatching {
        FirebaseApp.getInstance().applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    override fun get(providerId: String): Long = prefs()?.getLong(providerId, 0L) ?: 0L

    override fun set(providerId: String, cooldownUntilMs: Long) {
        prefs()?.edit()?.putLong(providerId, cooldownUntilMs)?.apply()
    }

    override fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }
}

/** Remembers which providers are temporarily out of free quota. */
class AiCooldownStore(
    private val backend: AiCooldownBackend = SharedPreferencesAiCooldownBackend
) {
    fun isCooling(providerId: String, nowMs: Long): Boolean = backend.get(providerId) > nowMs

    fun mark(providerId: String, cooldownUntilMs: Long) {
        backend.set(providerId, cooldownUntilMs)
    }

    fun cooldownRemainingMs(providerId: String, nowMs: Long): Long =
        (backend.get(providerId) - nowMs).coerceAtLeast(0L)

    fun clearAll() {
        backend.clear()
    }
}
