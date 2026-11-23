package com.example.data.ai

import com.example.data.repository.AppPreferencesRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Default Saira wiring: Gemini free tier first, user-configured backup second. */
object SairaAiClient {

    private val preferencesRepository by lazy {
        runCatching {
            AppPreferencesRepository(FirebaseApp.getInstance().applicationContext)
        }.getOrNull()
    }

    private val router: AiRouter by lazy {
        AiRouter(
            primary = GeminiAiProvider(),
            fallback = { currentBackupProvider() },
            cooldowns = AiCooldownStore()
        )
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
