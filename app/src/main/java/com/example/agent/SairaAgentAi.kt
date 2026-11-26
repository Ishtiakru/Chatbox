package com.example.agent

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.ai.GeminiKeyPool
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One inline attachment (photo / PDF / text / audio / video) sent to Gemini. */
data class AgentAttachment(val name: String, val mimeType: String, val bytes: ByteArray)

data class AgentTurn(val role: String, val text: String)

/**
 * Direct multimodal Gemini calls for Saira's admin agent: attachments
 * (Drive / gallery files), JSON-mode planning and live Google Search
 * grounding, rotating through the same key pool as normal Saira chat.
 */
class SairaAgentAi(context: Context) {
    private val pool = GeminiKeyPool(context.applicationContext)

    suspend fun generate(
        system: String,
        history: List<AgentTurn>,
        prompt: String,
        attachments: List<AgentAttachment> = emptyList(),
        json: Boolean = false,
        search: Boolean = false,
        maxTokens: Int = 2048
    ): String = withContext(Dispatchers.IO) {
        val keys = pool.availableKeys()
        if (keys.isEmpty()) throw IllegalStateException("Saira-র AI key পাওয়া যায়নি।")
        var lastError: Exception? = null
        var attempts = 0
        var useSearch = search
        for (model in MODELS) {
            for (key in keys.shuffled().take(KEYS_PER_MODEL)) {
                attempts++
                val body = buildBody(system, history, prompt, attachments, json, useSearch, maxTokens)
                val (code, raw) = try {
                    post("$BASE/$model:generateContent?key=$key", body)
                } catch (e: Exception) {
                    lastError = e
                    continue
                }
                if (code in 200..299) {
                    val text = extract(raw)
                    if (!text.isNullOrBlank()) return@withContext text
                    lastError = IllegalStateException("empty answer")
                    continue
                }
                val low = raw.lowercase()
                when {
                    // Search grounding not allowed for this key/model → retry without it.
                    code == 400 && useSearch && (low.contains("search") || low.contains("tool")) -> {
                        useSearch = false
                    }
                    code == 429 || low.contains("resource_exhausted") -> pool.markCooling(key, 60_000L)
                    code == 404 -> break // model missing → next model
                }
                lastError = IllegalStateException("Gemini HTTP $code: ${raw.take(160)}")
                Log.w(TAG, "model=$model code=$code")
            }
        }
        throw lastError ?: IllegalStateException("AI উত্তর দেয়নি ($attempts চেষ্টা)")
    }

    private fun buildBody(
        system: String,
        history: List<AgentTurn>,
        prompt: String,
        attachments: List<AgentAttachment>,
        json: Boolean,
        search: Boolean,
        maxTokens: Int
    ): String {
        val contents = JSONArray()
        history.forEach { t ->
            contents.put(
                JSONObject()
                    .put("role", if (t.role == "model") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val parts = JSONArray()
        attachments.forEach { a ->
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", a.mimeType)
                        .put("data", Base64.encodeToString(a.bytes, Base64.NO_WRAP))
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))
        contents.put(JSONObject().put("role", "user").put("parts", parts))
        val gen = JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.4)
        if (json) gen.put("responseMimeType", "application/json")
        val root = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gen)
        if (search && !json) {
            root.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        return root.toString()
    }

    private fun extract(raw: String): String? = runCatching {
        val cands = JSONObject(raw).optJSONArray("candidates") ?: return null
        if (cands.length() == 0) return null
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
        buildString {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought")) append(p.optString("text"))
            }
        }.trim()
    }.getOrNull()

    private fun post(url: String, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            return code to raw
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "SairaAgentAi"
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        const val KEYS_PER_MODEL = 4
        val MODELS = listOf("gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-3.8-flash")
    }
}
