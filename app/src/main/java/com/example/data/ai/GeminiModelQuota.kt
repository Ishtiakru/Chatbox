package com.example.data.ai

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Gemini's free limits are per project **and per model**: a key whose daily quota for
 * gemini-3.5-flash-lite is used up still answers on gemini-3.1-flash-lite. So a limit answer only
 * parks that key for that one model (daily limit → until Google's reset at midnight Pacific time,
 * per-minute limit → ~1 minute) instead of cooling the key for every model — which used to empty
 * the whole pool after a few messages ("Saira এই মুহূর্তে উত্তর দিতে পারছে না").
 */
object GeminiModelQuota {

    /** Text models Saira's chat walks through, best first (CI pool health, Nov 2026). */
    val CHAT_MODELS: List<String> = listOf(
        GeminiAiProvider.DEFAULT_MODEL,      // gemini-3.5-flash-lite
        "gemini-3.1-flash-lite",
        "gemini-3-flash-preview",
        GeminiAiProvider.FALLBACK_MODEL,     // gemini-flash-lite-latest
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview"
    ).distinct()

    private const val MINUTE_COOLDOWN_MS = 65_000L
    /** This many daily-limit answers in a row → skip the model for a while (no time wasted on it). */
    private const val DAILY_RUN_TO_SKIP = 8
    private const val MODEL_SKIP_MS = 15 * 60_000L
    private const val MODEL_GONE_MS = 6 * 60 * 60_000L
    private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

    private val keyUntil = ConcurrentHashMap<String, Long>()
    private val dailyRun = ConcurrentHashMap<String, Int>()
    private val modelUntil = ConcurrentHashMap<String, Long>()

    fun isDailyLimit(raw: String): Boolean = raw.contains("PerDay", ignoreCase = true)

    fun usable(model: String, key: String, now: Long = System.currentTimeMillis()): Boolean =
        (keyUntil[slot(model, key)] ?: 0L) <= now

    fun modelUsable(model: String, now: Long = System.currentTimeMillis()): Boolean =
        (modelUntil[model] ?: 0L) <= now

    fun markLimit(model: String, key: String, daily: Boolean, now: Long = System.currentTimeMillis()) {
        keyUntil[slot(model, key)] = if (daily) nextDailyReset(now) else now + MINUTE_COOLDOWN_MS
        if (daily) {
            val run = dailyRun.merge(model, 1, Int::plus) ?: 1
            if (run >= DAILY_RUN_TO_SKIP) {
                modelUntil[model] = now + MODEL_SKIP_MS
                dailyRun[model] = 0
            }
        }
    }

    fun markOk(model: String) {
        dailyRun.remove(model)
    }

    fun markGone(model: String, now: Long = System.currentTimeMillis()) {
        modelUntil[model] = now + MODEL_GONE_MS
    }

    /** Next midnight in Google's quota time zone (Pacific). */
    fun nextDailyReset(now: Long): Long {
        val local = Instant.ofEpochMilli(now).atZone(PACIFIC)
        return local.toLocalDate().plusDays(1).atStartOfDay(PACIFIC).toInstant().toEpochMilli()
    }

    /** For tests. */
    fun reset() {
        keyUntil.clear()
        dailyRun.clear()
        modelUntil.clear()
    }

    private fun slot(model: String, key: String) = "$model|$key"
}
