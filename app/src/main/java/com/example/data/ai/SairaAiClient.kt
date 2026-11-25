package com.example.data.ai

import com.example.data.repository.AppPreferencesRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Default Saira wiring: Gemini fast tier first, user-configured backup second. */
object SairaAiClient {

    private val preferencesRepository by lazy {
        runCatching {
            AppPreferencesRepository(FirebaseApp.getInstance().applicationContext)
        }.getOrNull()
    }

    private val geminiProvider = GeminiAiProvider()

    /** Firestore-managed Gemini key pool (hundreds of keys, hot-swappable). */
    private val keyPool: GeminiKeyPool? by lazy {
        runCatching {
            GeminiKeyPool(FirebaseApp.getInstance().applicationContext)
        }.getOrNull()
    }

    /**
     * Primary: pooled direct-REST Gemini (rotates through every key in
     * settings/aiKeyPool) with the Firebase AI Logic provider as the
     * in-provider fallback — with zero keys provisioned it behaves exactly
     * like the old wiring.
     */
    private val pooledProvider: AiProvider by lazy {
        keyPool?.let { PooledGeminiProvider(it, geminiProvider) } ?: geminiProvider
    }

    private val router: AiRouter by lazy {
        AiRouter(
            primary = pooledProvider,
            fallback = { currentBackupProvider() },
            cooldowns = AiCooldownStore(),
            // Never let a raw AI exception reach the UI: when every provider is
            // down / exhausted / unconfigured, fall back to a friendly message so
            // Saira always responds smoothly instead of showing an error.
            neverThrow = true,
            safetyNet = { error -> AiSafeResponses.forError(error) }
        )
    }

    /** Pre-warms App Check token and Gemini model instance so the first reply starts in 1-2s. */
    suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            val appContext = runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
            if (appContext != null) {
                com.example.util.configureAppCheck(appContext, null)
            }
            runCatching {
                geminiProvider.warmUp(SairaPrompts.chatSystemInstruction)
            }
            // Pre-fetch the key pool so the first reply does not pay the read.
            runCatching {
                withTimeoutOrNull(8_000L) { keyPool?.availableKeys() }
            }
            runCatching {
                withTimeoutOrNull(8_000L) {
                    val token = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
                    if (token.isBlank() || token == "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ==") {
                        if (appContext != null) {
                            com.example.util.refreshAppCheckToken(appContext, timeoutMs = 8_000L)
                        } else {
                            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                        }
                    }
                }
            }
        }
    }

    suspend fun complete(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String = router.complete(systemInstruction, history, prompt)

    fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> = router.stream(systemInstruction, history, prompt)

    suspend fun resetCooldowns() {
        router.resetCooldowns()
    }

    private suspend fun currentBackupProvider(): AiProvider? {
        val prefs = preferencesRepository ?: return null
        val kind = AiFallbackProviderKind.fromId(prefs.aiFallbackProviderKind.first())
        return AiFallbackConfig(
            kind = kind,
            apiKey = prefs.aiFallbackApiKey.first(),
            baseUrl = prefs.aiFallbackBaseUrl.first().ifBlank { kind.defaultBaseUrl },
            model = prefs.aiFallbackModel.first().ifBlank { kind.defaultModel }
        ).toProvider()
    }
}
