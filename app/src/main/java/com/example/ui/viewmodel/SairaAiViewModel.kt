package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiExchange
import com.example.data.repository.SairaAiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class SairaAiUiState(
    val draft: String = "",
    val exchanges: List<AiExchange> = emptyList(),
    val isBusy: Boolean = false,
    val error: String = ""
)

class SairaAiViewModel(
    private val repository: SairaAiRepository = SairaAiRepository()
) : ViewModel() {

    private companion object {
        const val TAG = "SairaAiVM"
        const val AI_TIMEOUT_MS = 60_000L
    }

    private val _uiState = MutableStateFlow(SairaAiUiState())
    val uiState: StateFlow<SairaAiUiState> = _uiState.asStateFlow()

    fun updateDraft(text: String) {
        if (text.length <= 1000) {
            _uiState.value = _uiState.value.copy(draft = text)
        }
    }

    fun submitQuestion() {
        val current = _uiState.value
        val question = current.draft.trim()
        if (current.isBusy || question.isEmpty() || question.length > 1000) return

        val exchangeId = UUID.randomUUID().toString()
        val initialExchange = AiExchange(
            id = exchangeId,
            question = question,
            answer = "",
            isStreaming = true
        )
        _uiState.value = current.copy(
            draft = "",
            isBusy = true,
            error = "",
            exchanges = current.exchanges + initialExchange
        )

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    repository.askStream(question).collect { chunk ->
                        accumulated.append(chunk)
                        updateExchange(exchangeId, accumulated.toString(), isStreaming = true)
                    }
                }
                val finalAnswer = accumulated.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    updateExchange(exchangeId, finalAnswer, isStreaming = false)
                } else {
                    removeExchange(exchangeId)
                    _uiState.value = _uiState.value.copy(
                        error = "Saira returned an empty response. Please try again."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Saira AI request timed out", e)
                if (accumulated.isBlank()) removeExchange(exchangeId)
                else updateExchange(exchangeId, accumulated.toString(), isStreaming = false)
                _uiState.value = _uiState.value.copy(
                    error = "Request timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saira AI generation failed", e)
                if (accumulated.isBlank()) removeExchange(exchangeId)
                else updateExchange(exchangeId, accumulated.toString(), isStreaming = false)
                _uiState.value = _uiState.value.copy(
                    error = e.localizedMessage ?: "Saira AI is unavailable right now."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isBusy = false)
            }
        }
    }

    private fun updateExchange(id: String, answer: String, isStreaming: Boolean) {
        _uiState.value = _uiState.value.copy(
            exchanges = _uiState.value.exchanges.map { item ->
                if (item.id == id) item.copy(answer = answer, isStreaming = isStreaming) else item
            }
        )
    }

    private fun removeExchange(id: String) {
        _uiState.value = _uiState.value.copy(
            exchanges = _uiState.value.exchanges.filterNot { it.id == id }
        )
    }
}
