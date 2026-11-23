package com.example.data.ai

import com.example.data.repository.AppPreferencesRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Default Saira wiring: Gemini free tier first, then the user's backup chain. */
object SairaAiClient {

    private val preferencesRepository by lazy {
        runCatching {
            AppPreferencesRepository(FirebaseApp.getInstance().applicationContext)
        }.getOrNull()
    }

    private val router: AiRouter by lazy {
        AiRouter(
            primary = GeminiAiProvider(),
            fallbacks = { currentBackupProviders() },
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

    private suspend fun currentBackupProviders(): List<AiProvider> {
        val prefs = preferencesRepository ?: return emptyList()
        return prefs.aiBackupProviders.first()
            .filter { it.enabled && it.isConfigured }
            .mapNotNull { it.toProvider() }
    }
}
