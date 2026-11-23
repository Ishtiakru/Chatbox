package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Routes every Saira request through the free Gemini provider first and then
 * through the configured backup chain, in order. A provider that answers with
 * a quota error is put on a cooldown so the next message skips it.
 */
class AiRouter(
    private val primary: AiProvider,
    private val fallbacks: suspend () -> List<AiProvider>,
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
        if (!cooldowns.isCooling(primary.id, now())) {
            try {
                return primary.generate(systemInstruction, history, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                markCooldown(primary.id, e)
            } catch (e: Exception) {
                log("Primary AI provider failed; switching to the backup chain", e)
            }
        }

        val backups = fallbacks().filter { !cooldowns.isCooling(it.id, now()) }
        if (backups.isEmpty()) {
            throw AiUnavailableException(
                if (fallbacks().isEmpty()) NO_BACKUP_MESSAGE else ALL_EXHAUSTED_MESSAGE
            )
        }

        var lastError: Throwable? = null
        for (backup in backups) {
            try {
                return backup.generate(systemInstruction, history, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                markCooldown(backup.id, e)
                lastError = e
            } catch (e: Exception) {
                log("Backup AI '${backup.id}' failed; trying the next one", e)
                lastError = e
            }
        }

        throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, lastError)
    }

    fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = flow {
        var finishedByPrimary = false

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
                markCooldown(primary.id, e)
                finishedByPrimary = emitted
            } catch (e: Exception) {
                log("Primary AI stream failed; switching to the backup chain", e)
                finishedByPrimary = emitted
            }
        }

        if (finishedByPrimary) return@flow

        val backups = fallbacks().filter { !cooldowns.isCooling(it.id, now()) }
        if (backups.isEmpty()) {
            throw AiUnavailableException(
                if (fallbacks().isEmpty()) NO_BACKUP_MESSAGE else ALL_EXHAUSTED_MESSAGE
            )
        }

        var lastError: Throwable? = null
        for (backup in backups) {
            val text = try {
                backup.generate(systemInstruction, history, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                markCooldown(backup.id, e)
                lastError = e
                null
            } catch (e: Exception) {
                log("Backup AI '${backup.id}' stream failed; trying the next one", e)
                lastError = e
                null
            }

            if (!text.isNullOrBlank()) {
                emit(text)
                return@flow
            }
        }

        throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, lastError)
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
