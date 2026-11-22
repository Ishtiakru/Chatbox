package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DemoCatalog
import com.example.data.model.DemoContact
import com.example.data.model.DemoMessage
import com.example.data.model.SairaOfflineGuide
import com.example.data.repository.DemoPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class DemoModal {
    VOICE, VIDEO, ATTACH, NEW_CHAT, ABOUT
}

data class DemoUiState(
    val activeContactId: String = "maya",
    val searchQuery: String = "",
    val draft: String = "",
    val savedMessages: Map<String, List<DemoMessage>> = emptyMap(),
    val loginLanguage: String = "en-US",
    val activeModal: DemoModal? = null
) {
    val activeContact: DemoContact
        get() = DemoCatalog.contacts.firstOrNull { it.id == activeContactId }
            ?: DemoCatalog.contacts.first()

    val filteredContacts: List<DemoContact>
        get() {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) return DemoCatalog.contacts
            return DemoCatalog.contacts.filter { contact ->
                val matchesContact = "${contact.name} ${contact.role}".lowercase().contains(q)
                val matchesMessage = messagesFor(contact.id).any { it.text.lowercase().contains(q) }
                matchesContact || matchesMessage
            }
        }

    fun messagesFor(contactId: String): List<DemoMessage> {
        val seeds = DemoCatalog.seeds[contactId].orEmpty()
        val extra = savedMessages[contactId].orEmpty()
        return seeds + extra
    }

    fun previewFor(contact: DemoContact): String {
        val q = searchQuery.trim().lowercase()
        val allMessages = messagesFor(contact.id)
        if (q.isNotEmpty()) {
            val matched = allMessages.lastOrNull { it.text.lowercase().contains(q) }
            if (matched != null) {
                val prefix = if (matched.from == "me") "You: " else ""
                return "$prefix${matched.text}"
            }
        }
        val last = allMessages.lastOrNull()
        return if (last != null) {
            val prefix = if (last.from == "me") "You: " else ""
            "$prefix${last.text}"
        } else {
            contact.role
        }
    }
}

class DemoChatViewModel(
    private val prefsRepo: DemoPreferencesRepository
) : ViewModel() {

    private val _localState = MutableStateFlow(DemoUiState())

    val uiState: StateFlow<DemoUiState> = combine(
        _localState,
        prefsRepo.savedMessages,
        prefsRepo.loginLanguage
    ) { local, saved, lang ->
        local.copy(savedMessages = saved, loginLanguage = lang)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DemoUiState()
    )

    fun selectContact(contactId: String) {
        _localState.value = _localState.value.copy(
            activeContactId = contactId,
            draft = ""
        )
    }

    fun updateSearch(query: String) {
        _localState.value = _localState.value.copy(searchQuery = query)
    }

    fun updateDraft(text: String) {
        if (text.length <= 2000) {
            _localState.value = _localState.value.copy(draft = text)
        }
    }

    fun appendEmoji() {
        val next = (_localState.value.draft + " 😊").take(2000)
        _localState.value = _localState.value.copy(draft = next)
    }

    fun showModal(modal: DemoModal?) {
        _localState.value = _localState.value.copy(activeModal = modal)
    }

    fun setLoginLanguage(languageCode: String) {
        viewModelScope.launch {
            prefsRepo.setLoginLanguage(languageCode)
        }
    }

    fun sendMessage() {
        val current = uiState.value
        val text = current.draft.trim()
        if (text.isEmpty()) return
        val contactId = current.activeContactId
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date())
        val updatedList = current.savedMessages[contactId].orEmpty().toMutableList()
        updatedList.add(DemoMessage(from = "me", text = text, time = time))
        if (contactId == "saira") {
            updatedList.add(DemoMessage(from = "them", text = SairaOfflineGuide.reply(text), time = time))
        }
        val nextSaved = current.savedMessages.toMutableMap().apply {
            put(contactId, updatedList)
        }
        _localState.value = _localState.value.copy(draft = "")
        viewModelScope.launch {
            prefsRepo.saveDemoMessages(nextSaved)
        }
    }
}
