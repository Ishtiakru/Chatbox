package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Routes every Saira request through the free Gemini provider first and
 * automatically switches to the configured backup provider when the free
 * quota is exhausted. Cooldowns are remembered so a dead provider is not
 * hammered on every message.
 */
class AiRouter(
    private val primary: AiProvider,
    private val fallback: suspend () -> AiProvider?,
    private val cooldowns: AiCooldownStore = AiCooldownStore(),
    private val now: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {

    fun cooldownRemainingMs(providerId: String): Long =
        cooldowns.cooldownRemainingMs(providerId, now())

    suspend fun complete(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String {
        var primaryQuotaExhausted = false
        var primaryNonQuotaError: Exception? = null

        if (!cooldowns.isCooling(primary.id, now())) {
            try {
                return primary.generate(systemInstruction, history, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                primaryQuotaExhausted = true
                markCooldown(primary.id, e)
            } catch (e: Exception) {
                primaryNonQuotaError = e
                log("Primary AI provider failed; switching to backup", e)
            }
        } else {
            primaryQuotaExhausted = true
        }

        val backup = fallback() ?: throw (
            if (primaryQuotaExhausted || primaryNonQuotaError == null) {
                AiUnavailableException(NO_BACKUP_MESSAGE, primaryNonQuotaError)
            } else {
                primaryNonQuotaError
            }
        )
        if (cooldowns.isCooling(backup.id, now())) {
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE)
        }

        return try {
            backup.generate(systemInstruction, history, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaExhaustedException) {
            markCooldown(backup.id, e)
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, e)
        }
    }

    fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = flow {
        var finishedByPrimary = false
        var primaryQuotaExhausted = false
        var primaryNonQuotaError: Exception? = null

        if (!cooldowns.isCooling(primary.id, now())) {
            var emitted = false
            try {
                primary.stream(systemInstruction, history, prompt).collect { chunk ->
                    emitted = true
                    emit(chunk)
                }
                finishedByPrimary = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                primaryQuotaExhausted = true
                markCooldown(primary.id, e)
                finishedByPrimary = emitted
            } catch (e: Exception) {
                primaryNonQuotaError = e
                log("Primary AI stream failed; switching to backup", e)
                finishedByPrimary = emitted
            }
        } else {
            primaryQuotaExhausted = true
        }

        if (finishedByPrimary) return@flow

        val backup = fallback() ?: throw (
            if (primaryQuotaExhausted || primaryNonQuotaError == null) {
                AiUnavailableException(NO_BACKUP_MESSAGE, primaryNonQuotaError)
            } else {
                primaryNonQuotaError
            }
        )
        if (cooldowns.isCooling(backup.id, now())) {
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE)
        }

        val text = try {
            backup.generate(systemInstruction, history, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaExhaustedException) {
            markCooldown(backup.id, e)
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, e)
        }

        if (text.isBlank()) throw AiUnavailableException(EMPTY_ANSWER_MESSAGE)
        emit(text)
    }

    fun resetCooldowns() {
        cooldowns.clearAll()
    }

    private fun markCooldown(providerId: String, error: Throwable) {
        val until = now() + cooldownMs
        cooldowns.mark(providerId, until)
        log("AI provider '$providerId' out of free quota; cooling down for ${cooldownMs / 1000}s", error)
    }

    private fun log(message: String, error: Throwable? = null) {
        runCatching { Log.w(TAG, message, error) }
    }

    companion object {
        private const val TAG = "AiRouter"
        const val DEFAULT_COOLDOWN_MS = 5 * 60_000L

        const val NO_BACKUP_MESSAGE =
            "Saira's free AI quota is used up and no backup AI is configured. " +
                "Add a free backup API key in Saira → Auto-Reply settings, then try again."

        const val ALL_EXHAUSTED_MESSAGE =
            "Saira's free AI quota is used up for now. Please try again in a few minutes."

        const val EMPTY_ANSWER_MESSAGE =
            "Saira returned an empty response. Please try again."
    }
}
