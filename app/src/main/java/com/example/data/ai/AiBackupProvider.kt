package com.example.data.ai

import kotlinx.serialization.Serializable
import java.util.UUID

/** Free-tier backup providers with sane defaults; every field stays editable in settings. */
enum class AiBackupProviderKind(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyHint: String
) {
    GOOGLE_AI_STUDIO(
        id = "google_ai_studio",
        label = "Google AI Studio",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-flash",
        keyHint = "aistudio.google.com/apikey"
    ),
    GROQ(
        id = "groq",
        label = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyHint = "console.groq.com/keys"
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        keyHint = "openrouter.ai/keys"
    ),
    CEREBRAS(
        id = "cerebras",
        label = "Cerebras",
        defaultBaseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "llama3.1-8b",
        keyHint = "cloud.cerebras.ai"
    ),
    MISTRAL(
        id = "mistral",
        label = "Mistral",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        keyHint = "console.mistral.ai"
    ),
    GITHUB(
        id = "github",
        label = "GitHub Models",
        defaultBaseUrl = "https://models.github.ai/inference",
        defaultModel = "openai/gpt-4o-mini",
        keyHint = "github.com/settings/tokens"
    ),
    CUSTOM(
        id = "custom",
        label = "Custom",
        defaultBaseUrl = "",
        defaultModel = "",
        keyHint = "your OpenAI-compatible gateway"
    );

    companion object {
        fun fromId(id: String?): AiBackupProviderKind = entries.firstOrNull { it.id == id } ?: GROQ
    }
}

/**
 * One link in the backup chain. The router tries the enabled, configured
 * entries in order and skips whoever is in quota cooldown.
 */
@Serializable
data class AiBackupProvider(
    val id: String = UUID.randomUUID().toString(),
    val kindId: String = AiBackupProviderKind.GROQ.id,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true
) {
    val kind: AiBackupProviderKind get() = AiBackupProviderKind.fromId(kindId)

    val effectiveBaseUrl: String
        get() = baseUrl.trim().ifEmpty { kind.defaultBaseUrl }

    val effectiveModel: String
        get() = model.trim().ifEmpty { kind.defaultModel }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && effectiveBaseUrl.isNotBlank() && effectiveModel.isNotBlank()

    fun toProvider(): OpenAiCompatProvider? {
        if (!isConfigured) return null
        return OpenAiCompatProvider(
            baseUrl = effectiveBaseUrl,
            apiKey = apiKey.trim(),
            model = effectiveModel,
            id = "backup-$id"
        )
    }
}
