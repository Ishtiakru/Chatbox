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
    private val store = SecureStore(context.applicationContext)

    /** "provider/model" of the last answer (pro brain) or "gemini-free". */
    @Volatile var lastModel: String = ""
        private set

    /**
     * Strongest available brain first: the admin's own API keys (Claude / GPT / Gemini Pro …,
     * see [ProBrain]); live web facts come from Gemini's Google Search grounding. Falls back to the
     * free Gemini pool. [fast] = quick routing/translation work → cheaper fast models.
     */
    suspend fun generate(
        system: String,
        history: List<AgentTurn>,
        prompt: String,
        attachments: List<AgentAttachment> = emptyList(),
        json: Boolean = false,
        search: Boolean = false,
        maxTokens: Int = 2048,
        fast: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val pro = ProBrain.parse(store.get(ProBrain.STORE_KEY))
        if (pro.isNotEmpty()) {
            var p = prompt
            if (search && !json) {
                val findings = runCatching {
                    poolGenerate(
                        "You are a web research assistant. Use Google Search. List the up-to-date facts that answer " +
                            "the question, each with its source name and date. Max 250 words. No opinions.",
                        emptyList(), prompt.take(4000), emptyList(), json = false, search = true, maxTokens = 1200
                    )
                }.getOrNull()
                if (!findings.isNullOrBlank()) {
                    p = "$prompt\n\n[Live web search findings — use them for current facts:]\n$findings"
                }
            }
            val r = ProBrain.generate(pro, system, history, p, attachments, json, maxOf(maxTokens, 4096), fast)
            if (r != null) {
                lastModel = r.model
                return@withContext r.text
            }
            Log.w(TAG, "pro brain did not answer → free Gemini pool")
        }
        poolGenerate(system, history, prompt, attachments, json, search, maxTokens).also { lastModel = "gemini-free" }
    }

    private suspend fun poolGenerate(
        system: String,
        history: List<AgentTurn>,
        prompt: String,
        attachments: List<AgentAttachment>,
        json: Boolean,
        search: Boolean,
        maxTokens: Int
    ): String {
        val keys = pool.availableKeys()
        if (keys.isEmpty()) throw IllegalStateException("Saira-র AI key পাওয়া যায়নি।")
        var lastError: Exception? = null
        var attempts = 0
        var useSearch = search
        for (model in MODELS) {
            val modelKeys = keys.shuffled().take(KEYS_PER_MODEL)
            var i = 0
            var modelMissing = false
            while (i < modelKeys.size && !modelMissing) {
                val key = modelKeys[i]
                attempts++
                val body = buildBody(system, history, prompt, attachments, json, useSearch, maxTokens)
                val (code, raw) = try {
                    post("$BASE/$model:generateContent?key=$key", body)
                } catch (e: Exception) {
                    lastError = e
                    i++
                    continue
                }
                if (code in 200..299) {
                    val text = extract(raw)
                    if (!text.isNullOrBlank()) return text
                    lastError = IllegalStateException("empty answer")
                    i++
                    continue
                }
                val low = raw.lowercase()
                lastError = IllegalStateException("Gemini HTTP $code: ${raw.take(160)}")
                Log.w(TAG, "model=$model code=$code search=$useSearch")
                if (useSearch && (code == 429 || (code == 400 && (low.contains("search") || low.contains("tool"))))) {
                    // Google Search grounding has its own (often zero) free quota → same key, no search.
                    useSearch = false
                    continue
                }
                when {
                    code == 429 || low.contains("resource_exhausted") -> pool.markCooling(key, 60_000L)
                    code == 404 -> modelMissing = true
                }
                i++
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
