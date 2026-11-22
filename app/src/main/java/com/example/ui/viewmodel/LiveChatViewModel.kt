package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.friendlyFirestoreMessage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LiveScreenMode {
    LIST, THREAD, DETAILS
}

data class LiveThreadState(
    val selectedId: String? = null,
    val screen: LiveScreenMode = LiveScreenMode.LIST,
    val newConversationOpen: Boolean = false,
    val peerDraft: String = "",
    val messageDraft: String = "",
    val searchQuery: String = "",
    val messages: List<LiveMessage> = emptyList(),
    val olderMessages: List<LiveMessage> = emptyList(),
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val messagesFromCache: Boolean = false,
    val busy: Boolean = false,
    val pendingNotice: Boolean = false,
    val error: String = "",
    val idCopiedNotice: Boolean = false
) {
    val allDisplayMessages: List<LiveMessage>
        get() {
            val recentIds = messages.mapTo(HashSet()) { it.id }
            val filteredOlder = olderMessages.filter { it.id !in recentIds }
            return filteredOlder + messages
        }
}

class LiveChatViewModel(
    private val repository: LiveChatRepository,
    private val currentUserId: String,
    displayName: String?
) : ViewModel() {

    private companion object {
        const val TAG = "LiveChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_PROFILE_OBSERVERS = 30
        const val SLOW_WRITE_NOTICE_MS = 8000L
        const val PAGE_SIZE = 100L
    }

    private val draftsByConversation = mutableMapOf<String, String>()
    private val errorsByConversation = mutableMapOf<String, String>()
    private val profileJobs = mutableMapOf<String, Job>()
    private var messageObserverJob: Job? = null
    private var oldestCursor: DocumentSnapshot? = null

    private val _peerProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, UserProfile>> = _peerProfiles.asStateFlow()

    private val _threadState = MutableStateFlow(LiveThreadState())
    val threadState: StateFlow<LiveThreadState> = _threadState.asStateFlow()

    private val _profileReady = MutableStateFlow(false)
    val profileReady: StateFlow<Boolean> = _profileReady.asStateFlow()

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { list ->
                syncPeerProfileObservers(list)
                UiState.Success(list)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", rootError)
                emit(UiState.Error(friendlyFirestoreMessage(rootError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val blockedIdsState: StateFlow<List<String>> =
        repository.observeBlockedIds(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing blocked IDs", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            val result = repository.registerProfile(currentUserId, displayName)
            result.onSuccess {
                _profileReady.value = true
            }.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    private fun syncPeerProfileObservers(conversations: List<Conversation>) {
        val peers = conversations
            .map { it.peerUid(currentUserId) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_PROFILE_OBSERVERS)
            .toSet()

        val stale = profileJobs.keys.filter { it !in peers }
        stale.forEach { uid ->
            profileJobs.remove(uid)?.cancel()
        }

        peers.forEach { uid ->
            if (!profileJobs.containsKey(uid)) {
                profileJobs[uid] = viewModelScope.launch {
                    repository.observePeerProfile(uid)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Profile listener failed for $uid", error)
                        }
                        .collect { profile ->
                            if (profile != null) {
                                _peerProfiles.value = _peerProfiles.value + (uid to profile)
                            }
                        }
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        messageObserverJob?.cancel()
        oldestCursor = null
        val savedDraft = draftsByConversation[conversationId].orEmpty()
        val savedError = errorsByConversation[conversationId].orEmpty()
        _threadState.value = _threadState.value.copy(
            selectedId = conversationId,
            screen = LiveScreenMode.THREAD,
            newConversationOpen = false,
            messageDraft = savedDraft,
            messages = emptyList(),
            olderMessages = emptyList(),
            hasOlder = false,
            loadingOlder = false,
            messagesFromCache = true,
            error = savedError
        )
        messageObserverJob = viewModelScope.launch {
            repository.observeRecentMessages(conversationId, PAGE_SIZE)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing messages", root)
                    _threadState.value = _threadState.value.copy(
                        error = friendlyFirestoreMessage(root),
                        messagesFromCache = false
                    )
                }
                .collect { snap ->
                    val currentOlder = _threadState.value.olderMessages
                    if (oldestCursor == null) {
                        oldestCursor = snap.oldestCursor
                    }
                    _threadState.value = _threadState.value.copy(
                        messages = snap.messages,
                        messagesFromCache = snap.fromCache,
                        hasOlder = currentOlder.isNotEmpty() || snap.messages.size >= PAGE_SIZE.toInt()
                    )
                }
        }
    }

    fun loadOlderMessages() {
        val state = _threadState.value
        val conversationId = state.selectedId ?: return
        val cursor = oldestCursor ?: return
        if (state.loadingOlder) return
        viewModelScope.launch {
            _threadState.value = _threadState.value.copy(loadingOlder = true, error = "")
            val result = repository.loadOlderMessages(conversationId, cursor, PAGE_SIZE)
            result.onSuccess { (older, nextCursor) ->
                oldestCursor = nextCursor
                val merged = (older + _threadState.value.olderMessages).distinctBy { it.id }
                _threadState.value = _threadState.value.copy(
                    olderMessages = merged,
                    hasOlder = older.size >= PAGE_SIZE.toInt() && nextCursor != null,
                    loadingOlder = false
                )
            }.onFailure { error ->
                _threadState.value = _threadState.value.copy(
                    loadingOlder = false,
                    error = friendlyFirestoreMessage(error)
                )
            }
        }
    }

    fun setScreen(screen: LiveScreenMode) {
        _threadState.value = _threadState.value.copy(screen = screen)
    }

    fun toggleNewConversation() {
        _threadState.value = _threadState.value.copy(
            newConversationOpen = !_threadState.value.newConversationOpen,
            error = ""
        )
    }

    fun updateSearch(query: String) {
        _threadState.value = _threadState.value.copy(searchQuery = query.take(128))
    }

    fun updatePeerDraft(text: String) {
        _threadState.value = _threadState.value.copy(peerDraft = text.take(128))
    }

    fun updateMessageDraft(text: String) {
        val id = _threadState.value.selectedId
        val clamped = text.take(2000)
        if (id != null) {
            draftsByConversation[id] = clamped
        }
        _threadState.value = _threadState.value.copy(messageDraft = clamped)
    }

    fun markIdCopied() {
        _threadState.value = _threadState.value.copy(idCopiedNotice = true)
    }

    fun startConversation() {
        val state = _threadState.value
        if (state.busy) return
        val peerUid = state.peerDraft.trim()
        if (peerUid in blockedIdsState.value) {
            _threadState.value = state.copy(error = "Unblock this account before requesting a conversation.")
            return
        }
        runSlowTrackedWrite {
            val result = repository.createConversation(currentUserId, peerUid)
            result.onSuccess { newId ->
                _threadState.value = _threadState.value.copy(
                    peerDraft = "",
                    newConversationOpen = false,
                    error = ""
                )
                selectConversation(newId)
            }.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    fun respondToRequest(conversationId: String, status: String) {
        if (_threadState.value.busy) return
        runSlowTrackedWrite {
            val result = repository.respondToConversation(conversationId, status)
            result.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    fun sendMessage() {
        val state = _threadState.value
        val conversationId = state.selectedId ?: return
        val text = state.messageDraft
        if (state.busy || text.trim().isEmpty()) return
        errorsByConversation[conversationId] = ""
        runSlowTrackedWrite {
            val result = repository.sendMessage(conversationId, currentUserId, text)
            result.onSuccess {
                if (draftsByConversation[conversationId] == text) {
                    draftsByConversation[conversationId] = ""
                }
                if (_threadState.value.selectedId == conversationId && _threadState.value.messageDraft == text) {
                    _threadState.value = _threadState.value.copy(messageDraft = "", error = "")
                }
            }.onFailure { error ->
                val msg = friendlyFirestoreMessage(error)
                errorsByConversation[conversationId] = msg
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(error = msg)
                }
            }
        }
    }

    fun blockPeer(peerUid: String, conversation: Conversation) {
        if (_threadState.value.busy) return
        runSlowTrackedWrite {
            val result = repository.blockAndClose(currentUserId, peerUid, conversation)
            result.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    fun unblockPeer(peerUid: String) {
        if (_threadState.value.busy) return
        runSlowTrackedWrite {
            val result = repository.unblock(currentUserId, peerUid)
            result.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    private fun runSlowTrackedWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            _threadState.value = _threadState.value.copy(busy = true, pendingNotice = false, error = "")
            val timerJob = launch {
                delay(SLOW_WRITE_NOTICE_MS)
                _threadState.value = _threadState.value.copy(pendingNotice = true)
            }
            try {
                block()
            } finally {
                timerJob.cancel()
                _threadState.value = _threadState.value.copy(busy = false, pendingNotice = false)
            }
        }
    }
}
