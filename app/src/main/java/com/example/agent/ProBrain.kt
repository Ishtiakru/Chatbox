package com.example.agent

import android.util.Base64
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** One of the admin's own AI API keys (stored only on the admin's phone + GitHub secret). */
data class ProKey(val provider: String, val key: String, val model: String?)

/**
 * Saira's "pro brain": the strongest models the admin has API keys for
 * (Claude, GPT, Gemini Pro, OpenRouter, Grok, DeepSeek, Groq). Strongest first;
 * on "model not found" the next model is tried, then the provider's model list
 * is searched. Falls back to the free Gemini pool when nothing answers.
 */
object ProBrain {
    private const val TAG = "ProBrain"
    const val STORE_KEY = "pro_keys"
    const val SECRET_NAME = "SAIRA_PRO_KEYS"

    val ORDER = listOf("anthropic", "openai", "gemini", "openrouter", "xai", "deepseek", "groq")
    private val VISION = setOf("anthropic", "openai", "gemini", "openrouter", "xai")

    val LABELS = mapOf(
        "anthropic" to "Claude (Anthropic)", "openai" to "OpenAI GPT", "gemini" to "Google Gemini",
        "openrouter" to "OpenRouter", "xai" to "xAI Grok", "deepseek" to "DeepSeek", "groq" to "Groq"
    )

    private val BEST = mapOf(
        "anthropic" to listOf("claude-opus-5-5", "claude-fable-5-2", "claude-sonnet-5-5"),
        "openai" to listOf("gpt-6.2-astra", "gpt-6.1-sol", "gpt-5.5"),
        "gemini" to listOf("gemini-pro-latest", "gemini-3.1-pro-preview", "gemini-3.8-flash"),
        "openrouter" to listOf("anthropic/claude-opus-5-5", "openai/gpt-6.2-astra", "google/gemini-pro-latest"),
        "xai" to listOf("grok-5", "grok-4"),
        "deepseek" to listOf("deepseek-reasoner", "deepseek-chat"),
        "groq" to listOf("moonshotai/kimi-k2-instruct", "openai/gpt-oss-120b")
    )
    private val FAST = mapOf(
        "anthropic" to listOf("claude-haiku-5-5", "claude-sonnet-5-5"),
        "openai" to listOf("gpt-6.2-sol", "gpt-6.1-terra"),
        "gemini" to listOf("gemini-3.8-flash", "gemini-flash-latest"),
        "openrouter" to listOf("anthropic/claude-sonnet-5-5", "google/gemini-flash-latest"),
        "xai" to listOf("grok-4-fast", "grok-4"),
        "deepseek" to listOf("deepseek-flash", "deepseek-chat"),
        "groq" to listOf("openai/gpt-oss-120b")
    )
    private val DISCOVER = mapOf(
        "anthropic" to listOf("claude-fable", "claude-opus", "claude-sonnet"),
        "openai" to listOf("^gpt-6\\.\\d+-astra$", "^gpt-6", "^gpt-5\\.\\d+$"),
        "gemini" to listOf("gemini-\\d.*pro", "gemini-\\d.*flash$"),
        "openrouter" to listOf("^anthropic/claude-(opus|fable)", "^openai/gpt-6", "^google/gemini.*pro"),
        "xai" to listOf("^grok-\\d+(\\.\\d+)?$", "^grok"),
        "deepseek" to listOf("deepseek"),
        "groq" to listOf("kimi", "gpt-oss-120b", "llama")
    )
    private val URLS = mapOf(
        "openai" to "https://api.openai.com/v1", "openrouter" to "https://openrouter.ai/api/v1",
        "xai" to "https://api.x.ai/v1", "deepseek" to "https://api.deepseek.com",
        "groq" to "https://api.groq.com/openai/v1"
    )

    private val dead = ConcurrentHashMap.newKeySet<String>()
    private val good = ConcurrentHashMap<String, String>()
    private val discovered = ConcurrentHashMap<String, List<String>>()

