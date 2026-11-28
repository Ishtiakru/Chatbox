package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Primary Saira provider: Gemini via direct REST with a Firestore-managed key
 * pool ([GeminiKeyPool]).
 *
 * Behavior per request:
 *  1. Take the currently available (non-cooling) keys from the pool, round-robin.
 *  2. Try them in order; a quota/limit answer cools that key and moves to the
 *     next one ("aktar limit ses hole arek api te shift hobe") — the user never
 *     sees the limit.
 *  3. If the pool is empty/unavailable, or every attempted key is dead, delegate
 *     to [delegate] (the existing Firebase AI Logic provider) so behavior is
 *     identical to today until keys are provisioned.
 *  4. Errors from the delegate propagate to [AiRouter], which already shifts to
 *     the user-configured backup and, in neverThrow mode, to a friendly reply —
 *     Saira never shows an error either way.
 */
class PooledGeminiProvider(
    private val pool: GeminiKeyPool,
    private val delegate: AiProvider,
    override val id: String = ID
) : AiProvider {

    override suspend fun generate(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String = generateInternal(systemInstruction, history, prompt, emptyList())

    /**
     * Same model/key ladder as [generate], with pictures attached to the last user turn
     * (all CHAT_MODELS are multimodal). Throws when no key/model could answer.
     */
    suspend fun generateWithImages(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String,
        images: List<AiImage>
    ): String = generateInternal(systemInstruction, history, prompt, images)

    private suspend fun generateInternal(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String,
        images: List<AiImage>
    ): String = withContext(Dispatchers.IO) {
        val keys = pool.availableKeys()
        if (keys.isEmpty()) {
            if (images.isNotEmpty()) throw AiUnavailableException("no Gemini keys for image analysis")
            return@withContext delegate.generate(systemInstruction, history, prompt)
        }
        val startedAt = System.currentTimeMillis()
        var attempts = 0
        models@ for (model in GeminiModelQuota.CHAT_MODELS) {
            if (!GeminiModelQuota.modelUsable(model)) continue
            var tried = 0
            var transientFailures = 0
            for (key in keys) {
                if (tried >= PER_MODEL_ATTEMPTS) break
                if (attempts >= MAX_KEY_ATTEMPTS || System.currentTimeMillis() - startedAt > POOL_BUDGET_MS) break@models
                if (!GeminiModelQuota.usable(model, key)) continue
                tried++
                attempts++
                val outcome = try {
                    tryKey(key, model, systemInstruction, history, prompt, images)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Attempt.Transient
                }
                when (outcome) {
                    is Attempt.Ok -> {
                        GeminiModelQuota.markOk(model)
                        return@withContext outcome.text
                    }
                    // A limit only parks this key for THIS model — it still works on the others.
                    is Attempt.Quota -> GeminiModelQuota.markLimit(model, key, outcome.daily)
                    is Attempt.DeadKey -> pool.markCooling(key, GeminiKeyPool.DEAD_KEY_COOLDOWN_MS)
                    is Attempt.ModelGone -> {
                        GeminiModelQuota.markGone(model)
                        continue@models
                    }
                    is Attempt.Transient, is Attempt.Other -> {
                        // 5xx / hang / odd reply: model-side trouble — after a couple, try the next model.
                        if (outcome is Attempt.Other) Log.w(TAG, "REST attempt failed on $model; rotating", outcome.error)
                        transientFailures++
                        if (transientFailures >= MODEL_SWITCH_AFTER_TRANSIENTS) continue@models
                    }
                }
            }
        }
        if (images.isNotEmpty()) throw AiUnavailableException("image analysis: every model/key is busy")
        delegate.generate(systemInstruction, history, prompt)
    }

    override fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = channelFlow {
        val keys = pool.availableKeys()
        if (keys.isEmpty()) {
            delegate.stream(systemInstruction, history, prompt).collect { send(it) }
            return@channelFlow
        }
        var attempts = 0
        var handled = false
        var lastStreamError: Throwable? = null
        val startedAt = System.currentTimeMillis()
        models@ for (model in GeminiModelQuota.CHAT_MODELS) {
            if (!GeminiModelQuota.modelUsable(model)) continue
            var tried = 0
            var transientFailures = 0
            for (key in keys) {
                if (tried >= PER_MODEL_ATTEMPTS) break
                if (attempts >= MAX_KEY_ATTEMPTS || System.currentTimeMillis() - startedAt > POOL_BUDGET_MS) break@models
                if (!GeminiModelQuota.usable(model, key)) continue
                tried++
                attempts++
                var emitted = false
                try {
                    withContext(Dispatchers.IO) {
                        streamKey(key, model, systemInstruction, history, prompt).collect {
                            emitted = true
                            send(it)
                        }
                    }
                    GeminiModelQuota.markOk(model)
                    handled = true
                    break@models
                } catch (e: CancellationException) {
                    throw e
                } catch (e: KeyQuotaException) {
                    GeminiModelQuota.markLimit(model, key, e.daily)
                } catch (e: KeyDeadException) {
                    pool.markCooling(key, GeminiKeyPool.DEAD_KEY_COOLDOWN_MS)
                } catch (e: ModelGoneException) {
                    GeminiModelQuota.markGone(model)
                    continue@models
                } catch (e: Throwable) {
                    // Part of the answer is already on screen — don't start a second answer after it.
                    if (emitted) {
                        handled = true
                        break@models
                    }
                    if (e !is KeyTransientException && e !is java.net.SocketTimeoutException) {
                        Log.w(TAG, "SSE attempt failed on $model; rotating key", e)
                        lastStreamError = e
                    }
                    transientFailures++
                    if (transientFailures >= MODEL_SWITCH_AFTER_TRANSIENTS) continue@models
                }
            }
        }
        if (!handled) {
            if (lastStreamError != null) AiDiagnostics.report("poolStream", lastStreamError)
            delegate.stream(systemInstruction, history, prompt).collect { send(it) }
        }
    }

    // ---- REST plumbing ----------------------------------------------------

    private sealed interface Attempt {
        data class Ok(val text: String) : Attempt
        data class Quota(val daily: Boolean) : Attempt
        object DeadKey : Attempt
        object ModelGone : Attempt
        object Transient : Attempt
        data class Other(val error: Throwable) : Attempt
    }

    private class KeyQuotaException(val daily: Boolean) : Exception("quota")
    private class KeyDeadException : Exception("invalid api key")
    private class ModelGoneException : Exception("model not found")
    private class KeyTransientException : Exception("transient upstream")

    private fun requestJson(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String,
        images: List<AiImage> = emptyList()
    ): String = JSONObject().apply {
        put("system_instruction", JSONObject().put("parts", JSONArray().put(
            JSONObject().put("text", systemInstruction)
        )))
        val contents = JSONArray()
        for (m in history) contents.put(contentJson(m))
        val last = contentJson(AiChatMessage("user", prompt))
        if (images.isNotEmpty()) {
            val parts = JSONArray()
            for (img in images) {
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", img.mimeType).put("data", img.base64)))
            }
            parts.put(JSONObject().put("text", prompt))
            last.put("parts", parts)
        }
        contents.put(last)
        put("contents", contents)
        put(
            "generationConfig",
            // Pictures (prescriptions, documents…) need room for a full, careful analysis.
            JSONObject().put("maxOutputTokens", if (images.isNotEmpty()) 2048 else 1024)
                .put("temperature", if (images.isNotEmpty()) 0.3 else 0.7)
        )
    }.toString()

    private fun tryKey(
        key: String,
        model: String,
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String,
        images: List<AiImage> = emptyList()
    ): Attempt {
        val body = requestJson(systemInstruction, history, prompt, images)
        val (status, raw) = post(
            "$BASE/$model:generateContent?key=$key", body,
            if (images.isNotEmpty()) VISION_READ_TIMEOUT_MS else GENERATE_READ_TIMEOUT_MS
        )
        classify(status, raw)?.let { return it }
        val text = parseText(raw)
            ?: return Attempt.Other(RuntimeException("No candidates in reply (status=$status)"))
        return Attempt.Ok(text)
    }

    private fun streamKey(
        key: String,
        model: String,
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = flow {
        val body = requestJson(systemInstruction, history, prompt)
        val conn = open("$BASE/$model:streamGenerateContent?alt=sse&key=$key", body)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.readBytes()?.decodeToString().orEmpty()
                throw classifyToException(code, err)
            }
            var any = false
            val reader: BufferedReader = conn.inputStream.bufferedReader()
            reader.useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    // keep the piece's own spaces / line breaks — trimming each piece glued words together
                    val text = parseText(payload, trim = false) ?: continue
                    if (text.isNotEmpty()) {
                        any = true
                        emit(text)
                    }
                }
            }
            if (!any) throw RuntimeException("Empty SSE stream")
        } finally {
            conn.disconnect()
        }
    }

    private fun contentJson(m: AiChatMessage): JSONObject {
        val role = if (m.role == "model" || m.role == "assistant") "model" else "user"
        return JSONObject()
            .put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
    }

    private fun classify(status: Int, raw: String): Attempt? {
        val lower = raw.lowercase()
        return when {
            status == 429 ||
                lower.contains("resource_exhausted") ||
                lower.contains("resource exhausted") ||
                lower.contains("rate limit") ||
                lower.contains("quota") -> Attempt.Quota(GeminiModelQuota.isDailyLimit(raw))
            lower.contains("api_key_invalid") ||
                lower.contains("api key not valid") ||
                lower.contains("api_key_not_valid") ||
                (status == 403 && lower.contains("perm")) -> Attempt.DeadKey
            status == 404 || lower.contains("model not found") ||
                lower.contains("is not found") -> Attempt.ModelGone
            status >= 500 -> Attempt.Transient
            status in 400..499 -> Attempt.Other(RuntimeException("HTTP $status: ${raw.take(300)}"))
            status == 0 -> Attempt.Other(RuntimeException(raw.take(300)))
            else -> null
        }
    }

    private fun classifyToException(status: Int, raw: String): Exception {
        return when (val a = classify(status, raw)) {
            is Attempt.Quota -> KeyQuotaException(a.daily)
            is Attempt.DeadKey -> KeyDeadException()
            is Attempt.ModelGone -> ModelGoneException()
            is Attempt.Transient -> KeyTransientException()
            else -> RuntimeException("HTTP $status: ${raw.take(300)}")
        }
    }

    private fun parseText(raw: String, trim: Boolean = true): String? = runCatching {
        val json = JSONObject(raw)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        (if (trim) sb.toString().trim() else sb.toString()).ifEmpty { null }
    }.getOrNull()

    private fun post(url: String, body: String, readTimeoutMs: Int = GENERATE_READ_TIMEOUT_MS): Pair<Int, String> {
        val conn = open(url, body, readTimeoutMs)
        return try {
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.readBytes().decodeToString()
            } else {
                conn.errorStream?.readBytes()?.decodeToString().orEmpty()
            }
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, body: String, readTimeoutMs: Int = STREAM_READ_TIMEOUT_MS): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5_000
        conn.readTimeout = readTimeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn
    }

    companion object {
        private const val TAG = "PooledGemini"
        const val ID = "gemini-pool"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** Limit answers come back in ~0.1 s, so walking several models stays fast. */
        private const val MAX_KEY_ATTEMPTS = 18
        private const val PER_MODEL_ATTEMPTS = 4
        private const val POOL_BUDGET_MS = 30_000L
        private const val MODEL_SWITCH_AFTER_TRANSIENTS = 2
        private const val GENERATE_READ_TIMEOUT_MS = 12_000
        private const val VISION_READ_TIMEOUT_MS = 30_000
        private const val STREAM_READ_TIMEOUT_MS = 15_000
    }
}
