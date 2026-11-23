package com.example.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.ConcurrentHashMap

/** Primary provider: Gemini through Firebase AI Logic (free tier). */
class GeminiAiProvider(
    private val modelName: String = DEFAULT_MODEL,
    private val maxOutputTokens: Int = 1024,
    override val id: String = DEFAULT_ID
) : AiProvider {

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private fun model(systemInstruction: String): GenerativeModel {
        val tokens = maxOutputTokens
        return modelCache.getOrPut(systemInstruction) {
            Firebase.ai.generativeModel(
                modelName = modelName,
                generationConfig = generationConfig { maxOutputTokens = tokens },
                systemInstruction = content { text(systemInstruction) }
            )
        }
    }

    private fun historyContent(history: List<AiChatMessage>) =
        history.map { message -> content(role = message.role) { text(message.text) } }

    override suspend fun generate(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): String {
        val chat: Chat = model(systemInstruction).startChat(history = historyContent(history))
        return chat.generateContent(prompt).text?.trim().orEmpty()
    }

    override fun stream(
        systemInstruction: String,
        history: List<AiChatMessage>,
        prompt: String
    ): Flow<String> {
        val chat = model(systemInstruction).startChat(history = historyContent(history))
        return chat.sendMessageStream(prompt).mapNotNull { chunk -> chunk.text }
    }

    companion object {
        const val DEFAULT_ID = "gemini"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
