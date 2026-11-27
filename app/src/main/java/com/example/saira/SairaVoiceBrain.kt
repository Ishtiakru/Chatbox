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
            put("generationConfig", JSONObject().put("maxOutputTokens", 300))
        }.toString()

        val keys = runCatching { pool.availableKeys() }.getOrDefault(emptyList()).take(6)
        for (model in MODELS) {
            for (key in keys) {
                val (code, raw) = post("$BASE/$model:generateContent?key=$key", body)
                when {
                    code in 200..299 -> return@withContext parseText(raw)?.let { cleanTranscript(it) }
                    code == 429 || raw.contains("RESOURCE_EXHAUSTED") -> pool.markCooling(key, 60_000L)
                    code == 404 -> break // model unavailable → next model
                    code >= 500 -> continue
                    else -> Log.w(TAG, "transcribe HTTP $code ${raw.take(160)}")
                }
            }
        }
        null
    }

    private fun post(url: String, body: String): Pair<Int, String> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6_000
            conn.readTimeout = 20_000
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
        private val MODELS = listOf("gemini-3.6-flash", GeminiAiProvider.DEFAULT_MODEL, "gemini-4-hydrogen")

        private const val SYSTEM =
            "You are a speech-to-text engine for a phone assistant named Saira (Bengali: সায়রা). " +
                "Write exactly what the speaker said, in the language spoken: Bengali speech in Bengali script, " +
                "English words in English. Always write the assistant's name as \"Saira\". " +
                "Output only the transcript — no quotes, labels or translation. If there is no clear speech, output nothing."

        fun cleanTranscript(t: String): String =
            t.trim().trim('"', '“', '”').replace(Regex("^(transcript|transcription)\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim()
    }
}
