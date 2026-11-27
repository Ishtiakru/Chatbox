package com.example.saira

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.ai.GeminiAiProvider
import com.example.data.ai.GeminiKeyPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speech → text for voice mode using the same Gemini key pool as Saira chat.
 * Audio is only sent after the on-device voice match says it is the owner speaking.
 */
class SairaVoiceBrain(context: Context) {
    private val pool = GeminiKeyPool(context.applicationContext)

    /** Text of the speech ("" = no clear speech), or null when no model could be reached. */
    suspend fun transcribe(pcm: ShortArray): String? = withContext(Dispatchers.IO) {
        val wav = SairaVoiceprint.toWav(pcm)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64)))
                            .put(JSONObject().put("text", "Transcribe this."))
                    )
                )
            )
            put("generationConfig", JSONObject().put("maxOutputTokens", 400).put("temperature", 0))
        }.toString()

        // Measured live (CI probe, Nov 2026): flash-lite models transcribe Bengali/English in ~1 s,
        // while 3.6-flash / 4-hydrogen are mostly quota (429) / overloaded (503) on the free pool.
        val keys = runCatching { pool.availableKeys() }.getOrDefault(emptyList()).shuffled().take(8)
        if (keys.isEmpty()) { lastError = "no keys"; return@withContext null }
        val started = System.currentTimeMillis()
        var tries = 0
        for (model in MODELS) {
            var failures = 0
            for (key in keys) {
                if (System.currentTimeMillis() - started > BUDGET_MS || tries >= MAX_TRIES) break
                tries++
                val (code, raw) = post("$BASE/$model:generateContent?key=$key", body)
                when {
                    code in 200..299 -> {
                        lastError = null
                        return@withContext parseText(raw)?.let { cleanTranscript(it) }.orEmpty()
                    }
                    code == 404 || raw.contains("is not found", ignoreCase = true) -> break
                    code == 429 || raw.contains("RESOURCE_EXHAUSTED") -> {
                        // Only cool the shared pool for the model Saira chat also uses.
                        if (model == GeminiAiProvider.DEFAULT_MODEL) pool.markCooling(key, GeminiKeyPool.QUOTA_COOLDOWN_MS)
                        lastError = "quota"
                    }
                    else -> {
                        lastError = "HTTP $code"
                        Log.w(TAG, "transcribe $model HTTP $code ${raw.take(160)}")
                        if (++failures >= 2) break // overloaded / timing out → next model
                    }
                }
            }
        }
        null
    }

    /** Short reason for the last failed [transcribe] (for the status line). */
    @Volatile var lastError: String? = null
        private set

    private fun post(url: String, body: String): Pair<Int, String> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 9_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.readBytes()?.decodeToString().orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }.getOrElse { 0 to (it.message ?: "network") }

    private fun parseText(raw: String): String? = runCatching {
        val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.trim()
    }.getOrNull()

    companion object {
        private const val TAG = "SairaVoiceBrain"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private val MODELS = listOf(GeminiAiProvider.DEFAULT_MODEL, GeminiAiProvider.FALLBACK_MODEL, "gemini-3.6-flash")
        private const val BUDGET_MS = 18_000L
        private const val MAX_TRIES = 8

        private const val SYSTEM =
            "You are a speech-to-text engine for a phone assistant named Saira (Bengali: সায়রা). " +
                "Write exactly what the speaker said, in the language spoken: Bengali speech in Bengali script, " +
                "English words in English. Always write the assistant's name as \"Saira\". " +
                "Output only the transcript — no quotes, labels or translation. If there is no clear speech, output nothing."

        fun cleanTranscript(t: String): String =
            t.trim().trim('"', '“', '”').replace(Regex("^(transcript|transcription)\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim()
    }
}
