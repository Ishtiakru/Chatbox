package com.example.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One chat turn. [role] is "user" or "model" (OpenAI-compatible providers map "model" to "assistant").
 */
data class AiChatMessage(val role: String, val text: String)

/** Raised when a provider says its free quota / rate limit is used up. */
class AiQuotaExhaustedException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Raised when every configured provider is unavailable, exhausted, or misconfigured. */
class AiUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface AiProvider {
    /** Stable provider id, also used as the cooldown key. */
    val id: String

    suspend fun generate(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String

    /** Providers that cannot stream fall back to a single emission. */
    fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = flow { emit(generate(systemInstruction, history, prompt)) }
}

object AiErrorClassifier {
    private val QUOTA_MARKERS = listOf(
        "quota",
        "resource_exhausted",
        "resource exhausted",
        "rate limit",
        "ratelimit",
        "too many requests",
        "429",
        "exceeded",
        "overloaded",
        "try again later"
    )

    fun isQuotaExhausted(error: Throwable?): Boolean {
        var current = error
        var depth = 0
        while (current != null && depth < 6) {
            val text = "${current.javaClass.simpleName} ${current.message.orEmpty()}".lowercase()
            if (QUOTA_MARKERS.any { marker -> text.contains(marker) }) return true
            current = current.cause
            depth++
        }
        return false
    }
}
