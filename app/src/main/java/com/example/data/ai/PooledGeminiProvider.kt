package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.channelFlow
import kotlinx.coroutines.flow.Flow
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
    ): String = withContext(Dispatchers.IO) {
        val keys = pool.availableKeys()
        if (keys.isEmpty()) {
            return@withContext delegate.generate(systemInstruction, history, prompt)
        }
        var model = GeminiAiProvider.DEFAULT_MODEL
        var attempts = 0
        for (key in keys) {
            if (attempts >= MAX_KEY_ATTEMPTS) break
            attempts++
            val outcome = try {
                tryKey(key, model, systemInstruction, history, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Attempt.Other(e)
            }
            when (outcome) {
                is Attempt.Ok -> return@withContext outcome.text
                is Attempt.Quota -> pool.markCooling(key, GeminiKeyPool.QUOTA_COOLDOWN_MS)
                is Attempt.DeadKey -> pool.markCooling(key, GeminiKeyPool.DEAD_KEY_COOLDOWN_MS)
                is Attempt.ModelGone -> {
                    model = GeminiAiProvider.FALLBACK_MODEL
                    val retry = try {
                        tryKey(key, model, systemInstruction, history, prompt)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Attempt.Other(e)
                    }
                    when (retry) {
                        is Attempt.Ok -> return@withContext retry.text
                        is Attempt.Quota -> pool.markCooling(key, GeminiKeyPool.QUOTA_COOLDOWN_MS)
                        is Attempt.DeadKey -> pool.markCooling(key, GeminiKeyPool.DEAD_KEY_COOLDOWN_MS)
                        else -> Unit
                    }
                }
                is Attempt.Other -> {
                    // Not a per-key problem (DNS, TLS, malformed reply…):
                    // stop burning keys and let the Firebase provider try.
                    Log.w(TAG, "REST attempt failed non-quota; delegating to Firebase AI", outcome.error)
                    break
                }
            }
        }
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
        var model = GeminiAiProvider.DEFAULT_MODEL
        var attempts = 0
        var handled = false
        for (key in keys) {
            if (attempts >= MAX_KEY_ATTEMPTS) break
            attempts++
            try {
                withContext(Dispatchers.IO) {
                    streamKey(key, model, systemInstruction, history, prompt).collect {
                        send(it)
                    }
                }
                handled = true
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyQuotaException) {
                pool.markCooling(key, GeminiKeyPool.QUOTA_COOLDOWN_MS)
            } catch (e: KeyDeadException) {
                pool.markCooling(key, GeminiKeyPool.DEAD_KEY_COOLDOWN_MS)
            } catch (e: ModelGoneException) {
                model = GeminiAiProvider.FALLBACK_MODEL
            } catch (e: Throwable) {
                Log.w(TAG, "SSE attempt failed non-quota; delegating stream to Firebase AI", e)
                break
            }
        }
        if (!handled) {
            delegate.stream(systemInstruction, history, prompt).collect { send(it) }
        }
    }

    // ---- REST plumbing ----------------------------------------------------

    private sealed interface Attempt {
        data class Ok(val text: String) : Attempt
        object Quota : Attempt
        object DeadKey : Attempt
        object ModelGone : Attempt
        data class Other(val error: Throwable) : Attempt
    }

    private class KeyQuotaException : Exception("quota")
    private class KeyDeadException : Exception("invalid api key")
    private class ModelGoneException : Exception("model not found")

    private fun requestJson(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String = JSONObject().apply {
        put("system_instruction", JSONObject().put("parts", JSONArray().put(
            JSONObject().put("text", systemInstruction)
        )))
        val contents = JSONArray()
        for (m in history) contents.put(contentJson(m))
        contents.put(contentJson(AiChatMessage("user", prompt)))
        put("contents", contents)
        put(
            "generationConfig",
            JSONObject().put("maxOutputTokens", 1024).put("temperature", 0.7)
        )
    }.toString()

    private fun tryKey(
        key: String,
        model: String,
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Attempt {
        val body = requestJson(systemInstruction, history, prompt)
        val (status, raw) = post("$BASE/$model:generateContent?key=$key", body)
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
                    val text = parseText(payload) ?: continue
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
                lower.contains("quota") -> Attempt.Quota
            lower.contains("api_key_invalid") ||
                lower.contains("api key not valid") ||
                lower.contains("api_key_not_valid") ||
                (status == 403 && lower.contains("perm")) -> Attempt.DeadKey
            status == 404 || lower.contains("model not found") ||
                lower.contains("is not found") -> Attempt.ModelGone
            status in 400..499 -> Attempt.Other(RuntimeException("HTTP $status: ${raw.take(300)}"))
            status == 0 -> Attempt.Other(RuntimeException(raw.take(300)))
            else -> null
        }
    }

    private fun classifyToException(status: Int, raw: String): Exception {
        return when (classify(status, raw)) {
            is Attempt.Quota -> KeyQuotaException()
            is Attempt.DeadKey -> KeyDeadException()
            is Attempt.ModelGone -> ModelGoneException()
            else -> RuntimeException("HTTP $status: ${raw.take(300)}")
        }
    }

    private fun parseText(raw: String): String? = runCatching {
        val json = JSONObject(raw)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        sb.toString().trim().ifEmpty { null }
    }.getOrNull()

    private fun post(url: String, body: String): Pair<Int, String> {
        val conn = open(url, body)
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

    private fun open(url: String, body: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn
    }

    companion object {
        private const val TAG = "PooledGemini"
        const val ID = "gemini-pool"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_KEY_ATTEMPTS = 10
    }
}