    fun detect(rawKey: String): String? {
        val k = rawKey.trim()
        val prefix = k.substringBefore(':', "").lowercase()
        if (prefix.isNotEmpty() && prefix.length < 12 && prefix in ORDER) return prefix
        return when {
            k.startsWith("sk-ant-") -> "anthropic"
            k.startsWith("sk-or-") -> "openrouter"
            k.startsWith("AIza") || k.startsWith("AQ.") -> "gemini"
            k.startsWith("xai-") -> "xai"
            k.startsWith("gsk_") -> "groq"
            Regex("sk-[0-9a-f]{32}").matches(k) -> "deepseek"
            k.startsWith("sk-") -> "openai"
            else -> null
        }
    }

    /** One key per line, optionally followed by a model name ("sk-ant-… claude-fable-5-2"). */
    fun parse(raw: String?): List<ProKey> {
        val out = mutableListOf<ProKey>()
        raw.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            var key = parts[0]
            val provider = detect(key) ?: return@forEach
            if (key.lowercase().startsWith("$provider:")) key = key.substringAfter(':')
            if (key.length < 16 || out.any { it.key == key }) return@forEach
            out += ProKey(provider, key, parts.getOrNull(1))
        }
        return out.sortedBy { ORDER.indexOf(it.provider) }
    }

    fun serialize(keys: List<ProKey>): String = keys.joinToString("\n") { k ->
        val prefix = if (k.provider == "deepseek" && !Regex("sk-[0-9a-f]{32}").matches(k.key)) "deepseek:" else ""
        prefix + k.key + (k.model?.let { " $it" } ?: "")
    }

    fun mask(key: String): String = if (key.length <= 12) "••••" else key.take(7) + "…" + key.takeLast(4)

    private fun canHandle(k: ProKey, attachments: List<AgentAttachment>): Boolean = attachments.all { a ->
        when {
            a.mimeType.startsWith("text/") || a.mimeType == "application/json" -> true
            a.mimeType.startsWith("image/") -> k.provider in VISION
            a.mimeType == "application/pdf" -> k.provider == "anthropic" || k.provider == "gemini"
            else -> k.provider == "gemini"
        }
    }

    class ProResult(val text: String, val model: String)

    /** Blocking (call on Dispatchers.IO). Returns null when no pro key could answer. */
    fun generate(
        keys: List<ProKey>,
        system: String,
        history: List<AgentTurn>,
        prompt: String,
        attachments: List<AgentAttachment> = emptyList(),
        json: Boolean = false,
        maxTokens: Int = 4096,
        fast: Boolean = false
    ): ProResult? {
        val sys = if (json) "$system\nAnswer with ONE valid JSON object only — no prose, no markdown fences." else system
        for (k in keys) {
            if (!canHandle(k, attachments)) continue
            val models = modelsFor(k, fast).toMutableList()
            var discoveredTried = false
            var i = 0
            providerLoop@ while (i < models.size) {
                val model = models[i++]
                var attempt = 0
                while (attempt < 2) {
                    attempt++
                    val (code, raw) = try {
                        call(k, model, sys, history, prompt, attachments, maxTokens)
                    } catch (e: Exception) {
                        Log.w(TAG, "${k.provider}/$model: ${e.javaClass.simpleName}")
                        continue
                    }
                    if (code in 200..299) {
                        val text = extract(k.provider, raw)?.trim()
                        if (!text.isNullOrBlank()) {
                            good.putIfAbsent(k.key + fast, model)
                            return ProResult(if (json) cleanJson(text) else text, "${k.provider}/$model")
                        }
                        continue
                    }
                    val low = raw.lowercase()
                    Log.w(TAG, "${k.provider}/$model HTTP $code ${raw.take(120)}")
                    val modelMissing = code == 404 || (code == 400 && "model" in low &&
                        listOf("not found", "does not exist", "invalid model", "not supported", "unknown").any { it in low })
                    if (modelMissing) {
                        dead += k.key + "|" + model
                        break
                    }
                    if (code == 401 || code == 402 || code == 403) break@providerLoop
                    if (code == 429 || code >= 500) Thread.sleep(2_500L * attempt) else break
                }
                if (i >= models.size && !discoveredTried) {
                    discoveredTried = true
                    models += discover(k).filter { it !in models && (k.key + "|" + it) !in dead }
                }
            }
        }
        return null
    }

    private fun modelsFor(k: ProKey, fast: Boolean): List<String> {
        val list = mutableListOf<String>()
        good[k.key + fast]?.let { list += it }
        if (!fast) k.model?.let { list += it }
        list += (if (fast) FAST else BEST)[k.provider].orEmpty()
        return list.distinct().filter { (k.key + "|" + it) !in dead }
    }

    private fun cleanJson(t: String): String {
        val s = t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (s.startsWith("{")) return s
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        return if (a >= 0 && b > a) s.substring(a, b + 1) else s
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun textOf(a: AgentAttachment) =
        "\n\n=== Attached file: ${a.name} ===\n" + a.bytes.toString(Charsets.UTF_8).take(200_000)

    private fun call(
        k: ProKey, model: String, system: String, history: List<AgentTurn>, prompt: String,
        attachments: List<AgentAttachment>, maxTokens: Int
    ): Pair<Int, String> {
        val textFiles = attachments.filter { it.mimeType.startsWith("text/") || it.mimeType == "application/json" }
        val binary = attachments - textFiles.toSet()
        val fullPrompt = prompt + textFiles.joinToString("") { textOf(it) }
        return when (k.provider) {
            "anthropic" -> {
                val msgs = JSONArray()
                history.forEach { t ->
                    msgs.put(JSONObject().put("role", if (t.role == "model") "assistant" else "user").put("content", t.text))
                }
                val content = JSONArray()
                binary.forEach { a ->
                    val type = if (a.mimeType == "application/pdf") "document" else "image"
                    content.put(
                        JSONObject().put("type", type).put(
                            "source", JSONObject().put("type", "base64").put("media_type", a.mimeType).put("data", b64(a.bytes))
                        )
                    )
                }
                content.put(JSONObject().put("type", "text").put("text", fullPrompt))
                msgs.put(JSONObject().put("role", "user").put("content", content))
                val body = JSONObject().put("model", model).put("max_tokens", maxTokens)
                    .put("system", system).put("messages", fixAlternation(msgs))
                post(
                    "https://api.anthropic.com/v1/messages", body.toString(),
                    mapOf("x-api-key" to k.key, "anthropic-version" to "2023-06-01")
                )
            }
            "gemini" -> {
                val contents = JSONArray()
                history.forEach { t ->
                    contents.put(
                        JSONObject().put("role", if (t.role == "model") "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                    )
                }
                val parts = JSONArray()
                binary.forEach { a ->
                    parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", a.mimeType).put("data", b64(a.bytes))))
                }
                parts.put(JSONObject().put("text", fullPrompt))
                contents.put(JSONObject().put("role", "user").put("parts", parts))
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
                post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                    body.toString(), mapOf("x-goog-api-key" to k.key)
                )
            }
            else -> {
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                history.forEach { t ->
                    msgs.put(JSONObject().put("role", if (t.role == "model") "assistant" else "user").put("content", t.text))
                }
                val user: Any = if (binary.isNotEmpty()) {
                    JSONArray().also { arr ->
                        binary.forEach { a ->
                            arr.put(
                                JSONObject().put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", "data:${a.mimeType};base64,${b64(a.bytes)}"))
                            )
                        }
                        arr.put(JSONObject().put("type", "text").put("text", fullPrompt))
                    }
                } else fullPrompt
                msgs.put(JSONObject().put("role", "user").put("content", user))
                val body = JSONObject().put("model", model).put("messages", msgs)
                    .put(if (k.provider == "openai") "max_completion_tokens" else "max_tokens", maxTokens)
                val headers = mutableMapOf("Authorization" to "Bearer ${k.key}")
                if (k.provider == "openrouter") {
                    headers["HTTP-Referer"] = "https://github.com/Ishtiakru/Chatbox"
                    headers["X-Title"] = "Chatbox Saira"
                }
                post("${URLS[k.provider]}/chat/completions", body.toString(), headers)
            }
        }
    }

    /** Anthropic needs strictly alternating user/assistant turns starting with user. */
    private fun fixAlternation(msgs: JSONArray): JSONArray {
        val out = JSONArray()
        var lastRole = ""
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            val role = m.getString("role")
            if (out.length() == 0 && role != "user") continue
            if (role == lastRole) {
                val prev = out.getJSONObject(out.length() - 1)
                val pc = prev.get("content")
                val mc = m.get("content")
                if (pc is String && mc is String) {
                    prev.put("content", "$pc\n\n$mc")
                } else {
                    val arr = JSONArray()
                    listOf(pc, mc).forEach { c ->
                        if (c is JSONArray) for (j in 0 until c.length()) arr.put(c.get(j))
                        else arr.put(JSONObject().put("type", "text").put("text", c.toString()))
                    }
                    prev.put("content", arr)
                }
                continue
            }
            out.put(m)
            lastRole = role
        }
        return out
    }

    private fun extract(provider: String, raw: String): String? = runCatching {
        val o = JSONObject(raw)
        when (provider) {
            "anthropic" -> {
                val c = o.optJSONArray("content") ?: return null
                buildString {
                    for (i in 0 until c.length()) {
                        val b = c.getJSONObject(i)
                        if (b.optString("type") == "text") append(b.optString("text"))
                    }
                }
            }
            "gemini" -> {
                val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                buildString {
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (!p.optBoolean("thought")) append(p.optString("text"))
                    }
                }
            }
            else -> {
                val msg = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: return null
                when (val c = msg.opt("content")) {
                    is String -> c
                    is JSONArray -> buildString {
                        for (i in 0 until c.length()) append(c.optJSONObject(i)?.optString("text").orEmpty())
                    }
                    else -> null
                }
            }
        }
    }.getOrNull()

    private fun discover(k: ProKey): List<String> = discovered.getOrPut(k.key) {
        val names = runCatching {
            val (code, raw) = when (k.provider) {
                "anthropic" -> get("https://api.anthropic.com/v1/models?limit=100",
                    mapOf("x-api-key" to k.key, "anthropic-version" to "2023-06-01"))
                "gemini" -> get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                    mapOf("x-goog-api-key" to k.key))
                else -> get("${URLS[k.provider]}/models", mapOf("Authorization" to "Bearer ${k.key}"))
            }
            if (code !in 200..299) return@runCatching emptyList<String>()
            val o = JSONObject(raw)
            val arr = o.optJSONArray("data") ?: o.optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.getJSONObject(i)
                (m.optString("id").ifBlank { m.optString("name").substringAfter("models/") }).ifBlank { null }
            }
        }.getOrDefault(emptyList())
        val picked = mutableListOf<String>()
        DISCOVER[k.provider].orEmpty().forEach { pat ->
            val re = Regex(pat)
            names.filter { re.containsMatchIn(it) && !Regex("embed|image|audio|tts").containsMatchIn(it) }
                .sortedDescending().filter { it !in picked }.take(2).forEach { picked += it }
        }
        picked.take(4)
    }

    /** Quick health check of one key → "OK (model)" or the error. */
    fun test(k: ProKey): String {
        val r = generate(listOf(k), "Reply with exactly: OK", emptyList(), "Say OK", maxTokens = 64, fast = true)
            ?: generate(listOf(k), "Reply with exactly: OK", emptyList(), "Say OK", maxTokens = 64)
        return if (r != null) "✅ ${r.model}" else "❌ উত্তর পাইনি (key ভুল, credit নেই বা model বন্ধ)"
    }

    private fun post(url: String, body: String, headers: Map<String, String>): Pair<Int, String> =
        http("POST", url, body, headers)

    private fun get(url: String, headers: Map<String, String>): Pair<Int, String> = http("GET", url, null, headers)

    private fun http(method: String, url: String, body: String?, headers: Map<String, String>): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            return code to raw
        } finally {
            conn.disconnect()
        }
    }
}
