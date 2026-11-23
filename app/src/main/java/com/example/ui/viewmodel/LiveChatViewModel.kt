package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.LiveMessage
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AlwaysOnlineConnectivityProvider
import com.example.data.repository.ChatMediaStorageRepository
import com.example.data.repository.ConnectivityStatusProvider
import com.example.data.repository.DemoPreferencesRepository
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.friendlyFirestoreMessage
import com.example.media.VoiceNotePlayer
import com.example.media.VoiceNoteRecorder
import com.example.media.VoicePlaybackState
import com.example.saira.SairaAssistantEngine
import com.example.saira.SairaCompanionMessage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val pendingImageUri: String? = null,
    val uploadingImage: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingAmplitude: Float = 0.1f,
    val pendingVoicePath: String? = null,
    val pendingVoiceDurationMs: Long = 0L,
    val uploadingVoice: Boolean = false,
    val uploadProgress: Float = 0f,
    val searchQuery: String = "",
    val threadSearchOpen: Boolean = false,
    val threadSearchQuery: String = "",
    val messages: List<LiveMessage> = emptyList(),
    val olderMessages: List<LiveMessage> = emptyList(),
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val messagesFromCache: Boolean = false,
    val isOnline: Boolean = true,
    val busy: Boolean = false,
    val pendingNotice: Boolean = false,
    val error: String = "",
    val idCopiedNotice: Boolean = false,
    val sairaOneMinuteEnabled: Boolean = true,
    val sairaCompanionMessages: List<SairaCompanionMessage> = emptyList(),
    val sairaCompanionDraft: String = "",
    val sairaCompanionBusy: Boolean = false
) {
    val isUploadingMedia: Boolean
        get() = uploadingImage || uploadingVoice

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
    displayName: String?,
    private val mediaRepository: ChatMediaStorageRepository? = null,
    private val voiceRecorder: VoiceNoteRecorder? = null,
    private val connectivityObserver: ConnectivityStatusProvider = AlwaysOnlineConnectivityProvider,
    private val preferencesRepository: DemoPreferencesRepository? = null
) : ViewModel() {

    private companion object {
        const val TAG = "LiveChatVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_PROFILE_OBSERVERS = 30
        const val LIST_MESSAGE_PREVIEW_LIMIT = 50L
        const val SLOW_WRITE_NOTICE_MS = 8000L
        const val PAGE_SIZE = 100L
        const val MAX_VOICE_DURATION_MS = 60_000L
        const val RECORD_TICK_MS = 150L
        const val SAIRA_INACTIVITY_DELAY_MS = 60_000L
        const val SAIRA_RECENT_WINDOW_MS = 10 * 60_000L
    }

    private val currentUserDisplayName = displayName?.trim().orEmpty().ifEmpty { "Friend" }
    private val draftsByConversation = mutableMapOf<String, String>()
    private val pendingImagesByConversation = mutableMapOf<String, String>()
    private val pendingVoicesByConversation = mutableMapOf<String, Pair<String, Long>>()
    private val errorsByConversation = mutableMapOf<String, String>()
    private val companionByConversation = mutableMapOf<String, List<SairaCompanionMessage>>()
    private val triggeredInactivityForMessageId = mutableSetOf<String>()
    private val profileJobs = mutableMapOf<String, Job>()
    private val listMessageJobs = mutableMapOf<String, Job>()
    private var messageObserverJob: Job? = null
    private var recordingTickerJob: Job? = null
    private var sairaOneMinuteJob: Job? = null
    private var oldestCursor: DocumentSnapshot? = null

    private val voicePlayer = VoiceNotePlayer(viewModelScope)
    val voicePlaybackState: StateFlow<VoicePlaybackState> = voicePlayer.state

    private val _peerProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, UserProfile>> = _peerProfiles.asStateFlow()

    private val _recentMessagesByConversation = MutableStateFlow<Map<String, List<LiveMessage>>>(emptyMap())
    private val _olderMessagesByConversation = MutableStateFlow<Map<String, List<LiveMessage>>>(emptyMap())

    val searchableMessagesByConversation: StateFlow<Map<String, List<LiveMessage>>> =
        combine(_recentMessagesByConversation, _olderMessagesByConversation) { recentMap, olderMap ->
            val keys = recentMap.keys + olderMap.keys
            keys.associateWith { convId ->
                val recent = recentMap[convId].orEmpty()
                val older = olderMap[convId].orEmpty()
                if (older.isEmpty()) {
                    recent
                } else {
                    val recentIds = recent.mapTo(HashSet()) { it.id }
                    older.filter { it.id !in recentIds } + recent
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    private val _threadState = MutableStateFlow(LiveThreadState())
    val threadState: StateFlow<LiveThreadState> = _threadState.asStateFlow()

    private val _profileReady = MutableStateFlow(false)
    val profileReady: StateFlow<Boolean> = _profileReady.asStateFlow()

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        repository.observeConversations(currentUserId)
            .map<List<Conversation>, UiState<List<Conversation>>> { list ->
                syncPeerProfileObservers(list)
                syncConversationMessageObservers(list)
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
        _threadState.value = _threadState.value.copy(isOnline = connectivityObserver.isCurrentlyOnline())
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (!online) {
                    if (_threadState.value.isRecordingVoice) {
                        cancelVoiceRecording()
                    }
                    sairaOneMinuteJob?.cancel()
                }
                _threadState.value = _threadState.value.copy(
                    isOnline = online,
                    newConversationOpen = if (online) _threadState.value.newConversationOpen else false
                )
                if (online) {
                    val selectedId = _threadState.value.selectedId
                    if (selectedId != null) {
                        scheduleSairaInactivityCheck(selectedId, _threadState.value.messages)
                    }
                }
            }
        }
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.sairaChatboxOneMinEnabled.collect { enabled ->
                    _threadState.value = _threadState.value.copy(sairaOneMinuteEnabled = enabled)
                    if (!enabled) {
                        sairaOneMinuteJob?.cancel()
                    } else {
                        val selectedId = _threadState.value.selectedId
                        if (selectedId != null) {
                            scheduleSairaInactivityCheck(selectedId, _threadState.value.messages)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            val result = repository.registerProfile(
                uid = currentUserId,
                displayNameRaw = displayName,
                isOnline = connectivityObserver.isCurrentlyOnline(),
                onDeferredFailure = { msg ->
                    _threadState.value = _threadState.value.copy(error = msg)
                }
            )
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

    private fun syncConversationMessageObservers(conversations: List<Conversation>) {
        val observedIds = conversations
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_PROFILE_OBSERVERS)
            .toSet()

        val stale = listMessageJobs.keys.filter { it !in observedIds }
        if (stale.isNotEmpty()) {
            stale.forEach { convId ->
                listMessageJobs.remove(convId)?.cancel()
            }
            _recentMessagesByConversation.value = _recentMessagesByConversation.value - stale.toSet()
            _olderMessagesByConversation.value = _olderMessagesByConversation.value - stale.toSet()
        }

        observedIds.forEach { convId ->
            if (!listMessageJobs.containsKey(convId)) {
                listMessageJobs[convId] = viewModelScope.launch {
                    repository.observeRecentMessages(convId, LIST_MESSAGE_PREVIEW_LIMIT)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "List message listener failed for $convId", error)
                        }
                        .collect { snap ->
                            if (!snap.fromCache) {
                                connectivityObserver.reportServerReachable()
                            }
                            _recentMessagesByConversation.value =
                                _recentMessagesByConversation.value + (convId to snap.messages)
                        }
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        messageObserverJob?.cancel()
        sairaOneMinuteJob?.cancel()
        cancelVoiceRecording()
        voicePlayer.stopAndRelease()
        oldestCursor = null
        val savedDraft = draftsByConversation[conversationId].orEmpty()
        val savedPendingImage = pendingImagesByConversation[conversationId]
        val savedPendingVoice = pendingVoicesByConversation[conversationId]
        val savedError = errorsByConversation[conversationId].orEmpty()
        val savedCompanion = companionByConversation[conversationId].orEmpty()
        val cachedMessages = _recentMessagesByConversation.value[conversationId].orEmpty()
        val cachedOlder = _olderMessagesByConversation.value[conversationId].orEmpty()
        _threadState.value = _threadState.value.copy(
            selectedId = conversationId,
            screen = LiveScreenMode.THREAD,
            newConversationOpen = false,
            threadSearchOpen = false,
            threadSearchQuery = "",
            messageDraft = savedDraft,
            pendingImageUri = savedPendingImage,
            uploadingImage = false,
            isRecordingVoice = false,
            recordingElapsedMs = 0L,
            recordingAmplitude = 0.1f,
            pendingVoicePath = savedPendingVoice?.first,
            pendingVoiceDurationMs = savedPendingVoice?.second ?: 0L,
            uploadingVoice = false,
            uploadProgress = 0f,
            messages = cachedMessages,
            olderMessages = cachedOlder,
            hasOlder = cachedOlder.isNotEmpty() || cachedMessages.size >= PAGE_SIZE.toInt(),
            loadingOlder = false,
            messagesFromCache = !connectivityObserver.isCurrentlyOnline() || cachedMessages.isEmpty(),
            error = savedError,
            sairaCompanionMessages = savedCompanion,
            sairaCompanionDraft = "",
            sairaCompanionBusy = false
        )
        scheduleSairaInactivityCheck(conversationId, cachedMessages)
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
                    if (!snap.fromCache) {
                        connectivityObserver.reportServerReachable()
                    }
                    val currentOlder = _threadState.value.olderMessages
                    if (oldestCursor == null) {
                        oldestCursor = snap.oldestCursor
                    }
                    _recentMessagesByConversation.value =
                        _recentMessagesByConversation.value + (conversationId to snap.messages)
                    _threadState.value = _threadState.value.copy(
                        messages = snap.messages,
                        messagesFromCache = snap.fromCache,
                        hasOlder = currentOlder.isNotEmpty() || snap.messages.size >= PAGE_SIZE.toInt()
                    )
                    val latest = snap.messages.lastOrNull()
                    if (latest != null && latest.senderId != currentUserId && companionByConversation[conversationId]?.isNotEmpty() == true) {
                        companionByConversation.remove(conversationId)
                        _threadState.value = _threadState.value.copy(sairaCompanionMessages = emptyList())
                    }
                    scheduleSairaInactivityCheck(conversationId, snap.messages)
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
                _olderMessagesByConversation.value =
                    _olderMessagesByConversation.value + (conversationId to merged)
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

    fun openThreadSearch() {
        _threadState.value = _threadState.value.copy(
            screen = LiveScreenMode.THREAD,
            threadSearchOpen = true
        )
    }

    fun closeThreadSearch() {
        _threadState.value = _threadState.value.copy(
            threadSearchOpen = false,
            threadSearchQuery = ""
        )
    }

    fun updateThreadSearch(query: String) {
        _threadState.value = _threadState.value.copy(threadSearchQuery = query.take(128))
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

    fun appendMessageEmoji(emoji: String = "😊") {
        if (!connectivityObserver.isCurrentlyOnline()) {
            reportAttachmentError("Reactions and emojis are disabled offline. Only text SMS can be sent.")
            return
        }
        val current = _threadState.value.messageDraft
        updateMessageDraft(current + emoji)
    }

    fun createCameraPhotoUri(): Uri? {
        if (!connectivityObserver.isCurrentlyOnline()) {
            reportAttachmentError("Camera photos are disabled offline. Only text SMS can be sent.")
            return null
        }
        return runCatching { mediaRepository?.createCameraImageUri() }
            .onFailure { error ->
                _threadState.value = _threadState.value.copy(
                    error = error.message ?: "Unable to prepare camera photo file."
                )
            }
            .getOrNull()
    }

    fun attachPendingImage(uriString: String?) {
        val id = _threadState.value.selectedId ?: return
        if (!uriString.isNullOrBlank() && !connectivityObserver.isCurrentlyOnline()) {
            reportAttachmentError("Photos and media are disabled offline. Only text SMS can be sent.")
            return
        }
        val previous = pendingImagesByConversation[id]
        if (previous != null && previous != uriString) {
            mediaRepository?.cleanupTempCameraFile(previous)
        }
        if (uriString.isNullOrBlank()) {
            pendingImagesByConversation.remove(id)
        } else {
            pendingImagesByConversation[id] = uriString
        }
        errorsByConversation[id] = ""
        _threadState.value = _threadState.value.copy(
            pendingImageUri = uriString,
            error = ""
        )
    }

    fun clearPendingImage() {
        val id = _threadState.value.selectedId
        val previous = if (id != null) pendingImagesByConversation.remove(id) else _threadState.value.pendingImageUri
        mediaRepository?.cleanupTempCameraFile(previous)
        _threadState.value = _threadState.value.copy(
            pendingImageUri = null,
            uploadingImage = false,
            uploadProgress = 0f
        )
    }

    fun reportAttachmentError(message: String) {
        val id = _threadState.value.selectedId
        if (id != null) {
            errorsByConversation[id] = message
        }
        _threadState.value = _threadState.value.copy(error = message)
    }

    fun startVoiceRecording() {
        val state = _threadState.value
        val conversationId = state.selectedId ?: return
        if (!connectivityObserver.isCurrentlyOnline()) {
            reportAttachmentError("Voice notes are disabled offline. Only text SMS can be sent.")
            return
        }
        if (state.busy || state.isUploadingMedia || state.isRecordingVoice) return
        val recorder = voiceRecorder
        if (recorder == null) {
            reportAttachmentError("Voice recording is unavailable on this device.")
            return
        }

        voicePlayer.stopAndRelease()
        pendingVoicesByConversation.remove(conversationId)?.let { (oldPath, _) ->
            recorder.deleteFile(oldPath)
        }
        errorsByConversation[conversationId] = ""

        val started = recorder.start()
        if (started.isFailure) {
            reportAttachmentError("Unable to start microphone recording. Check device audio availability.")
            return
        }

        _threadState.value = _threadState.value.copy(
            isRecordingVoice = true,
            recordingElapsedMs = 0L,
            recordingAmplitude = 0.12f,
            pendingVoicePath = null,
            pendingVoiceDurationMs = 0L,
            error = ""
        )

        recordingTickerJob?.cancel()
        recordingTickerJob = viewModelScope.launch {
            var elapsed = 0L
            while (_threadState.value.isRecordingVoice) {
                delay(RECORD_TICK_MS)
                elapsed += RECORD_TICK_MS
                val amp = recorder.currentAmplitudeNormalized()
                if (elapsed >= MAX_VOICE_DURATION_MS) {
                    _threadState.value = _threadState.value.copy(
                        recordingElapsedMs = MAX_VOICE_DURATION_MS,
                        recordingAmplitude = amp
                    )
                    stopVoiceRecording(sendImmediately = false)
                    break
                } else {
                    _threadState.value = _threadState.value.copy(
                        recordingElapsedMs = elapsed,
                        recordingAmplitude = amp
                    )
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        voiceRecorder?.cancel()
        _threadState.value = _threadState.value.copy(
            isRecordingVoice = false,
            recordingElapsedMs = 0L,
            recordingAmplitude = 0.1f
        )
    }

    fun stopVoiceRecording(sendImmediately: Boolean = false) {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val conversationId = _threadState.value.selectedId
        val result = voiceRecorder?.stop()?.getOrNull()
        if (result == null) {
            _threadState.value = _threadState.value.copy(
                isRecordingVoice = false,
                recordingElapsedMs = 0L,
                recordingAmplitude = 0.1f
            )
            reportAttachmentError("Voice note was too short. Record for at least a second.")
            return
        }

        val path = result.file.absolutePath
        if (conversationId != null) {
            pendingVoicesByConversation[conversationId] = path to result.durationMs
        }
        _threadState.value = _threadState.value.copy(
            isRecordingVoice = false,
            recordingElapsedMs = 0L,
            recordingAmplitude = 0.1f,
            pendingVoicePath = path,
            pendingVoiceDurationMs = result.durationMs,
            error = ""
        )
        if (sendImmediately) {
            sendMessage()
        }
    }

    fun clearPendingVoice() {
        val id = _threadState.value.selectedId
        voicePlayer.stopIfKey("pending_voice")
        val removedPath = if (id != null) {
            pendingVoicesByConversation.remove(id)?.first
        } else {
            _threadState.value.pendingVoicePath
        }
        voiceRecorder?.deleteFile(removedPath)
        _threadState.value = _threadState.value.copy(
            pendingVoicePath = null,
            pendingVoiceDurationMs = 0L,
            uploadingVoice = false
        )
    }

    fun toggleVoicePlayback(key: String, source: String, fallbackDurationMs: Long = 0L) {
        voicePlayer.togglePlayPause(key, source, fallbackDurationMs)
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
            val result = repository.createConversation(
                uid = currentUserId,
                rawPeerUid = peerUid,
                isOnline = connectivityObserver.isCurrentlyOnline()
            )
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
        if (!connectivityObserver.isCurrentlyOnline()) {
            _threadState.value = _threadState.value.copy(
                error = "Accepting or closing requests requires an internet connection. Offline mode supports text SMS only."
            )
            return
        }
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
        val pendingImage = state.pendingImageUri
        val pendingVoicePath = state.pendingVoicePath
        val pendingVoiceDurationMs = state.pendingVoiceDurationMs
        if (state.busy || state.isUploadingMedia) return
        if (text.trim().isEmpty() && pendingImage.isNullOrBlank() && pendingVoicePath.isNullOrBlank()) return

        val online = connectivityObserver.isCurrentlyOnline()
        if (!online && (!pendingImage.isNullOrBlank() || !pendingVoicePath.isNullOrBlank())) {
            val msg = "Offline mode supports text SMS only. Remove the photo or voice note attachment to send a text SMS."
            errorsByConversation[conversationId] = msg
            _threadState.value = _threadState.value.copy(error = msg)
            return
        }

        voicePlayer.stopIfKey("pending_voice")
        errorsByConversation[conversationId] = ""
        runSlowTrackedWrite {
            var uploadedImageUrl: String? = null
            var uploadedAudioUrl: String? = null
            val uploadedAudioDurationMs: Long? = if (!pendingVoicePath.isNullOrBlank()) pendingVoiceDurationMs else null

            if (!pendingImage.isNullOrBlank()) {
                val uploader = mediaRepository
                if (uploader == null) {
                    val msg = "Image upload service is unavailable."
                    errorsByConversation[conversationId] = msg
                    _threadState.value = _threadState.value.copy(error = msg)
                    return@runSlowTrackedWrite
                }
                _threadState.value = _threadState.value.copy(uploadingImage = true, uploadProgress = 0.05f)
                val uploadResult = uploader.uploadChatImage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    imageUri = Uri.parse(pendingImage),
                    onProgress = { progress ->
                        if (_threadState.value.selectedId == conversationId) {
                            _threadState.value = _threadState.value.copy(uploadProgress = progress)
                        }
                    }
                )
                uploadResult.onFailure { error ->
                    val msg = error.message ?: "Unable to upload image."
                    errorsByConversation[conversationId] = msg
                    if (_threadState.value.selectedId == conversationId) {
                        _threadState.value = _threadState.value.copy(
                            uploadingImage = false,
                            uploadProgress = 0f,
                            error = msg
                        )
                    }
                    return@runSlowTrackedWrite
                }
                uploadedImageUrl = uploadResult.getOrNull()
            }

            if (!pendingVoicePath.isNullOrBlank()) {
                val uploader = mediaRepository
                if (uploader == null) {
                    val msg = "Voice upload service is unavailable."
                    errorsByConversation[conversationId] = msg
                    _threadState.value = _threadState.value.copy(
                        uploadingImage = false,
                        uploadingVoice = false,
                        error = msg
                    )
                    return@runSlowTrackedWrite
                }
                _threadState.value = _threadState.value.copy(
                    uploadingImage = false,
                    uploadingVoice = true,
                    uploadProgress = 0.05f
                )
                val uploadResult = uploader.uploadVoiceNote(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    audioFile = java.io.File(pendingVoicePath),
                    onProgress = { progress ->
                        if (_threadState.value.selectedId == conversationId) {
                            _threadState.value = _threadState.value.copy(uploadProgress = progress)
                        }
                    }
                )
                uploadResult.onFailure { error ->
                    val msg = error.message ?: "Unable to upload voice note."
                    errorsByConversation[conversationId] = msg
                    if (_threadState.value.selectedId == conversationId) {
                        _threadState.value = _threadState.value.copy(
                            uploadingImage = false,
                            uploadingVoice = false,
                            uploadProgress = 0f,
                            error = msg
                        )
                    }
                    return@runSlowTrackedWrite
                }
                uploadedAudioUrl = uploadResult.getOrNull()
            }

            val result = repository.sendMessage(
                conversationId = conversationId,
                uid = currentUserId,
                rawBody = text,
                imageUrl = uploadedImageUrl,
                audioUrl = uploadedAudioUrl,
                audioDurationMs = uploadedAudioDurationMs,
                isOnline = connectivityObserver.isCurrentlyOnline(),
                onDeferredFailure = { msg ->
                    handleDeferredMessageFailure(conversationId, msg)
                }
            )
            result.onSuccess {
                if (draftsByConversation[conversationId] == text) {
                    draftsByConversation[conversationId] = ""
                }
                if (pendingImagesByConversation[conversationId] == pendingImage) {
                    pendingImagesByConversation.remove(conversationId)
                }
                if (pendingVoicesByConversation[conversationId]?.first == pendingVoicePath) {
                    pendingVoicesByConversation.remove(conversationId)
                }
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(
                        messageDraft = if (_threadState.value.messageDraft == text) "" else _threadState.value.messageDraft,
                        pendingImageUri = if (_threadState.value.pendingImageUri == pendingImage) null else _threadState.value.pendingImageUri,
                        pendingVoicePath = if (_threadState.value.pendingVoicePath == pendingVoicePath) null else _threadState.value.pendingVoicePath,
                        pendingVoiceDurationMs = if (_threadState.value.pendingVoicePath == pendingVoicePath) 0L else _threadState.value.pendingVoiceDurationMs,
                        uploadingImage = false,
                        uploadingVoice = false,
                        uploadProgress = 0f,
                        error = ""
                    )
                }
            }.onFailure { error ->
                val msg = friendlyFirestoreMessage(error)
                errorsByConversation[conversationId] = msg
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(
                        uploadingImage = false,
                        uploadingVoice = false,
                        uploadProgress = 0f,
                        error = msg
                    )
                }
            }
        }
    }

    fun sendQuickReaction(reaction: String) {
        val state = _threadState.value
        val conversationId = state.selectedId ?: return
        if (!connectivityObserver.isCurrentlyOnline()) {
            val msg = "Reactions are disabled offline. Only text SMS messages can be sent."
            errorsByConversation[conversationId] = msg
            _threadState.value = _threadState.value.copy(error = msg)
            return
        }
        val normalized = reaction.trim().take(8)
        if (normalized.isEmpty() || state.busy || state.isUploadingMedia || state.isRecordingVoice) return

        errorsByConversation[conversationId] = ""
        runSlowTrackedWrite {
            val result = repository.sendMessage(
                conversationId = conversationId,
                uid = currentUserId,
                rawBody = normalized,
                imageUrl = null,
                isOnline = connectivityObserver.isCurrentlyOnline(),
                onDeferredFailure = { msg ->
                    handleDeferredMessageFailure(conversationId, msg)
                }
            )
            result.onFailure { error ->
                val msg = friendlyFirestoreMessage(error)
                errorsByConversation[conversationId] = msg
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(error = msg)
                }
            }
        }
    }

    private fun handleDeferredMessageFailure(conversationId: String, message: String) {
        errorsByConversation[conversationId] = message
        if (_threadState.value.selectedId == conversationId) {
            _threadState.value = _threadState.value.copy(error = message)
        }
    }

    fun blockPeer(peerUid: String, conversation: Conversation) {
        if (_threadState.value.busy) return
        if (!connectivityObserver.isCurrentlyOnline()) {
            _threadState.value = _threadState.value.copy(
                error = "Blocking requires an internet connection. Offline mode supports text SMS only."
            )
            return
        }
        runSlowTrackedWrite {
            val result = repository.blockAndClose(currentUserId, peerUid, conversation)
            result.onFailure { error ->
                _threadState.value = _threadState.value.copy(error = friendlyFirestoreMessage(error))
            }
        }
    }

    fun unblockPeer(peerUid: String) {
        if (_threadState.value.busy) return
        if (!connectivityObserver.isCurrentlyOnline()) {
            _threadState.value = _threadState.value.copy(
                error = "Unblocking requires an internet connection. Offline mode supports text SMS only."
            )
            return
        }
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

    fun updateSairaCompanionDraft(text: String) {
        _threadState.value = _threadState.value.copy(sairaCompanionDraft = text.take(1000))
    }

    fun dismissSairaCompanion() {
        val id = _threadState.value.selectedId ?: return
        companionByConversation.remove(id)
        _threadState.value = _threadState.value.copy(
            sairaCompanionMessages = emptyList(),
            sairaCompanionDraft = "",
            sairaCompanionBusy = false
        )
    }

    fun sendSairaCompanionMessage() {
        val state = _threadState.value
        val conversationId = state.selectedId ?: return
        val question = state.sairaCompanionDraft.trim()
        if (question.isEmpty() || state.sairaCompanionBusy || !connectivityObserver.isCurrentlyOnline()) return

        val conversation = (conversationsState.value as? UiState.Success)?.data
            ?.firstOrNull { it.id == conversationId }
        val peerUid = conversation?.peerUid(currentUserId).orEmpty()
        val peerName = _peerProfiles.value[peerUid]?.displayName?.trim().orEmpty().ifEmpty { "your friend" }

        val userMsg = SairaCompanionMessage(text = question, isFromUser = true)
        val updatedWithUser = state.sairaCompanionMessages + userMsg
        companionByConversation[conversationId] = updatedWithUser
        _threadState.value = state.copy(
            sairaCompanionMessages = updatedWithUser,
            sairaCompanionDraft = "",
            sairaCompanionBusy = true
        )

        viewModelScope.launch {
            try {
                val reply = SairaAssistantEngine.askCompanionInThread(
                    userName = currentUserDisplayName,
                    peerName = peerName,
                    userQuestion = question
                )
                val botMsg = SairaCompanionMessage(text = reply, isFromUser = false)
                val currentList = companionByConversation[conversationId].orEmpty() + botMsg
                companionByConversation[conversationId] = currentList
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(
                        sairaCompanionMessages = currentList,
                        sairaCompanionBusy = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saira companion reply failed", e)
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(sairaCompanionBusy = false)
                }
            }
        }
    }

    private fun scheduleSairaInactivityCheck(conversationId: String, messages: List<LiveMessage>) {
        sairaOneMinuteJob?.cancel()
        val state = _threadState.value
        if (!state.sairaOneMinuteEnabled || !connectivityObserver.isCurrentlyOnline()) return
        val conversation = (conversationsState.value as? UiState.Success)?.data
            ?.firstOrNull { it.id == conversationId } ?: return
        if (conversation.status != "active") return
        val peerUid = conversation.peerUid(currentUserId)
        if (peerUid in blockedIdsState.value) return

        val latestMsg = messages.lastOrNull() ?: return
        if (latestMsg.body.startsWith("[Saira Assistant]")) return
        if (latestMsg.id in triggeredInactivityForMessageId) return

        val sentMillis = latestMsg.sentAt?.toDate()?.time ?: System.currentTimeMillis()
        val ageMs = (System.currentTimeMillis() - sentMillis).coerceAtLeast(0L)
        if (ageMs > SAIRA_RECENT_WINDOW_MS) return

        val remainingDelayMs = (SAIRA_INACTIVITY_DELAY_MS - ageMs).coerceIn(0L, SAIRA_INACTIVITY_DELAY_MS)
        sairaOneMinuteJob = viewModelScope.launch {
            if (remainingDelayMs > 0L) {
                delay(remainingDelayMs)
            }
            val currentState = _threadState.value
            if (currentState.selectedId != conversationId ||
                !currentState.sairaOneMinuteEnabled ||
                !connectivityObserver.isCurrentlyOnline()
            ) {
                return@launch
            }
            val stillLatest = currentState.messages.lastOrNull() ?: return@launch
            if (stillLatest.id != latestMsg.id || stillLatest.id in triggeredInactivityForMessageId) {
                return@launch
            }
            triggeredInactivityForMessageId.add(latestMsg.id)

            val peerName = _peerProfiles.value[peerUid]?.displayName?.trim().orEmpty().ifEmpty { "your friend" }
            val isSenderWaitingOnPeer = stillLatest.senderId == currentUserId

            if (isSenderWaitingOnPeer) {
                val assistText = SairaAssistantEngine.generateChatboxOneMinuteReply(
                    senderName = currentUserDisplayName,
                    peerName = peerName,
                    lastMessageText = stillLatest.body.ifBlank { "a message" },
                    isSenderWaitingOnPeer = true
                )
                val assistMsg = SairaCompanionMessage(text = assistText, isFromUser = false)
                val updatedList = companionByConversation[conversationId].orEmpty() + assistMsg
                companionByConversation[conversationId] = updatedList
                if (_threadState.value.selectedId == conversationId) {
                    _threadState.value = _threadState.value.copy(sairaCompanionMessages = updatedList)
                }
            } else {
                val autoReplyText = SairaAssistantEngine.generateChatboxOneMinuteReply(
                    senderName = peerName,
                    peerName = currentUserDisplayName,
                    lastMessageText = stillLatest.body.ifBlank { "a message" },
                    isSenderWaitingOnPeer = false
                )
                val formatted = if (autoReplyText.startsWith("[Saira Assistant]")) {
                    autoReplyText
                } else {
                    "[Saira Assistant] $autoReplyText"
                }
                repository.sendMessage(
                    conversationId = conversationId,
                    uid = currentUserId,
                    rawBody = formatted,
                    isOnline = connectivityObserver.isCurrentlyOnline()
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sairaOneMinuteJob?.cancel()
        recordingTickerJob?.cancel()
        voiceRecorder?.release()
        voicePlayer.stopAndRelease()
        pendingVoicesByConversation.values.forEach { (path, _) ->
            voiceRecorder?.deleteFile(path)
        }
        pendingVoicesByConversation.clear()
    }
}
