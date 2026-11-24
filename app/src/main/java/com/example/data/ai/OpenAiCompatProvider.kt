package com.example.data.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Backup provider speaking the OpenAI-compatible `/chat/completions` API
 * (Groq, OpenRouter, or any self-hosted gateway).
 *
 * Transient connectivity failures (timeouts, dropped sockets) are retried once
 * so a brief network blip never turns into a visible error for the user.
 */
class OpenAiCompatProvider(
    private val baseUrl: String,
    private val apiKey: String,
    val model: String,
    override val id: String = "openai-compatible",
    private val maxTokens: Int = 1024,
    private val timeoutMs: Int = 25_000,
    private val maxRetries: Int = 1
) : AiProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.7)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                if (systemInstruction.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                }
                history.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == "model") "assistant" else message.role)
                        put("content", message.text)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        var lastError: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                return@withContext performRequest(payload)
            } catch (e: AiQuotaExhaustedException) {
                throw e
            } catch (e: AiUnavailableException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Transient connectivity issue: retry if attempts remain.
                lastError = e
            } catch (e: Exception) {
                throw AiUnavailableException(
                    "Backup AI request failed: ${e.message ?: e.javaClass.simpleName}", e
                )
            }
        }
        throw AiUnavailableException(
            "Backup AI request failed after ${maxRetries + 1} attempts: " +
                "${lastError?.message ?: lastError?.javaClass?.simpleName ?: "unknown"}",
            lastError
        )
    }

    /** Performs a single HTTP request and returns the assistant content, or throws. */
    private fun performRequest(payload: kotlinx.serialization.json.JsonObject): String {
        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
                .orEmpty()

            when {
                status == 429 -> throw AiQuotaExhaustedException(
                    "Backup AI rate limit reached (HTTP 429)."
                )

                status == 401 || status == 403 -> throw AiUnavailableException(
                    "Backup AI rejected the API key (HTTP $status). Check the key in Saira settings."
                )

                status !in 200..299 -> throw AiUnavailableException(
                    "Backup AI error (HTTP $status): ${body.take(200)}"
                )

                else -> {
                    val content = json.parseToJsonElement(body)
                        .jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.content
                    return content?.trim().orEmpty()
                }
            }
        } catch (e: AiQuotaExhaustedException) {
            throw e
        } catch (e: AiUnavailableException) {
            throw e
        } catch (e: IOException) {
            // Re-throw so the retry loop treats it as a transient connectivity error.
            throw e
        } catch (e: Exception) {
            throw AiUnavailableException(
                "Backup AI request failed: ${e.message ?: e.javaClass.simpleName}", e
            )
        } finally {
            connection.disconnect()
        }
    }
}
