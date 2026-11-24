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

    private val router: AiRouter by lazy {
        AiRouter(
            primary = geminiProvider,
            fallback = { currentBackupProvider() },
            cooldowns = AiCooldownStore()
        )
    }

    /** Pre-warms App Check token and Gemini model instance so the first reply starts in 1-2s. */
    suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            runCatching {
                geminiProvider.warmUp(SairaPrompts.chatSystemInstruction)
            }
            runCatching {
                withTimeoutOrNull(8_000L) {
                    val token = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
                    if (token.isBlank()) {
                        FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
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
