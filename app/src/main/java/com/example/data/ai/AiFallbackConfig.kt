package com.example.data.ai

/** Free backup AI providers with sane defaults; every field stays editable in settings. */
enum class AiFallbackProviderKind(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GROQ(
        id = "groq",
        label = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile"
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free"
    ),
    CUSTOM(
        id = "custom",
        label = "Custom",
        defaultBaseUrl = "",
        defaultModel = ""
    );

    companion object {
        fun fromId(id: String?): AiFallbackProviderKind = entries.firstOrNull { it.id == id } ?: GROQ
    }
}

data class AiFallbackConfig(
    val kind: AiFallbackProviderKind,
    val apiKey: String,
    val baseUrl: String,
    val model: String
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() &&
            baseUrl.trim().ifEmpty { kind.defaultBaseUrl }.isNotBlank() &&
            model.trim().ifEmpty { kind.defaultModel }.isNotBlank()

    fun toProvider(): OpenAiCompatProvider? {
        if (!isConfigured) return null
        return OpenAiCompatProvider(
            baseUrl = baseUrl.trim().ifEmpty { kind.defaultBaseUrl },
            apiKey = apiKey.trim(),
            model = model.trim().ifEmpty { kind.defaultModel },
            id = "fallback-${kind.id}"
        )
    }
}
