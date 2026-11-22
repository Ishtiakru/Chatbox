package com.example.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class SairaAiRepository {
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(
                    "You are Saira, a helpful and calm guide to Chatbox. " +
                        "Answer in the user's language (English or Bengali if asked in Bengali) " +
                        "and ground app facts in the curated app knowledge below. " +
                        "If unsure, say so honestly; never claim unimplemented features like live voice/video calls are active. " +
                        "Do not take or suggest administrative actions.\n\n" +
                        SairaKnowledge.CURATED_TEXT
                )
            }
        )
    }

    fun askStream(question: String): Flow<String> {
        val trimmed = question.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 1000) {
            "Question must be between 1 and 1000 characters."
        }
        return model.generateContentStream(trimmed)
            .mapNotNull { chunk -> chunk.text }
    }
}
