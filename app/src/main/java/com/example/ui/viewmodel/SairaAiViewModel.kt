package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiBackupProvider
import com.example.data.ai.AiBackupProviderKind
import com.example.data.ai.SairaAiClient
import com.example.data.model.AiExchange
import com.example.data.repository.AppPreferencesRepository
import com.example.data.repository.SairaAiRepository
import com.example.saira.SairaNotificationRule
import com.example.saira.SairaPlatform
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
    val error: String = "",
    val chatboxOneMinAutoReplyEnabled: Boolean = true,
    val notificationAutoReplyEnabled: Boolean = false,
    val notificationRules: List<SairaNotificationRule> = emptyList(),
    val ruleContactDraft: String = "",
    val rulePlatformDraft: SairaPlatform = SairaPlatform.ANY,
    val ruleGuidanceDraft: String = "",
    val backupProviders: List<AiBackupProvider> = emptyList(),
    val aiNotice: String = ""
) {
    val configuredBackupCount: Int
        get() = backupProviders.count { it.enabled && it.isConfigured }
}

class SairaAiViewModel(
    private val repository: SairaAiRepository = SairaAiRepository(),
    private val preferencesRepository: AppPreferencesRepository? = null
) : ViewModel() {

    private companion object {
        const val TAG = "SairaAiVM"
        const val AI_TIMEOUT_MS = 60_000L
    }

    private val _uiState = MutableStateFlow(SairaAiUiState())
    val uiState: StateFlow<SairaAiUiState> = _uiState.asStateFlow()

    init {
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.sairaNotificationRules.collect { rules ->
                    _uiState.value = _uiState.value.copy(notificationRules = rules)
                }
            }
            viewModelScope.launch {
                prefs.sairaNotifReplyEnabled.collect { enabled ->
                    _uiState.value = _uiState.value.copy(notificationAutoReplyEnabled = enabled)
                }
            }
            viewModelScope.launch {
                prefs.sairaChatboxOneMinEnabled.collect { enabled ->
                    _uiState.value = _uiState.value.copy(chatboxOneMinAutoReplyEnabled = enabled)
                }
            }
            viewModelScope.launch {
                prefs.aiBackupProviders.collect { providers ->
                    _uiState.value = _uiState.value.copy(backupProviders = providers)
                }
            }
        }
    }

    fun updateDraft(text: String) {
        if (text.length <= 2000) {
            _uiState.value = _uiState.value.copy(draft = text)
        }
    }

    fun submitQuestion() {
        val current = _uiState.value
        val question = current.draft.trim()
        if (current.isBusy || question.isEmpty() || question.length > 2000) return

        val isFirstTurn = current.exchanges.isEmpty()
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
                    repository.askStream(question = question, isFirstTurn = isFirstTurn).collect { chunk ->
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

    fun resetChat() {
        repository.resetSession()
        _uiState.value = _uiState.value.copy(
            exchanges = emptyList(),
            error = ""
        )
    }

    fun setChatboxOneMinuteAutoReply(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chatboxOneMinAutoReplyEnabled = enabled)
        viewModelScope.launch {
            preferencesRepository?.setSairaChatboxOneMinEnabled(enabled)
        }
    }

    fun setNotificationAutoReply(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationAutoReplyEnabled = enabled)
        viewModelScope.launch {
            preferencesRepository?.setSairaNotifReplyEnabled(enabled)
        }
    }

    fun updateRuleContactDraft(text: String) {
        _uiState.value = _uiState.value.copy(ruleContactDraft = text.take(80))
    }

    fun updateRulePlatformDraft(platform: SairaPlatform) {
        _uiState.value = _uiState.value.copy(rulePlatformDraft = platform)
    }

    fun updateRuleGuidanceDraft(text: String) {
        _uiState.value = _uiState.value.copy(ruleGuidanceDraft = text.take(400))
    }

    fun addNotificationRule() {
        val state = _uiState.value
        val contact = state.ruleContactDraft.trim()
        val guidance = state.ruleGuidanceDraft.trim()
        if (contact.isEmpty() || guidance.isEmpty()) return

        val newRule = SairaNotificationRule(
            contactName = contact,
            platform = state.rulePlatformDraft,
            taughtReplyGuidance = guidance,
            enabled = true
        )
        val updated = state.notificationRules + newRule
        _uiState.value = state.copy(
            notificationRules = updated,
            ruleContactDraft = "",
            ruleGuidanceDraft = ""
        )
        viewModelScope.launch {
            preferencesRepository?.saveSairaNotificationRules(updated)
        }
    }

    fun toggleNotificationRule(ruleId: String, enabled: Boolean) {
        val updated = _uiState.value.notificationRules.map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
        _uiState.value = _uiState.value.copy(notificationRules = updated)
        viewModelScope.launch {
            preferencesRepository?.saveSairaNotificationRules(updated)
        }
    }

    fun deleteNotificationRule(ruleId: String) {
        val updated = _uiState.value.notificationRules.filterNot { it.id == ruleId }
        _uiState.value = _uiState.value.copy(notificationRules = updated)
        viewModelScope.launch {
            preferencesRepository?.saveSairaNotificationRules(updated)
        }
    }

    fun addBackupProvider() {
        _uiState.value = _uiState.value.copy(
            backupProviders = _uiState.value.backupProviders + AiBackupProvider()
        )
    }

    fun updateBackupProvider(providerId: String, update: (AiBackupProvider) -> AiBackupProvider) {
        _uiState.value = _uiState.value.copy(
            backupProviders = _uiState.value.backupProviders.map { provider ->
                if (provider.id == providerId) update(provider) else provider
            }
        )
    }

    fun setBackupKind(providerId: String, kind: AiBackupProviderKind) {
        updateBackupProvider(providerId) { provider ->
            provider.copy(
                kindId = kind.id,
                baseUrl = if (provider.baseUrl.isBlank() || provider.baseUrl == provider.kind.defaultBaseUrl) {
                    ""
                } else {
                    provider.baseUrl
                },
                model = if (provider.model.isBlank() || provider.model == provider.kind.defaultModel) {
                    ""
                } else {
                    provider.model
                }
            )
        }
    }

    fun setBackupApiKey(providerId: String, value: String) {
        updateBackupProvider(providerId) { provider -> provider.copy(apiKey = value.take(300)) }
    }

    fun setBackupModel(providerId: String, value: String) {
        updateBackupProvider(providerId) { provider -> provider.copy(model = value.take(120)) }
    }

    fun setBackupBaseUrl(providerId: String, value: String) {
        updateBackupProvider(providerId) { provider -> provider.copy(baseUrl = value.take(300)) }
    }

    fun toggleBackupProvider(providerId: String, enabled: Boolean) {
        updateBackupProvider(providerId) { provider -> provider.copy(enabled = enabled) }
    }

    fun removeBackupProvider(providerId: String) {
        _uiState.value = _uiState.value.copy(
            backupProviders = _uiState.value.backupProviders.filterNot { it.id == providerId }
        )
    }

    fun moveBackupProvider(providerId: String, moveUp: Boolean) {
        val current = _uiState.value.backupProviders
        val index = current.indexOfFirst { it.id == providerId }
        val target = if (moveUp) index - 1 else index + 1
        if (index == -1 || target < 0 || target >= current.size) return
        val updated = current.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        _uiState.value = _uiState.value.copy(backupProviders = updated)
    }

    fun saveBackupProviders() {
        val current = _uiState.value
        _uiState.value = current.copy(aiNotice = "")
        viewModelScope.launch {
            preferencesRepository?.saveAiBackupProviders(current.backupProviders)
            SairaAiClient.resetCooldowns()
            _uiState.value = _uiState.value.copy(
                aiNotice = "Backup chain saved (${current.configuredBackupCount} ready). " +
                    "Saira switches automatically when Gemini's free quota ends."
            )
        }
    }

    fun clearAiNotice() {
        _uiState.value = _uiState.value.copy(aiNotice = "")
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
