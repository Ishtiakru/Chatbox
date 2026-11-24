package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatboxCatalog
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiFallbackProviderKind
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
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
import com.example.saira.SairaLocationHelper
import com.example.saira.SairaNotificationMatcher
import com.example.saira.SairaNotificationRule
import com.example.saira.SairaPlatform
import com.example.saira.SairaTimeGreeting
import com.example.util.NetworkMonitor
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class SairaAutoReplyTestResult(
    val replied: Boolean,
    val senderName: String,
    val platformLabel: String,
    val replyText: String,
    val matchedRuleContact: String?,
    val usedAi: Boolean,
    val includedLocation: Boolean,
    val statusNote: String
)

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

    private val _pendingCreatedThreads = MutableStateFlow<List<ChatboxThread>>(emptyList())
    private val chatboxThreadsMutex = Mutex()

    val chatboxThreads: StateFlow<List<ChatboxThread>> = combine(
        prefs.chatboxThreads,
        _pendingCreatedThreads
    ) { stored, pending ->
        val storedIds = stored.map { it.id }.toSet()
        pending.filterNot { it.id in storedIds } + stored
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chatboxMessages: StateFlow<Map<String, List<ChatboxMessage>>> = prefs.chatboxMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val chatboxBackupPin: StateFlow<String> = prefs.chatboxBackupPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, "123456")

    val chatboxBlockedThreads: StateFlow<Set<String>> = prefs.chatboxBlockedThreads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val chatboxCallLogs: StateFlow<List<ChatboxCallEntry>> = prefs.chatboxCallLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chatboxReports: StateFlow<List<ChatboxReportEntry>> = prefs.chatboxReports
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localUserProfile: StateFlow<LocalUserProfile> = prefs.localUserProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalUserProfile())

    val localSairaSessions: StateFlow<List<LocalSairaSessionData>> = prefs.localSairaSessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasCompletedLogin: StateFlow<Boolean> = prefs.hasCompletedLogin
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val storedAccountIdentifier: StateFlow<String> = prefs.storedAccountIdentifier
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var lastLoadedUid: String? = null
    private val chatboxMessagesMutex = Mutex()
    private val pendingAbsenceJobs = ConcurrentHashMap<String, Job>()
    private val pendingIncomingAutoReplyJobs = ConcurrentHashMap<String, Job>()
    private val sairaAbsenceEngagedThreads = ConcurrentHashMap<String, Long>()

    internal var autoReplyWaitMs: Long = DEFAULT_AUTO_REPLY_WAIT_MS
    internal var continuationDebounceMs: Long = DEFAULT_CONTINUATION_WAIT_MS
    internal var aiCompleter: suspend (String, List<AiChatMessage>, String) -> String = { sys, hist, prompt ->
        SairaAiClient.complete(sys, hist, prompt)
    }
    internal var clockHourProvider: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    internal var onlineOverride: Boolean? = null

    private fun effectiveIsOnline(): Boolean = onlineOverride ?: isOnline.value

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

    fun enterAppWithoutLogin() {
        viewModelScope.launch {
            prefs.setHasCompletedLogin(true)
        }
    }

    suspend fun loginWithCredentials(identifier: String, password: String): Result<Unit> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your mobile number or email."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password must be at least 4 characters."))
        }
        val normalizedId = cleanId.lowercase(Locale.ROOT)
        val existingId = prefs.storedAccountIdentifier.first().lowercase(Locale.ROOT)
        val existingHash = prefs.storedAccountPasswordHash.first()
        val enteredHash = hashPassword(normalizedId, password)

        if (existingId.isNotEmpty() && existingId == normalizedId && existingHash.isNotEmpty() && existingHash != enteredHash) {
            return Result.failure(IllegalArgumentException("Incorrect password. Tap Forgot password? to reset."))
        }

        prefs.saveAccountCredentials(cleanId, enteredHash)
        syncLocalProfileFromAccount(nameInput = null, identifier = cleanId)
        return Result.success(Unit)
    }

    suspend fun createAccountWithCredentials(
        fullName: String,
        identifier: String,
        password: String
    ): Result<Unit> {
        val cleanName = fullName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
        val cleanId = identifier.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your full name."))
        }
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your mobile number or email."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Choose a password of at least 4 characters."))
        }

        val enteredHash = hashPassword(cleanId.lowercase(Locale.ROOT), password)
        prefs.saveAccountCredentials(cleanId, enteredHash)
        syncLocalProfileFromAccount(nameInput = cleanName, identifier = cleanId)
        return Result.success(Unit)
    }

    suspend fun resetAccountPassword(identifier: String, newPassword: String): Result<String> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your mobile number or email."))
        }
        if (newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Enter a new password of at least 4 characters."))
        }
        val emailSent = if (cleanId.contains("@") && isOnline.value) {
            authRepo.sendPasswordReset(cleanId).isSuccess
        } else {
            false
        }
        val newHash = hashPassword(cleanId.lowercase(Locale.ROOT), newPassword)
        prefs.saveAccountCredentials(cleanId, newHash)
        val msg = if (emailSent) {
            "Password updated and reset email sent to $cleanId."
        } else {
            "Password updated. You can now log in."
        }
        return Result.success(msg)
    }

    suspend fun signInWithGoogleFromLogin(activityContext: android.content.Context): com.example.data.repository.AuthResult {
        val result = authRepo.signInWithGoogleInteractive(activityContext)
        if (result is com.example.data.repository.AuthResult.Success) {
            prefs.setHasCompletedLogin(true)
            syncLocalProfileFromAccount(
                nameInput = result.user.displayName,
                identifier = result.user.email.orEmpty()
            )
        }
        return result
    }

    private suspend fun syncLocalProfileFromAccount(nameInput: String?, identifier: String) {
        val current = localUserProfile.value
        val cleanName = nameInput?.trim()?.takeIf { it.isNotEmpty() }
            ?: current.displayName.takeIf { it != "Chatbox User" }
            ?: identifier.substringBefore("@").trim()
                .replace('.', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                .ifEmpty { "Chatbox User" }
        val isPhone = identifier.none { it == '@' } && identifier.count { it.isDigit() } >= 6
        val updated = current.copy(
            displayName = cleanName.take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH),
            phoneNumber = if (isPhone) identifier.take(ChatboxConstants.MAX_PHONE_LENGTH) else current.phoneNumber,
            shortId = current.shortId.ifBlank {
                LiveChatRepository.deterministicSixDigitSeed(identifier.ifBlank { cleanName })
            }
        )
        prefs.saveLocalUserProfile(updated)
    }

    private fun hashPassword(normalizedIdentifier: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("chatbox:$normalizedIdentifier:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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
        if (!enabled) {
            pendingAbsenceJobs.values.forEach { it.cancel() }
            pendingAbsenceJobs.clear()
            sairaAbsenceEngagedThreads.clear()
        }
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
        if (muted) {
            pendingAbsenceJobs.remove(peerUid)?.cancel()
            pendingIncomingAutoReplyJobs.remove(peerUid)?.cancel()
        }
        viewModelScope.launch { prefs.setDmPeerMuted(peerUid, muted) }
    }

    fun saveSairaRules(rules: List<SairaNotificationRule>) {
        viewModelScope.launch {
            prefs.saveSairaNotificationRules(rules)
            if (rules.any { it.enabled }) {
                prefs.setSairaNotifReplyEnabled(true)
            }
        }
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

        // User replied manually -> cancel any pending incoming auto-reply for this thread.
        pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()

        val senderDisplayName = ownProfile.value?.displayName?.ifBlank { null }
            ?: localUserProfile.value.displayName
        val userMsg = ChatboxMessage(
            id = "msg_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            from = "me",
            senderName = senderDisplayName,
            text = trimmed,
            time = timeString,
            kind = kind,
            imageUri = imageUri,
            audioPath = audioPath,
            audioDurationMs = audioDurationMs
        )
        val thread = chatboxThreads.value.firstOrNull { it.id == threadId }

        updateThread(threadId) { it.copy(unreadCount = 0) }
        viewModelScope.launch {
            val historySnapshot = chatboxMessagesMutex.withLock {
                val map = prefs.chatboxMessages.first().toMutableMap()
                val updatedList = map[threadId].orEmpty() + userMsg
                map[threadId] = updatedList
                prefs.saveChatboxMessages(map)
                updatedList
            }

            if (threadId == "saira" && trimmed.isNotEmpty()) {
                val sairaReply = generateSairaDirectDmReply(
                    userMessage = trimmed,
                    historyMessages = historySnapshot.dropLast(1)
                )
                val nowStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                appendSairaMessageToThread(threadId, sairaReply, nowStr)
            } else if (
                threadId != "saira" &&
                trimmed.isNotEmpty() &&
                thread != null &&
                !thread.isGroup &&
                oneMinAutoReplyEnabled.value &&
                threadId !in chatboxBlockedThreads.value &&
                threadId !in dmMutedPeers.value
            ) {
                scheduleChatboxAbsenceAutoReply(
                    thread = thread,
                    senderName = senderDisplayName,
                    lastMessageId = userMsg.id,
                    lastMessageText = trimmed
                )
            }
        }
    }

    private suspend fun generateSairaDirectDmReply(
        userMessage: String,
        historyMessages: List<ChatboxMessage>
    ): String {
        if (effectiveIsOnline()) {
            val aiHistory = historyMessages
                .filter { it.kind == ChatboxMessageKind.TEXT && it.text.isNotBlank() }
                .takeLast(12)
                .map { msg ->
                    AiChatMessage(
                        role = if (msg.from == "me") "user" else "model",
                        text = msg.text.take(600)
                    )
                }
            val isFirstTurn = historyMessages.none { it.from == "me" }
            val prompt = SairaPrompts.buildTurnPrompt(
                question = userMessage,
                isFirstTurn = isFirstTurn,
                isContinuation = !isFirstTurn,
                hourOfDay = clockHourProvider()
            )
            val aiAnswer = try {
                withTimeoutOrNull(25_000L) {
                    aiCompleter(SairaPrompts.chatSystemInstruction, aiHistory, prompt)
                }?.trim().orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ""
            }
            if (aiAnswer.isNotEmpty()) return aiAnswer
        }
        return ChatboxCatalog.getSairaOfflineGuideReply(userMessage)
    }

    private fun scheduleChatboxAbsenceAutoReply(
        thread: ChatboxThread,
        senderName: String,
        lastMessageId: String,
        lastMessageText: String
    ) {
        pendingAbsenceJobs.remove(thread.id)?.cancel()
        val nowMs = System.currentTimeMillis()
        val engagedAt = sairaAbsenceEngagedThreads[thread.id]
        val isContinuation = engagedAt != null && (nowMs - engagedAt) <= ACTIVE_SAIRA_WINDOW_MS

        val job = viewModelScope.launch {
            val waitDelay = if (isContinuation) continuationDebounceMs else autoReplyWaitMs
            delay(waitDelay)

            if (!oneMinAutoReplyEnabled.value) return@launch
            if (thread.id in chatboxBlockedThreads.value || thread.id in dmMutedPeers.value) return@launch

            val currentMessages = prefs.chatboxMessages.first()[thread.id].orEmpty()
            val latest = currentMessages.lastOrNull() ?: return@launch
            // Cancel if peer already replied or user sent a newer message tracked by another job
            if (latest.from != "me" || latest.id != lastMessageId) return@launch

            val result = evaluateAutoReplyInternal(
                senderName = senderName,
                platform = SairaPlatform.ANY,
                incomingText = lastMessageText,
                peerNameForAbsence = thread.name,
                peerIdForLocation = thread.id,
                isChatboxAbsence = true,
                forceContinuation = isContinuation,
                recentMessagesForHistory = currentMessages
            )
            if (!result.replied || result.replyText.isBlank()) return@launch

            val replyTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            appendSairaMessageToThread(thread.id, result.replyText, replyTimeStr)
            sairaAbsenceEngagedThreads[thread.id] = System.currentTimeMillis()
            prefs.appendAutoReplyLog(
                SairaAutoReplyLog(
                    id = "log_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                    contactName = thread.name,
                    platformLabel = if (result.matchedRuleContact != null) "Chatbox · Rule" else "Chatbox 10s",
                    incomingPreview = lastMessageText.take(120),
                    sentReply = result.replyText.take(280),
                    includedLocation = result.includedLocation,
                    timestampMs = System.currentTimeMillis()
                )
            )
            pendingAbsenceJobs.remove(thread.id)
        }
        pendingAbsenceJobs[thread.id] = job
    }

    /**
     * Simulates or delivers an incoming DM from a friend on [threadId].
     * When [triggerAutoReplyAfterWait] is true, Saira waits [autoReplyWaitMs] (10s default)
     * and if the owner has not replied manually, applies taught contact rules / 10s greeting,
     * permission-gated location sharing, and AI + fallback reply.
     */
    fun simulateIncomingChatboxMessage(
        threadId: String,
        body: String,
        triggerAutoReplyAfterWait: Boolean = true
    ) {
        val trimmed = body.trim().take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        if (trimmed.isEmpty()) return
        val thread = chatboxThreads.value.firstOrNull { it.id == threadId } ?: return
        if (thread.id == "saira" || thread.isGroup) return

        pendingAbsenceJobs.remove(threadId)?.cancel()
        pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val incomingMsg = ChatboxMessage(
            id = "inc_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            from = "them",
            senderName = thread.name,
            text = trimmed,
            time = timeStr,
            kind = ChatboxMessageKind.TEXT
        )

        viewModelScope.launch {
            chatboxMessagesMutex.withLock {
                val map = prefs.chatboxMessages.first().toMutableMap()
                map[threadId] = map[threadId].orEmpty() + incomingMsg
                prefs.saveChatboxMessages(map)
            }
            if (!triggerAutoReplyAfterWait) return@launch
            if (threadId in chatboxBlockedThreads.value || threadId in dmMutedPeers.value) return@launch

            val job = viewModelScope.launch {
                val rules = prefs.sairaNotificationRules.first()
                val matchedRule = SairaNotificationMatcher.findInAppRule(thread.name, SairaPlatform.ANY, rules)
                val shouldRun = matchedRule != null || prefs.sairaChatboxOneMinEnabled.first()
                if (!shouldRun) return@launch

                delay(autoReplyWaitMs)

                if (threadId in chatboxBlockedThreads.value || threadId in dmMutedPeers.value) return@launch
                val currentMessages = prefs.chatboxMessages.first()[threadId].orEmpty()
                val latest = currentMessages.lastOrNull() ?: return@launch
                // Owner already replied manually in time -> do not auto-reply!
                if (latest.id != incomingMsg.id || latest.from == "me") return@launch

                val result = evaluateAutoReplyInternal(
                    senderName = thread.name,
                    platform = SairaPlatform.ANY,
                    incomingText = trimmed,
                    peerNameForAbsence = ownProfile.value?.displayName ?: localUserProfile.value.displayName,
                    peerIdForLocation = threadId,
                    isChatboxAbsence = false,
                    forceContinuation = false,
                    recentMessagesForHistory = currentMessages
                )
                if (!result.replied || result.replyText.isBlank()) return@launch

                val replyTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                appendSairaMessageToThread(threadId, result.replyText, replyTimeStr)
                prefs.appendAutoReplyLog(
                    SairaAutoReplyLog(
                        id = "log_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                        contactName = thread.name,
                        platformLabel = result.platformLabel,
                        incomingPreview = trimmed.take(120),
                        sentReply = result.replyText.take(280),
                        includedLocation = result.includedLocation,
                        timestampMs = System.currentTimeMillis()
                    )
                )
                pendingIncomingAutoReplyJobs.remove(threadId)
            }
            pendingIncomingAutoReplyJobs[threadId] = job
        }
    }

    /**
     * Runs the full Saira auto-reply evaluation pipeline (taught contact rules, time-of-day
     * greeting, 10-second Chatbox absence fallback, location permission + consent gating,
     * and Gemini / Backup AI with deterministic fallback) and records the result in logs.
     */
    suspend fun testAutoReply(
        senderName: String,
        platform: SairaPlatform = SairaPlatform.ANY,
        incomingText: String,
        recordLog: Boolean = true
    ): SairaAutoReplyTestResult {
        val cleanSender = senderName.trim().ifEmpty { "Sazzad Jony" }
        val cleanIncoming = incomingText.trim().ifEmpty { "কেমন আছো?" }
        val selfName = ownProfile.value?.displayName?.ifBlank { null }
            ?: localUserProfile.value.displayName
        val result = evaluateAutoReplyInternal(
            senderName = cleanSender,
            platform = platform,
            incomingText = cleanIncoming,
            peerNameForAbsence = selfName,
            peerIdForLocation = null,
            isChatboxAbsence = false,
            forceContinuation = false,
            recentMessagesForHistory = emptyList()
        )
        if (result.replied && result.replyText.isNotBlank() && recordLog) {
            prefs.appendAutoReplyLog(
                SairaAutoReplyLog(
                    id = "log_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                    contactName = cleanSender,
                    platformLabel = result.platformLabel,
                    incomingPreview = cleanIncoming.take(120),
                    sentReply = result.replyText.take(280),
                    includedLocation = result.includedLocation,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    private suspend fun evaluateAutoReplyInternal(
        senderName: String,
        platform: SairaPlatform,
        incomingText: String,
        peerNameForAbsence: String,
        peerIdForLocation: String?,
        isChatboxAbsence: Boolean,
        forceContinuation: Boolean,
        recentMessagesForHistory: List<ChatboxMessage>
    ): SairaAutoReplyTestResult {
        val app = getApplication<Application>()
        val rules = prefs.sairaNotificationRules.first()
        val oneMinEnabled = prefs.sairaChatboxOneMinEnabled.first()
        val liveLocEnabled = prefs.sairaLiveLocationEnabled.first()
        val allowedPeers = prefs.sairaLiveLocationAllowedPeers.first()
        val hasLocPerm = SairaLocationHelper.hasLocationPermission(app)
        val chatboxUsers = prefs.sairaUserDirectory.first()
        val recentLogs = prefs.sairaAutoReplyLogs.first()
        val hour = clockHourProvider()

        val matchedRule = SairaNotificationMatcher.findInAppRule(
            contactName = senderName,
            platform = platform,
            rules = rules
        )

        val wantsLocation = SairaTimeGreeting.isLocationRequest(incomingText)
        val peerExplicitlyAllowedLoc = (matchedRule?.allowLocationShare == true) ||
            (peerIdForLocation != null && peerIdForLocation in allowedPeers)
        val locationPermitted = wantsLocation && liveLocEnabled && hasLocPerm && peerExplicitlyAllowedLoc
        val locationSnapshot = if (locationPermitted) {
            SairaLocationHelper.getBestAvailableLocation(app)
        } else {
            null
        }
        val locationNote = when {
            !wantsLocation -> ""
            !liveLocEnabled -> " (লোকেশন মাস্টার সুইচ বন্ধ থাকায় লোকেশন পাঠানো হয়নি)"
            !hasLocPerm -> " (Location Permission না থাকায় লোকেশন পাঠানো হয়নি)"
            !peerExplicitlyAllowedLoc -> " (এই ব্যক্তির জন্য লোকেশন শেয়ার অনুমতি বন্ধ)"
            locationSnapshot == null -> " (লোকেশন অনুমতি আছে, তবে ডিভাইসে সাম্প্রতিক GPS ফিক্স পাওয়া যায়নি)"
            else -> " (লাইভ লোকেশন সংযুক্ত হয়েছে)"
        }

        if (matchedRule != null && !isChatboxAbsence) {
            val cutoffMs = System.currentTimeMillis() - ACTIVE_SAIRA_WINDOW_MS
            val priorRuleLogs = recentLogs.filter {
                it.contactName.equals(senderName, ignoreCase = true) &&
                    it.timestampMs >= cutoffMs &&
                    it.incomingPreview.isNotBlank() &&
                    it.sentReply.isNotBlank()
            }
            val isContinuation = forceContinuation || priorRuleLogs.isNotEmpty()
            var usedAi = false
            val replyText = if (!isContinuation) {
                SairaNotificationMatcher.buildFirstBoxReply(
                    senderName = senderName,
                    rule = matchedRule,
                    locationSnapshot = locationSnapshot,
                    hourOfDay = hour
                )
            } else if (matchedRule.useAiReply && effectiveIsOnline()) {
                val history = priorRuleLogs.take(7).asReversed().flatMap { entry ->
                    listOf(
                        AiChatMessage("user", entry.incomingPreview),
                        AiChatMessage("model", entry.sentReply)
                    )
                }
                val prompt = SairaPrompts.buildNotificationAutoReplyPrompt(
                    senderName = senderName,
                    platformLabel = platform.label,
                    incomingMessage = incomingText,
                    taughtGuidance = matchedRule.taughtReplyGuidance,
                    locationText = locationSnapshot?.toShareText(),
                    chatboxUsers = chatboxUsers,
                    hourOfDay = hour,
                    isContinuation = true
                )
                val aiAttempt = try {
                    withTimeoutOrNull(20_000L) {
                        aiCompleter(SairaPrompts.autoReplySystemInstruction, history, prompt)
                    }?.trim().orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
                if (aiAttempt.isNotEmpty()) {
                    usedAi = true
                    if (locationSnapshot != null && !aiAttempt.contains("maps.google.com")) {
                        "$aiAttempt\n${locationSnapshot.toShareText()}"
                    } else {
                        aiAttempt
                    }
                } else {
                    SairaNotificationMatcher.buildDeterministicReply(
                        senderName = senderName,
                        rule = matchedRule,
                        locationSnapshot = locationSnapshot,
                        hourOfDay = hour,
                        isContinuation = true,
                        incomingText = incomingText
                    )
                }
            } else {
                SairaNotificationMatcher.buildDeterministicReply(
                    senderName = senderName,
                    rule = matchedRule,
                    locationSnapshot = locationSnapshot,
                    hourOfDay = hour,
                    isContinuation = true,
                    incomingText = incomingText
                )
            }

            val turnLabel = if (isContinuation) "Follow-up Turn" else "1st Box Reply"
            return SairaAutoReplyTestResult(
                replied = true,
                senderName = senderName,
                platformLabel = "${platform.label} · Rule",
                replyText = replyText,
                matchedRuleContact = matchedRule.contactName,
                usedAi = usedAi,
                includedLocation = locationSnapshot != null,
                statusNote = "নিয়ম মিলেছে (${matchedRule.contactName} · $turnLabel)$locationNote"
            )
        }

        if (oneMinEnabled || isChatboxAbsence) {
            var usedAi = false
            val aiAttempt = if (effectiveIsOnline() && matchedRule?.useAiReply != false) {
                val history = recentMessagesForHistory
                    .filter { it.kind == ChatboxMessageKind.TEXT && it.text.isNotBlank() }
                    .takeLast(8)
                    .map { msg ->
                        AiChatMessage(
                            role = if (msg.from == "me") "user" else "model",
                            text = msg.text.take(350)
                        )
                    }
                val prompt = SairaPrompts.buildOneMinAbsencePrompt(
                    senderName = senderName,
                    peerName = peerNameForAbsence,
                    lastMessageText = incomingText,
                    hourOfDay = hour,
                    isContinuation = forceContinuation
                )
                try {
                    withTimeoutOrNull(20_000L) {
                        aiCompleter(SairaPrompts.chatSystemInstruction, history, prompt)
                    }?.trim().orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }

            val baseReply = if (aiAttempt.isNotEmpty()) {
                usedAi = true
                aiAttempt
            } else if (matchedRule != null) {
                val info = SairaTimeGreeting.infoForHour(hour)
                if (forceContinuation) {
                    "বুঝেছি $senderName, $peerNameForAbsence ফিরলেই আপনার \"${incomingText.take(60)}\" বার্তাটি দেখবেন।"
                } else {
                    "হ্যালো $senderName! ${info.bengaliGreeting} ✨ ${matchedRule.taughtReplyGuidance}"
                }
            } else if (forceContinuation) {
                "বুঝেছি $senderName, $peerNameForAbsence এখনো অফলাইনে/ব্যস্ত আছেন—আমি আপনার কথা নোট করে রাখছি, আর কিছু জানতে চাইলে বলুন।"
            } else {
                SairaTimeGreeting.buildOneMinuteAbsenceFallback(
                    senderName = senderName,
                    peerName = peerNameForAbsence,
                    hourOfDay = hour
                )
            }

            val finalReply = if (locationSnapshot != null && !baseReply.contains("maps.google.com")) {
                "$baseReply\n${locationSnapshot.toShareText()}"
            } else {
                baseReply
            }

            return SairaAutoReplyTestResult(
                replied = true,
                senderName = senderName,
                platformLabel = if (matchedRule != null) "Chatbox 10s · Rule" else "Chatbox 10s",
                replyText = finalReply,
                matchedRuleContact = matchedRule?.contactName,
                usedAi = usedAi,
                includedLocation = locationSnapshot != null,
                statusNote = (if (matchedRule != null) {
                    "১০-সেকেন্ড অপেক্ষা + নিয়ম (${matchedRule.contactName}) সক্রিয়"
                } else {
                    "১০-সেকেন্ড চ্যাটবক্স অপেক্ষা উত্তর প্রস্তুত"
                }) + locationNote
            )
        }

        return SairaAutoReplyTestResult(
            replied = false,
            senderName = senderName,
            platformLabel = platform.label,
            replyText = "",
            matchedRuleContact = null,
            usedAi = false,
            includedLocation = false,
            statusNote = "$senderName-এর জন্য কোনো সক্রিয় নিয়ম নেই এবং ১০-সেকেন্ড অপেক্ষা সুইচ বন্ধ আছে।"
        )
    }

    private suspend fun appendSairaMessageToThread(
        threadId: String,
        replyText: String,
        timeString: String
    ) {
        val clean = replyText.trim()
        if (clean.isEmpty()) return
        val sairaMsg = ChatboxMessage(
            id = "saira_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            from = "them",
            senderName = "Saira",
            text = clean,
            time = timeString,
            kind = ChatboxMessageKind.TEXT
        )
        chatboxMessagesMutex.withLock {
            val map = prefs.chatboxMessages.first().toMutableMap()
            map[threadId] = map[threadId].orEmpty() + sairaMsg
            prefs.saveChatboxMessages(map)
        }
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
        val rawName = name.trim()
        val rawId = identifier.trim()

        // If the user typed a 6-digit ID or phone number into the name field with identifier blank,
        // promote it to identifier automatically so 6-digit lookup always works.
        val resolvedIdentifier = when {
            rawId.isNotEmpty() -> rawId
            ChatboxConstants.VALID_SHORT_ID_REGEX.matches(rawName) -> rawName
            rawName.filter { it.isDigit() }.length >= 7 && rawName.none { it.isLetter() } -> rawName
            else -> ""
        }
        val cleanNameInput = if (resolvedIdentifier == rawName && rawId.isEmpty()) "" else rawName
        val isSixDigitId = ChatboxConstants.VALID_SHORT_ID_REGEX.matches(resolvedIdentifier)
        val phoneDigits = resolvedIdentifier.filter { it.isDigit() }
        val isPhone = !isSixDigitId && phoneDigits.length >= 7

        val cleanName = cleanNameInput
            .take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
            .ifEmpty {
                when {
                    isSixDigitId -> "Chatbox Friend ($resolvedIdentifier)"
                    isPhone -> "Chatbox Friend (${phoneDigits.takeLast(4)})"
                    resolvedIdentifier.isNotEmpty() -> resolvedIdentifier.take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
                    else -> "Chatbox Friend"
                }
            }

        val existing = chatboxThreads.value.firstOrNull { thread ->
            !thread.isGroup && (
                (cleanNameInput.isNotEmpty() && thread.name.equals(cleanNameInput, ignoreCase = true)) ||
                    (isSixDigitId && thread.shortId == resolvedIdentifier) ||
                    (isPhone && thread.phoneNumber.filter { it.isDigit() } == phoneDigits)
                )
        }
        if (existing != null) return existing.id

        val newId = "dm_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val shortId = if (isSixDigitId) {
            resolvedIdentifier
        } else {
            LiveChatRepository.deterministicSixDigitSeed(
                resolvedIdentifier.ifBlank { newId }
            )
        }
        val phone = if (isPhone) resolvedIdentifier else ""
        val initials = if (isSixDigitId && cleanNameInput.isEmpty()) {
            resolvedIdentifier.take(2)
        } else {
            cleanName.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.take(1).uppercase() }.ifEmpty { cleanName.take(2).uppercase() }
        }

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
        _pendingCreatedThreads.value = listOf(thread) + _pendingCreatedThreads.value.filterNot { it.id == newId }
        viewModelScope.launch {
            chatboxThreadsMutex.withLock {
                val currentStored = prefs.chatboxThreads.first()
                if (currentStored.none { it.id == newId }) {
                    prefs.saveChatboxThreads(listOf(thread) + currentStored)
                }
                _pendingCreatedThreads.value = _pendingCreatedThreads.value.filterNot { it.id == newId }
            }
            if (isSixDigitId && cleanNameInput.isEmpty() && currentUser.value != null && isOnline.value) {
                val peerProfile = liveChatRepo.lookupPeerByShortId(resolvedIdentifier)
                if (peerProfile != null && peerProfile.displayName.isNotBlank()) {
                    val peerInitials = peerProfile.displayName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.take(1).uppercase() }
                        .ifEmpty { peerProfile.displayName.take(2).uppercase() }
                    updateThread(newId) { existingThread ->
                        existingThread.copy(
                            name = peerProfile.displayName,
                            avatarLabel = peerInitials,
                            workBio = peerProfile.about.ifBlank { existingThread.workBio }
                        )
                    }
                }
            }
        }
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
        _pendingCreatedThreads.value = listOf(newGroup) + _pendingCreatedThreads.value.filterNot { it.id == newId }
        val initMsg = ChatboxMessage(
            id = "grp_init_${System.currentTimeMillis()}",
            from = "them",
            senderName = cleanMembers.firstOrNull() ?: "Chatbox",
            text = "Group \"$resolvedName\" created with ${cleanMembers.joinToString(", ")}.",
            time = now
        )
        viewModelScope.launch {
            chatboxThreadsMutex.withLock {
                val currentStored = prefs.chatboxThreads.first()
                if (currentStored.none { it.id == newId }) {
                    prefs.saveChatboxThreads(listOf(newGroup) + currentStored)
                }
                _pendingCreatedThreads.value = _pendingCreatedThreads.value.filterNot { it.id == newId }
            }
            chatboxMessagesMutex.withLock {
                val currentMap = prefs.chatboxMessages.first().toMutableMap()
                currentMap[newId] = currentMap[newId].orEmpty() + initMsg
                prefs.saveChatboxMessages(currentMap)
            }
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
        if (!currentlyBlocked) {
            pendingAbsenceJobs.remove(threadId)?.cancel()
            pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()
            sairaAbsenceEngagedThreads.remove(threadId)
        }
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
        _pendingCreatedThreads.value = _pendingCreatedThreads.value.map {
            if (it.id == threadId) transform(it) else it
        }
        viewModelScope.launch {
            chatboxThreadsMutex.withLock {
                val list = prefs.chatboxThreads.first().map { if (it.id == threadId) transform(it) else it }
                prefs.saveChatboxThreads(list)
            }
        }
    }

    companion object {
        const val DEFAULT_AUTO_REPLY_WAIT_MS = 10_000L
        const val DEFAULT_CONTINUATION_WAIT_MS = 1_500L
        private const val ACTIVE_SAIRA_WINDOW_MS = 15 * 60_000L
    }
}
