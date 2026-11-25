package com.example.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.repository.FirebaseProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Firestore-managed pool of Gemini API keys ("300/400 APIs, one limit ends →
 * the next API takes over").
 *
 * Keys live in `settings/aiKeyPool` (array field `keys`) inside the app's
 * Firestore database, so the pool can be grown or replaced WITHOUT rebuilding
 * the app: sync the document (see .github/workflows/sync-keypool.yml) and every
 * installed APK picks the new keys up within minutes.
 *
 * Per-key cooldowns are device-local: a key that hits its free-tier quota is
 * cooled down here and skipped until the cooldown expires; when every key is
 * cooling the provider delegates to the Firebase AI Logic fallback, so the
 * user-visible behavior stays "always answers, never an error".
 */
class GeminiKeyPool(context: Context?) {

    private val appContext: Context? = context?.applicationContext
        ?: runCatching { com.google.firebase.FirebaseApp.getInstance().applicationContext }.getOrNull()

    private val prefs: SharedPreferences? = appContext?.let { ctx ->
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private val cursor = AtomicInteger(0)

    @Volatile
    private var cachedKeys: List<String>? = null

    @Volatile
    private var loadedAtMs: Long = 0L

    /** Keys that are present and not cooling right now, load-balanced round-robin. */
    suspend fun availableKeys(): List<String> = withContext(Dispatchers.IO) {
        val keys = ensureLoaded()
        val cooling = coolingMap()
        val now = System.currentTimeMillis()
        KeyRotation.order(keys, cooling, now, cursor.getAndIncrement())
    }

    fun markCooling(key: String, durationMs: Long) {
        val editor = prefs?.edit() ?: return
        editor.putLong(hashOf(key), System.currentTimeMillis() + durationMs).apply()
        Log.w(TAG, "Gemini key ${key.take(6)}… cooled for ${durationMs / 1000}s")
    }

    /** Force a reload on the next call (used after an explicit sync). */
    fun invalidate() {
        cachedKeys = null
        loadedAtMs = 0L
    }

    private suspend fun ensureLoaded(): List<String> {
        val now = System.currentTimeMillis()
        val cached = cachedKeys
        if (cached != null && (now - loadedAtMs) < CACHE_TTL_MS) return cached
        val fresh = runCatching { fetchFromFirestore() }.getOrNull()
        if (fresh != null) {
            cachedKeys = fresh
            loadedAtMs = now
            return fresh
        }
        // Network/rules hiccup: keep serving the last known pool.
        return cached ?: emptyList()
    }

    private suspend fun fetchFromFirestore(): List<String>? {
        val ctx = appContext ?: return null
        val snap = FirebaseProviders.firestore(ctx)
            .collection("settings")
            .document(DOC_ID)
            .get()
            .await()
        if (!snap.exists) {
            Log.i(TAG, "settings/$DOC_ID not present yet — key pool empty")
            return emptyList()
        }
        val raw = snap.get("keys")
        val keys = (raw as? List<*>).orEmpty()
            .filterIsInstance<String>()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        Log.i(TAG, "Loaded ${keys.size} Gemini key(s) from settings/$DOC_ID")
        return keys
    }

    private fun coolingMap(): Map<String, Long> {
        val p = prefs ?: return emptyMap()
        val out = HashMap<String, Long>()
        for ((k, v) in p.all) {
            if (v is Long) out[k] = v
        }
        return out
    }

    private fun hashOf(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "GeminiKeyPool"
        const val DOC_ID = "aiKeyPool"
        private const val PREFS_NAME = "chatbox_gemini_key_cooldowns"
        private const val CACHE_TTL_MS = 5 * 60_000L

        /** Quota (429 / daily-limit) errors: skip the key for a while. */
        const val QUOTA_COOLDOWN_MS = 10 * 60_000L

        /** Invalid / revoked / permission-less keys: park for a day. */
        const val DEAD_KEY_COOLDOWN_MS = 24 * 60 * 60_000L
    }
}

/**
 * Pure rotation logic (unit-testable, no Android deps): drop cooling keys and
 * rotate the remaining ones so load spreads across the whole pool.
 */
object KeyRotation {
    fun order(
        keys: List<String>,
        cooling: Map<String, Long>,
        now: Long,
        cursor: Int
    ): List<String> {
        val available = keys.filter { (cooling[it] ?: 0L) <= now }
        if (available.isEmpty()) return emptyList()
        val size = available.size
        val start = ((cursor % size) + size) % size
        return available.drop(start) + available.take(start)
    }
}
