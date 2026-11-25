package com.example.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Routes every Saira request through the free Gemini provider first and
 * automatically switches to the configured backup provider when the free
 * quota / rate limit is used up ("limit ses hole arekta api te shift hobe").
 *
 * Cooldowns are remembered so a dead provider is not hammered on every message.
 *
 * In [neverThrow] mode (used by the real app) the router guarantees it NEVER
 * surfaces a raw exception: when every provider is unavailable, exhausted, or
 * misconfigured it returns / emits a calm, friendly message via [safetyNet]
 * instead. That is what keeps Saira "smooth all the time" with no error UI.
 * Unit tests use the default throwing mode to assert the failure contract.
 */
class AiRouter(
    private val primary: AiProvider,
    private val fallback: suspend () -> AiProvider?,
    private val cooldowns: AiCooldownStore = AiCooldownStore(),
    private val now: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val neverThrow: Boolean = false,
    private val safetyNet: (Throwable?) -> String = {
        AiDiagnostics.report("safetyNet", it)
        AiSafeResponses.forError(it)
    }
) {

    fun cooldownRemainingMs(providerId: String): Long =
        cooldowns.cooldownRemainingMs(providerId, now())

    suspend fun complete(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String {
        var lastError: Throwable? = null
        var primaryQuotaExhausted = false

        if (!cooldowns.isCooling(primary.id, now())) {
            try {
                val result = primary.generate(systemInstruction, history, prompt)
                if (result.isNotBlank()) return result
                lastError = AiUnavailableException(EMPTY_ANSWER_MESSAGE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiQuotaExhaustedException) {
                primaryQuotaExhausted = true
                markCooldown(primary.id, e)
            } catch (e: Throwable) {
                lastError = e
                log("Primary AI provider failed; switching to backup", e)
            }
        } else {
            primaryQuotaExhausted = true
        }

        val backup = fallback() ?: run {
            if (neverThrow) return safetyNet(lastError ?: AiUnavailableException(NO_BACKUP_MESSAGE))
            throw (if (primaryQuotaExhausted || lastError == null) {
                AiUnavailableException(NO_BACKUP_MESSAGE, lastError)
            } else {
                lastError
            })
        }
        if (cooldowns.isCooling(backup.id, now())) {
            if (neverThrow) return safetyNet(lastError ?: AiUnavailableException(ALL_EXHAUSTED_MESSAGE))
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE)
        }

        return try {
            val result = backup.generate(systemInstruction, history, prompt)
            if (result.isNotBlank()) {
                result
            } else if (neverThrow) {
                safetyNet(AiUnavailableException(EMPTY_ANSWER_MESSAGE))
            } else {
                throw AiUnavailableException(EMPTY_ANSWER_MESSAGE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaExhaustedException) {
            markCooldown(backup.id, e)
            if (neverThrow) safetyNet(e) else throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, e)
        } catch (e: Throwable) {
            // Any other backup failure (bad key, network, etc.) must also be swallowed
            // in neverThrow mode so Saira never surfaces a raw error to the user.
            if (neverThrow) safetyNet(e) else throw e
        }
    }

    fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = flow {
        var finishedByPrimary = false
        var primaryQuotaExhausted = false
        var primaryNonQuotaError: Throwable? = null

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
            } catch (e: Throwable) {
                primaryNonQuotaError = e
                log("Primary AI stream failed; switching to backup", e)
                finishedByPrimary = emitted
            }
        } else {
            primaryQuotaExhausted = true
        }

        if (finishedByPrimary) return@flow

        val backup = fallback()
        if (backup == null) {
            if (neverThrow) {
                emit(safetyNet(primaryNonQuotaError ?: AiUnavailableException(NO_BACKUP_MESSAGE)))
                return@flow
            }
            throw (if (primaryQuotaExhausted || primaryNonQuotaError == null) {
                AiUnavailableException(NO_BACKUP_MESSAGE, primaryNonQuotaError)
            } else {
                primaryNonQuotaError
            })
        }
        if (cooldowns.isCooling(backup.id, now())) {
            if (neverThrow) {
                emit(safetyNet(primaryNonQuotaError ?: AiUnavailableException(ALL_EXHAUSTED_MESSAGE)))
                return@flow
            }
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE)
        }

        val text = try {
            backup.generate(systemInstruction, history, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiQuotaExhaustedException) {
            markCooldown(backup.id, e)
            if (neverThrow) {
                emit(safetyNet(e))
                return@flow
            }
            throw AiUnavailableException(ALL_EXHAUSTED_MESSAGE, e)
        } catch (e: Throwable) {
            // Any other backup failure must not escape in neverThrow mode.
            if (neverThrow) {
                emit(safetyNet(e))
                return@flow
            }
            throw e
        }

        if (text.isBlank()) {
            if (neverThrow) {
                emit(safetyNet(AiUnavailableException(EMPTY_ANSWER_MESSAGE)))
                return@flow
            }
            throw AiUnavailableException(EMPTY_ANSWER_MESSAGE)
        }
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
