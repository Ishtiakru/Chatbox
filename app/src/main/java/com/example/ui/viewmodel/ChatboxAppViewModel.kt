package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatboxCatalog
import com.example.data.ai.AiFallbackProviderKind
import com.example.data.ai.SairaAiClient
import com.example.data.model.AdminAuthStatus
import com.example.data.model.AiExchange
import com.example.data.model.AppTab
import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxMessageKind
import com.example.data.model.ChatboxReportEntry
import com.example.data.model.ChatboxThread
import com.example.data.model.CoreAppTab
import com.example.data.model.LocalSairaExchange
import com.example.data.model.LocalSairaSessionData
import com.example.data.model.LocalUserProfile
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkDynamicConfig
import com.example.data.model.UserProfile
import com.example.data.repository.AppPreferencesRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.SairaSessionRepository
import com.example.data.repository.SparkAdminRepository
import com.example.saira.SairaAutoReplyLog
import com.example.saira.SairaDirectoryUser
import com.example.saira.SairaNotificationRule
import com.example.util.NetworkMonitor
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ChatboxAppViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = AppPreferencesRepository(application)
    val authRepo = AuthRepository(application)
    val liveChatRepo = LiveChatRepository(application)
    val sairaSessionRepo = SairaSessionRepository(application)
    val sparkAdminRepo = SparkAdminRepository(application)

    val isOnline: StateFlow<Boolean> = NetworkMonitor.observeNetworkConnected(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NetworkMonitor.isCurrentlyConnected(application))

    val currentUser: StateFlow<FirebaseUser?> = authRepo.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepo.currentUser())

    val loginLanguage: StateFlow<String> = prefs.loginLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en-US")

    val quickReaction: StateFlow<String> = prefs.quickReaction
        .stateIn(viewModelScope, SharingStarted.Eagerly, "👍")

    val oneMinAutoReplyEnabled: StateFlow<Boolean> = prefs.sairaChatboxOneMinEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notifAutoReplyEnabled: StateFlow<Boolean> = prefs.sairaNotifReplyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val liveLocationEnabled: StateFlow<Boolean> = prefs.sairaLiveLocationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val liveLocationAllowedPeers: StateFlow<Set<String>> = prefs.sairaLiveLocationAllowedPeers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val dmPeerNicknames: StateFlow<Map<String, String>> = prefs.dmPeerNicknames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val dmMutedPeers: StateFlow<Set<String>> = prefs.dmMutedPeers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val sairaRules: StateFlow<List<SairaNotificationRule>> = prefs.sairaNotificationRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sairaAutoReplyLogs: StateFlow<List<SairaAutoReplyLog>> = prefs.sairaAutoReplyLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val aiFallbackProviderKind: StateFlow<String> = prefs.aiFallbackProviderKind
        .stateIn(viewModelScope, SharingStarted.Eagerly, AiFallbackProviderKind.GROQ.id)

    val aiFallbackApiKey: StateFlow<String> = prefs.aiFallbackApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aiFallbackBaseUrl: StateFlow<String> = prefs.aiFallbackBaseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val aiFallbackModel: StateFlow<String> = prefs.aiFallbackModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val chatboxThreads: StateFlow<List<ChatboxThread>> = prefs.chatboxThreads
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatboxCatalog.defaultThreads)

    val chatboxMessages: StateFlow<Map<String, List<ChatboxMessage>>> = prefs.chatboxMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatboxCatalog.defaultSeeds)

    val chatboxBackupPin: StateFlow<String> = prefs.chatboxBackupPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, "123456")

    val chatboxBlockedThreads: StateFlow<Set<String>> = prefs.chatboxBlockedThreads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val chatboxCallLogs: StateFlow<List<ChatboxCallEntry>> = prefs.chatboxCallLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatboxCatalog.defaultCallLogs)

    val chatboxReports: StateFlow<List<ChatboxReportEntry>> = prefs.chatboxReports
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localUserProfile: StateFlow<LocalUserProfile> = prefs.localUserProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalUserProfile())

    val localSairaSessions: StateFlow<List<LocalSairaSessionData>> = prefs.localSairaSessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val publicWelcome: StateFlow<String> = currentUser
        .flatMapLatest { user -> if (user != null) sparkAdminRepo.observePublicWelcome() else flowOf("") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val appearance: StateFlow<SparkAppearance> = currentUser
        .flatMapLatest { user ->
            if (user != null) sparkAdminRepo.observeSparkAppearance() else flowOf(SparkAppearance())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SparkAppearance())

    val dynamicConfig: StateFlow<SparkDynamicConfig> = currentUser
        .flatMapLatest { user ->
            if (user != null) sparkAdminRepo.observeSparkDynamicConfig() else flowOf(SparkDynamicConfig())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SparkDynamicConfig())

    val appUpdate: StateFlow<SparkAppUpdate> = currentUser
        .flatMapLatest { user ->
            if (user != null) sparkAdminRepo.observeSparkAppUpdate() else flowOf(SparkAppUpdate())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SparkAppUpdate())

    val userAvatarPath: StateFlow<String?> = currentUser
        .flatMapLatest { user ->
            val key = user?.uid ?: "local_user"
            prefs.observeUserAvatarPath(key)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _ownProfile = MutableStateFlow<UserProfile?>(null)
    val ownProfile: StateFlow<UserProfile?> = _ownProfile.asStateFlow()

    private val _selectedTab = MutableStateFlow<AppTab>(AppTab.Core(CoreAppTab.CHATBOX))
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _activeThreadId = MutableStateFlow<String?>("sazzad")
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var lastLoadedUid: String? = null

    init {
        syncSairaUserDirectory()
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) {
                    lastLoadedUid = null
                    _isAdmin.value = false
                    _ownProfile.value = null
                    _showProfileDialog.value = false
                    if (_selectedTab.value is AppTab.Core &&
                        (_selectedTab.value as AppTab.Core).tab == CoreAppTab.ADMIN
                    ) {
                        _selectedTab.value = AppTab.Core(CoreAppTab.CHATBOX)
                    }
                } else if (lastLoadedUid != user.uid) {
                    lastLoadedUid = user.uid
                    refreshUserBootState(user)
                }
            }
        }
    }

    private fun syncSairaUserDirectory() {
        viewModelScope.launch {
            runCatching {
                combine(localUserProfile, chatboxThreads) { localSelf, threads ->
                    val selfEntry = SairaDirectoryUser(
                        uid = localSelf.uid,
                        name = localSelf.displayName,
                        shortId = localSelf.shortId,
                        about = localSelf.about,
                        isSelf = true
                    )
                    val peerEntries = threads
                        .filter { it.id != "saira" && !it.isGroup }
                        .map { t ->
                            SairaDirectoryUser(
                                uid = t.id,
                                name = t.name,
                                shortId = t.shortId,
                                about = t.workBio.ifBlank { t.role },
                                isSelf = false
                            )
                        }
                    com.example.saira.SairaUserDirectory.build(self = selfEntry, peers = peerEntries)
                }.distinctUntilChanged().collect { directory ->
                    prefs.saveSairaUserDirectory(directory)
                }
            }
        }
    }

    private fun refreshUserBootState(user: FirebaseUser) {
        viewModelScope.launch {
            val status = sparkAdminRepo.checkSparkAdminStatus(user.uid, user.email)
            val adminAllowed = status == AdminAuthStatus.OWNER ||
                status == AdminAuthStatus.DELEGATE ||
                status == AdminAuthStatus.INVITED_DELEGATE ||
                status == AdminAuthStatus.UNCLAIMED_OWNER
            _isAdmin.value = adminAllowed
        }
        viewModelScope.launch {
            liveChatRepo.ensureOwnProfile(user.uid, user.displayName)
                .onSuccess { profile -> _ownProfile.value = profile }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun selectThread(threadId: String) {
        _activeThreadId.value = threadId
    }

    fun openProfileDialog(open: Boolean) {
        _showProfileDialog.value = open
    }

    fun openUpdateDialog(open: Boolean) {
        _showUpdateDialog.value = open
    }

    fun updateOwnProfile(profile: UserProfile) {
        _ownProfile.value = profile
    }

    fun saveLocalUserProfile(displayName: String, about: String, phoneNumber: String) {
        val current = localUserProfile.value
        val cleanName = displayName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty { "Chatbox User" }
        val cleanAbout = about.trim().take(ChatboxConstants.MAX_ABOUT_LENGTH)
            .ifEmpty { LiveChatRepository.DEFAULT_ABOUT_TEXT }
        val cleanPhone = phoneNumber.trim().take(ChatboxConstants.MAX_PHONE_LENGTH)
        val updated = current.copy(
            displayName = cleanName,
            about = cleanAbout,
            phoneNumber = cleanPhone
        )
        viewModelScope.launch {
            prefs.saveLocalUserProfile(updated)
        }
    }

    fun retryEnsureOwnProfile() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            liveChatRepo.ensureOwnProfile(user.uid, user.displayName)
                .onSuccess { profile -> _ownProfile.value = profile }
        }
    }

    fun saveOwnAvatarFromUri(uri: Uri) {
        val avatarKey = currentUser.value?.uid ?: "local_user"
        val app = getApplication<Application>()
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = app.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                    val maxDim = 512
                    val maxSide = max(bounds.outWidth, bounds.outHeight)
                    var sample = 1
                    while (maxSide / sample > maxDim * 2) sample *= 2
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val decoded = cr.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOpts)
                    } ?: return@runCatching null

                    val scale = min(1f, maxDim.toFloat() / max(decoded.width, decoded.height))
                    val targetW = max(1, (decoded.width * scale).roundToInt())
                    val targetH = max(1, (decoded.height * scale).roundToInt())
                    val scaled = if (targetW != decoded.width || targetH != decoded.height) {
                        decoded.scale(targetW, targetH).also {
                            if (it != decoded) decoded.recycle()
                        }
                    } else {
                        decoded
                    }

                    val avatarsDir = File(app.filesDir, "avatars").apply { mkdirs() }
                    avatarsDir.listFiles { f -> f.name.startsWith("${avatarKey}_") }
                        ?.forEach { it.delete() }
                    val outFile = File(avatarsDir, "${avatarKey}_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { fos ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    }
                    scaled.recycle()
                    outFile.absolutePath
                }.getOrNull()
            }
            if (!savedPath.isNullOrBlank()) {
                prefs.saveUserAvatarPath(avatarKey, savedPath)
            }
        }
    }

    fun removeOwnAvatar() {
        val avatarKey = currentUser.value?.uid ?: "local_user"
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val avatarsDir = File(app.filesDir, "avatars")
                    avatarsDir.listFiles { f -> f.name.startsWith("${avatarKey}_") }
                        ?.forEach { it.delete() }
                }
            }
            prefs.saveUserAvatarPath(avatarKey, null)
        }
    }

    suspend fun copyMediaToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        runCatching {
            val cr = app.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val maxDim = 1280
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide / sample > maxDim * 2) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@runCatching null

            val scale = min(1f, maxDim.toFloat() / max(decoded.width, decoded.height))
            val targetW = max(1, (decoded.width * scale).roundToInt())
            val targetH = max(1, (decoded.height * scale).roundToInt())
            val scaled = if (targetW != decoded.width || targetH != decoded.height) {
                decoded.scale(targetW, targetH).also {
                    if (it != decoded) decoded.recycle()
                }
            } else decoded

            val mediaDir = File(app.filesDir, "chat_media").apply { mkdirs() }
            val outFile = File(mediaDir, "img_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            scaled.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    suspend fun persistVoiceFileToAppStorage(tempFile: File): String? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        runCatching {
            if (!tempFile.exists() || tempFile.length() == 0L) return@runCatching null
            val mediaDir = File(app.filesDir, "chat_voice").apply { mkdirs() }
            val outFile = File(mediaDir, "voice_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}.m4a")
            tempFile.copyTo(outFile, overwrite = true)
            tempFile.delete()
            outFile.absolutePath
        }.getOrNull()
    }

    fun setLoginLanguage(code: String) {
        viewModelScope.launch { prefs.setLoginLanguage(code) }
    }

    fun setQuickReaction(emoji: String) {
        viewModelScope.launch { prefs.setQuickReaction(emoji) }
    }

    fun setThreadQuickReaction(threadId: String, emoji: String) {
        val clean = emoji.trim().take(8).ifEmpty { "👍" }
        updateThread(threadId) { it.copy(quickReaction = clean) }
        viewModelScope.launch { prefs.setQuickReaction(clean) }
    }

    fun setOneMinAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSairaChatboxOneMinEnabled(enabled) }
    }

    fun setNotifAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSairaNotifReplyEnabled(enabled) }
    }

    fun setLiveLocationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSairaLiveLocationEnabled(enabled) }
    }

    fun setPeerLocationAllowed(peerUid: String, allowed: Boolean) {
        viewModelScope.launch { prefs.setPeerLocationAllowed(peerUid, allowed) }
    }

    fun setDmPeerNickname(peerUid: String, nickname: String) {
        viewModelScope.launch { prefs.setDmPeerNickname(peerUid, nickname) }
    }

    fun setDmPeerMuted(peerUid: String, muted: Boolean) {
        viewModelScope.launch { prefs.setDmPeerMuted(peerUid, muted) }
    }

    fun saveSairaRules(rules: List<SairaNotificationRule>) {
        viewModelScope.launch { prefs.saveSairaNotificationRules(rules) }
    }

    fun clearSairaAutoReplyLogs() {
        viewModelScope.launch { prefs.clearAutoReplyLogs() }
    }

    fun saveAiFallbackConfig(kindId: String, apiKey: String, baseUrl: String, model: String) {
        viewModelScope.launch {
            prefs.saveAiFallbackConfig(kindId, apiKey, baseUrl, model)
            SairaAiClient.resetCooldowns()
        }
    }

    fun sendChatboxMessage(
        threadId: String,
        body: String,
        timeString: String,
        kind: ChatboxMessageKind = ChatboxMessageKind.TEXT,
        imageUri: String? = null,
        audioPath: String? = null,
        audioDurationMs: Long? = null
    ) {
        val trimmed = body.trim().take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        if (trimmed.isEmpty() && imageUri.isNullOrBlank() && audioPath.isNullOrBlank()) return
        val currentMap = chatboxMessages.value.toMutableMap()
        val list = currentMap[threadId].orEmpty().toMutableList()
        val msgId = "msg_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        list.add(
            ChatboxMessage(
                id = msgId,
                from = "me",
                senderName = localUserProfile.value.displayName,
                text = trimmed,
                time = timeString,
                kind = kind,
                imageUri = imageUri,
                audioPath = audioPath,
                audioDurationMs = audioDurationMs
            )
        )
        if (threadId == "saira" && trimmed.isNotEmpty()) {
            list.add(
                ChatboxMessage(
                    id = "saira_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                    from = "them",
                    senderName = "Saira",
                    text = ChatboxCatalog.getSairaOfflineGuideReply(trimmed),
                    time = timeString,
                    kind = ChatboxMessageKind.TEXT
                )
            )
        }
        currentMap[threadId] = list
        updateThread(threadId) { it.copy(unreadCount = 0) }
        viewModelScope.launch { prefs.saveChatboxMessages(currentMap) }
    }

    fun sendChatboxImage(threadId: String, uri: Uri) {
        viewModelScope.launch {
            val savedPath = copyMediaToAppStorage(uri) ?: return@launch
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            sendChatboxMessage(
                threadId = threadId,
                body = "",
                timeString = now,
                kind = ChatboxMessageKind.IMAGE,
                imageUri = savedPath
            )
        }
    }

    fun sendChatboxVoice(threadId: String, tempFile: File, durationMs: Long) {
        viewModelScope.launch {
            val savedPath = persistVoiceFileToAppStorage(tempFile) ?: return@launch
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            sendChatboxMessage(
                threadId = threadId,
                body = "Voice note",
                timeString = now,
                kind = ChatboxMessageKind.VOICE,
                audioPath = savedPath,
                audioDurationMs = durationMs
            )
        }
    }

    fun openOrCreateDirectThread(name: String, identifier: String = ""): String {
        val cleanName = name.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH).ifEmpty { "New Contact" }
        val cleanId = identifier.trim()
        val existing = chatboxThreads.value.firstOrNull {
            !it.isGroup && (
                it.name.equals(cleanName, ignoreCase = true) ||
                    (cleanId.isNotEmpty() && (it.shortId == cleanId || it.phoneNumber == cleanId))
                )
        }
        if (existing != null) return existing.id

        val newId = "dm_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val shortId = if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(cleanId)) {
            cleanId
        } else {
            LiveChatRepository.deterministicSixDigitSeed(newId)
        }
        val phone = if (cleanId.any { it.isDigit() } && !ChatboxConstants.VALID_SHORT_ID_REGEX.matches(cleanId)) {
            cleanId
        } else ""
        val initials = cleanName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }.ifEmpty { cleanName.take(2).uppercase() }

        val thread = ChatboxThread(
            id = newId,
            name = cleanName,
            shortId = shortId,
            phoneNumber = phone,
            role = "Active now",
            connectionLabel = "You're connected on Chatbox",
            locationText = "On Chatbox",
            workBio = "Chatbox member · ID $shortId",
            avatarLabel = initials,
            style = AvatarStyle.entries[Random.nextInt(AvatarStyle.entries.size)],
            online = true,
            readReceiptsEnabled = true
        )
        val updated = listOf(thread) + chatboxThreads.value
        viewModelScope.launch { prefs.saveChatboxThreads(updated) }
        return newId
    }

    fun createGroupWithContact(baseThreadId: String, groupName: String, memberNames: List<String>): String {
        val baseThread = chatboxThreads.value.firstOrNull { it.id == baseThreadId }
        val cleanMembers = (listOfNotNull(baseThread?.name) + memberNames).map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val resolvedName = groupName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty {
                if (cleanMembers.size >= 2) "${cleanMembers.first()} & ${cleanMembers[1]}"
                else "Group with ${baseThread?.name ?: "Friends"}"
            }
        val newId = "grp_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val shortId = LiveChatRepository.deterministicSixDigitSeed(newId)
        val initials = resolvedName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }.ifEmpty { "GP" }
        val newGroup = ChatboxThread(
            id = newId,
            name = resolvedName,
            shortId = shortId,
            role = "Group · ${cleanMembers.size + 1} members",
            connectionLabel = "Group conversation on Chatbox",
            workBio = "Members: ${(listOf("You") + cleanMembers).joinToString(", ")}",
            avatarLabel = initials,
            style = AvatarStyle.BLUE,
            label = "GROUP",
            online = true,
            isGroup = true,
            members = cleanMembers,
            readReceiptsEnabled = true
        )
        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val updatedThreads = listOf(newGroup) + chatboxThreads.value
        val currentMap = chatboxMessages.value.toMutableMap()
        currentMap[newId] = listOf(
            ChatboxMessage(
                id = "grp_init_${System.currentTimeMillis()}",
                from = "them",
                senderName = cleanMembers.firstOrNull() ?: "Chatbox",
                text = "Group \"$resolvedName\" created with ${cleanMembers.joinToString(", ")}.",
                time = now
            )
        )
        viewModelScope.launch {
            prefs.saveChatboxThreads(updatedThreads)
            prefs.saveChatboxMessages(currentMap)
        }
        return newId
    }

    fun markThreadUnread(threadId: String, unread: Boolean) {
        updateThread(threadId) { it.copy(unreadCount = if (unread) max(1, it.unreadCount) else 0) }
    }

    fun setThreadReadReceipts(threadId: String, enabled: Boolean) {
        updateThread(threadId) { it.copy(readReceiptsEnabled = enabled) }
    }

    fun setThreadDisappearingTimer(threadId: String, key: String) {
        updateThread(threadId) { it.copy(disappearingKey = key) }
    }

    fun setThreadE2eeVerified(threadId: String, verified: Boolean) {
        updateThread(threadId) { it.copy(e2eeVerified = verified) }
    }

    fun unlockThreadHistoryWithPin(threadId: String, enteredPin: String): Boolean {
        val clean = enteredPin.filter { it.isDigit() }
        if (clean != chatboxBackupPin.value) return false

        updateThread(threadId) { it.copy(historyUnlocked = true) }
        val archived = ChatboxCatalog.archivedHistorySeeds[threadId].orEmpty()
        if (archived.isNotEmpty()) {
            val currentMap = chatboxMessages.value.toMutableMap()
            val existing = currentMap[threadId].orEmpty()
            val existingIds = existing.map { it.id }.toSet()
            val toPrepend = archived.filterNot { it.id in existingIds }
            if (toPrepend.isNotEmpty()) {
                currentMap[threadId] = toPrepend + existing
                viewModelScope.launch { prefs.saveChatboxMessages(currentMap) }
            }
        }
        return true
    }

    fun updateBackupPin(newPin: String) {
        viewModelScope.launch { prefs.saveChatboxBackupPin(newPin) }
    }

    fun toggleThreadBlocked(threadId: String) {
        val currentlyBlocked = threadId in chatboxBlockedThreads.value
        viewModelScope.launch { prefs.setChatboxThreadBlocked(threadId, !currentlyBlocked) }
    }

    fun unblockThread(threadId: String) {
        viewModelScope.launch { prefs.setChatboxThreadBlocked(threadId, false) }
    }

    fun submitThreadReport(threadId: String, peerName: String, reason: String, note: String) {
        val now = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
        val entry = ChatboxReportEntry(
            id = "rep_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            threadId = threadId,
            peerName = peerName,
            reason = reason.trim().ifEmpty { "Feedback / Report" },
            note = note.trim().take(500),
            timeLabel = now
        )
        viewModelScope.launch { prefs.appendChatboxReport(entry) }
    }

    fun logChatboxCall(
        threadId: String,
        peerName: String,
        isVideo: Boolean,
        isMissed: Boolean,
        durationLabel: String
    ) {
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val entry = ChatboxCallEntry(
            id = "call_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            threadId = threadId,
            peerName = peerName,
            isVideo = isVideo,
            isMissed = isMissed,
            timeLabel = "$dateStr · $timeStr",
            durationLabel = if (isMissed) "Missed" else durationLabel.ifBlank { "Ended" }
        )
        val updatedLogs = listOf(entry) + chatboxCallLogs.value

        val kind = when {
            isMissed && isVideo -> ChatboxMessageKind.MISSED_VIDEO_CALL
            isMissed -> ChatboxMessageKind.MISSED_AUDIO_CALL
            else -> ChatboxMessageKind.CALL_COMPLETED
        }
        val label = when {
            isMissed && isVideo -> "Missed video call"
            isMissed -> "Missed audio call"
            isVideo -> "Video call · ${durationLabel.ifBlank { "Ended" }}"
            else -> "Audio call · ${durationLabel.ifBlank { "Ended" }}"
        }
        val currentMap = chatboxMessages.value.toMutableMap()
        val list = currentMap[threadId].orEmpty().toMutableList()
        list.add(
            ChatboxMessage(
                id = "call_msg_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                from = if (isMissed) "them" else "me",
                senderName = if (isMissed) peerName else localUserProfile.value.displayName,
                text = label,
                time = dateStr,
                kind = kind
            )
        )
        currentMap[threadId] = list
        viewModelScope.launch {
            prefs.saveChatboxCallLogs(updatedLogs)
            prefs.saveChatboxMessages(currentMap)
        }
    }

    fun saveLocalSairaExchange(
        sessionId: String,
        title: String,
        question: String,
        answer: String
    ) {
        val current = localSairaSessions.value.toMutableList()
        val idx = current.indexOfFirst { it.id == sessionId }
        val newEx = LocalSairaExchange(
            id = "lex_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            question = question.trim().take(2000),
            answer = answer.trim().take(12000)
        )
        val nowMs = System.currentTimeMillis()
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = existing.copy(
                title = title.trim().take(120).ifEmpty { existing.title },
                lastPreview = answer.trim().take(240),
                updatedAtMs = nowMs,
                exchanges = existing.exchanges + newEx
            )
        } else {
            current.add(
                0,
                LocalSairaSessionData(
                    id = sessionId,
                    title = title.trim().take(120).ifEmpty { "Saira Chat" },
                    lastPreview = answer.trim().take(240),
                    updatedAtMs = nowMs,
                    exchanges = listOf(newEx)
                )
            )
        }
        viewModelScope.launch { prefs.saveLocalSairaSessions(current) }
    }

    fun deleteLocalSairaSession(sessionId: String) {
        val updated = localSairaSessions.value.filterNot { it.id == sessionId }
        viewModelScope.launch { prefs.saveLocalSairaSessions(updated) }
    }

    private fun updateThread(threadId: String, transform: (ChatboxThread) -> ChatboxThread) {
        val list = chatboxThreads.value.map { if (it.id == threadId) transform(it) else it }
        viewModelScope.launch { prefs.saveChatboxThreads(list) }
    }
}
