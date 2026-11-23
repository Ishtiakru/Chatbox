package com.example.data.ai

import android.util.Log
import com.example.util.forceRefreshAppCheckToken
import com.example.util.isAppCheckError
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Primary provider: Gemini through Firebase AI Logic (free tier).
 * Uses gemini-3.8-flash by default for natural multi-turn responses,
 * with automatic fallback to gemini-3.5-flash-lite on non-quota model errors.
 */
class GeminiAiProvider(
    private val modelName: String = DEFAULT_MODEL,
    private val fallbackModelName: String? = FALLBACK_MODEL,
    private val maxOutputTokens: Int = 1024,
    override val id: String = DEFAULT_ID
) : AiProvider {

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun model(name: String, systemInstruction: String): GenerativeModel {
        val tokens = maxOutputTokens
        val cacheKey = "$name::$systemInstruction"
        return modelCache.getOrPut(cacheKey) {
            Firebase.ai.generativeModel(
                modelName = name,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    maxOutputTokens = tokens
                },
                systemInstruction = content { text(systemInstruction) }
            )
        }
    }

    fun warmUp(systemInstruction: String) {
        runCatching { model(modelName, systemInstruction) }
    }

    private fun historyContent(history: List<AiChatMessage>) =
        history.map { message -> content(role = message.role) { text(message.text) } }

    override suspend fun generate(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String {
        val convertedHistory = historyContent(history)
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                val chat: Chat = model(modelName, systemInstruction).startChat(history = convertedHistory)
                chat.sendMessage(prompt).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            throw AiUnavailableException("Gemini took too long to respond. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (AiErrorClassifier.isQuotaExhausted(e)) {
                throw AiQuotaExhaustedException("Gemini quota exhausted", e)
            }
            if (isAppCheckError(e)) {
                Log.w(TAG, "App Check error on primary generate(); force-refreshing token and retrying once.", e)
                forceRefreshAppCheckToken()
                return try {
                    withTimeout(TOTAL_TIMEOUT_MS) {
                        val chat: Chat = model(modelName, systemInstruction).startChat(history = convertedHistory)
                        chat.sendMessage(prompt).text?.trim().orEmpty()
                    }
                } catch (retryErr: TimeoutCancellationException) {
                    throw AiUnavailableException("Gemini took too long to respond. Please try again.", retryErr)
                } catch (retryErr: CancellationException) {
                    throw retryErr
                } catch (retryErr: Throwable) {
                    Log.e(TAG, "Gemini failed again after App Check refresh", retryErr)
                    throw retryErr
                }
            }
            Log.w(TAG, "Primary model '$modelName' failed; attempting fallback '$fallbackModelName'", e)
            val backupName = fallbackModelName?.takeIf { it != modelName }
            if (backupName != null) {
                return try {
                    withTimeout(TOTAL_TIMEOUT_MS) {
                        val chat: Chat = model(backupName, systemInstruction).startChat(history = convertedHistory)
                        chat.sendMessage(prompt).text?.trim().orEmpty()
                    }
                } catch (e2: TimeoutCancellationException) {
                    throw AiUnavailableException("Gemini took too long to respond. Please try again.", e2)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Throwable) {
                    if (AiErrorClassifier.isQuotaExhausted(e2)) {
                        throw AiQuotaExhaustedException("Gemini quota exhausted", e2)
                    }
                    throw e2
                }
            }
            throw e
        }
    }

    override fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = channelFlow {
        val candidateModels = buildList {
            add(modelName)
            fallbackModelName?.takeIf { it != modelName }?.let { add(it) }
        }
        val convertedHistory = historyContent(history)
        var lastError: Throwable? = null
        var succeeded = false
        var refreshedAppCheckOnce = false

        var idx = 0
        while (idx < candidateModels.size) {
            val candidate = candidateModels[idx]
            var emittedAny = false
            var timedOutOnFirstChunk = false
            try {
                withTimeout(TOTAL_TIMEOUT_MS) {
                    val chat = model(candidate, systemInstruction).startChat(history = convertedHistory)
                    coroutineScope {
                        val streamJob = launch {
                            chat.sendMessageStream(prompt).collect { chunk ->
                                val text = chunk.text
                                if (!text.isNullOrEmpty()) {
                                    emittedAny = true
                                    send(text)
                                }
                            }
                        }
                        val watchdog = launch {
                            delay(FIRST_CHUNK_TIMEOUT_MS)
                            if (!emittedAny) {
                                timedOutOnFirstChunk = true
                                streamJob.cancel()
                            }
                        }
                        streamJob.join()
                        watchdog.cancel()
                    }
                }
                if (timedOutOnFirstChunk && !emittedAny) {
                    lastError = AiUnavailableException(
                        "Saira AI took too long to start replying. Try again or add a Backup AI key in Auto-Reply settings."
                    )
                    idx++
                    continue
                }
                succeeded = true
                break
            } catch (e: TimeoutCancellationException) {
                lastError = AiUnavailableException(
                    "Saira AI response timed out. Please try again.",
                    e
                )
                if (emittedAny) break
                idx++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (AiErrorClassifier.isQuotaExhausted(e)) {
                    throw AiQuotaExhaustedException("Gemini quota exhausted", e)
                }
                if (!emittedAny && !refreshedAppCheckOnce && isAppCheckError(e)) {
                    refreshedAppCheckOnce = true
                    Log.w(TAG, "App Check error during stream(); force-refreshing token and retrying.", e)
                    forceRefreshAppCheckToken()
                    // Retry the same primary candidate once with a fresh App Check token
                    continue
                }
                lastError = e
                if (emittedAny) break
                idx++
            }
        }

        if (!succeeded && lastError != null) {
            throw lastError
        }
    }

    companion object {
        private const val TAG = "GeminiAiProvider"
        const val DEFAULT_ID = "gemini"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
        const val FIRST_CHUNK_TIMEOUT_MS = 15_000L
        const val TOTAL_TIMEOUT_MS = 45_000L
    }
}
