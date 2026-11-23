package com.example.data.repository

import com.example.saira.SairaAssistantEngine
import kotlinx.coroutines.flow.Flow

class SairaAiRepository {
    fun askStream(question: String, isFirstTurn: Boolean = false): Flow<String> {
        return SairaAssistantEngine.askStream(question = question, isFirstTurn = isFirstTurn)
    }

    fun resetSession() {
        SairaAssistantEngine.resetSession()
    }
}
