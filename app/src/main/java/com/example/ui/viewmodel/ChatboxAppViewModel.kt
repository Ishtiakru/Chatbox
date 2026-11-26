package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatboxCatalog
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiSafeResponses
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
import com.example.data.model.LiveMessage
import com.example.data.model.PeerSearchState
import com.example.data.model.LocalUserProfile
import com.example.data.model.ScheduledCallEntry
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkDynamicConfig
import com.example.data.model.UserProfile
import com.example.data.repository.AppPreferencesRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.SairaSessionRepository
import com.example.util.ChatboxNotifier
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

/** Live call session shown by the global call dialogs (signaling rides Firestore messages). */
data class ActiveChatboxCall(
    val callId: String,
    val threadId: String,
    val peerName: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    /** "ringing" until the callee accepts, then "connected". */
    val phase: String,
    val connectedAtMs: Long
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

    val sairaOffThreads: StateFlow<Set<String>> = prefs.sairaOffThreads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** DM settings switch: Saira answers for me in this chat (only while I'm away). */
    fun setThreadSairaEnabled(threadId: String, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setThreadSairaOff(threadId, !enabled)
            if (enabled && !prefs.sairaChatboxOneMinEnabled.first()) prefs.setSairaChatboxOneMinEnabled(true)
            if (!enabled) pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()
        }
    }

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
    private val attachMutex = Mutex()

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

    val chatboxFavoriteThreads: StateFlow<Set<String>> = prefs.chatboxFavoriteThreads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val chatboxScheduledCalls: StateFlow<List<ScheduledCallEntry>> = prefs.chatboxScheduledCalls
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

    // Declared BEFORE init {}: stopLiveSync() runs from the init uid collector.
    // ---------- Call signaling: rides the live-message pipeline (no new rules) ----------
    private val _activeCall = MutableStateFlow<ActiveChatboxCall?>(null)
    val activeCall: StateFlow<ActiveChatboxCall?> = _activeCall.asStateFlow()

    /** Real audio/video media for the active call (WebRTC); null when no call. */
    private val _callEngine = MutableStateFlow<com.example.call.CallEngine?>(null)
    val callEngine: StateFlow<com.example.call.CallEngine?> = _callEngine.asStateFlow()
    private val callSdpChunks = java.util.concurrent.ConcurrentHashMap<String, Array<String?>>()
    private val callSdpDone: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile private var pendingOfferSdp: Pair<String, String>? = null
    /** Finished calls: a re-delivered old "start" signal must never ring again. */
    private val endedCallIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var lastLoadedUid: String? = null
    private val chatboxMessagesMutex = Mutex()
    private val pendingAbsenceJobs = ConcurrentHashMap<String, Job>()
    private val pendingIncomingAutoReplyJobs = ConcurrentHashMap<String, Job>()
    private val sairaAbsenceEngagedThreads = ConcurrentHashMap<String, Long>()

    // Live (Firestore) DM sync state — declared before init{} (init reads it).
    private var liveConversationsJob: Job? = null
    private val liveMessageJobs = ConcurrentHashMap<String, Job>()
    private val liveAcceptedConversations: MutableSet<String> = ConcurrentHashMap.newKeySet()

    internal var autoReplyWaitMs: Long = DEFAULT_AUTO_REPLY_WAIT_MS
    internal var continuationDebounceMs: Long = DEFAULT_CONTINUATION_WAIT_MS
    internal var aiCompleter: suspend (String, List<AiChatMessage>, String) -> String = { sys, hist, prompt ->
        SairaAiClient.complete(sys, hist, prompt)
    }
    internal var clockHourProvider: () -> Int = { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    internal var onlineOverride: Boolean? = null

    private fun effectiveIsOnline(): Boolean = onlineOverride ?: isOnline.value

    /** Messages that came while the owner was on line; answered if they leave without replying. */
    private val deferredWhileOnline = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /** Owner left Chatbox: answer every message they saw but didn't reply to. */
    private fun replyToPendingAfterOwnerLeft() {
        viewModelScope.launch {
            val messages = prefs.chatboxMessages.first()
            val threads = chatboxThreads.value
            for ((threadId, pair) in deferredWhileOnline.entries.toList()) {
                val latest = messages[threadId].orEmpty().lastOrNull()
                if (latest == null || latest.id != pair.first || latest.from != "them") {
                    deferredWhileOnline.remove(threadId)
                    continue
                }
                val thread = threads.firstOrNull { it.id == threadId } ?: continue
                scheduleIncomingAutoReply(thread, pair.first, pair.second)
            }
        }
    }

    /** Owner is using Chatbox right now (app on screen). Tests can override. */
    internal var ownerOnlineOverride: Boolean? = null
    private fun ownerOnline(): Boolean = ownerOnlineOverride ?: com.example.util.AppPresence.foreground

    init {
        syncSairaUserDirectory()
        viewModelScope.launch {
            com.example.util.AppPresence.foregroundFlow.collect { visible ->
                if (!visible) replyToPendingAfterOwnerLeft()
            }
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) {
                    lastLoadedUid = null
                    stopLiveSync()
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
                    startLiveSync(user.uid)
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
        if (phoneSignInInProgress) return // completePhoneSignIn() writes the profile itself
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

    /** Debug/test builds only (the release login screen has no skip button). */
    fun enterAppWithoutLogin() {
        viewModelScope.launch {
            prefs.setHasCompletedLogin(true)
        }
    }

    /**
     * True while a create-account / forgot-password flow is running: the SMS code
     * signs the user in before the password is set, and the app must not open
     * until the password is saved.
     */
    private val _authFlowActive = MutableStateFlow(false)
    val authFlowActive: StateFlow<Boolean> = _authFlowActive.asStateFlow()

    /** Called when the SMS-code step is cancelled: never leave a password-less session. */
    fun cancelAuthFlow() {
        viewModelScope.launch {
            val user = authRepo.currentUser()
            if (_authFlowActive.value && user != null && !authRepo.hasPasswordOrGoogle(user)) {
                authRepo.signOut()
            }
            _authFlowActive.value = false
        }
    }

    private fun isPhoneIdentifier(id: String): Boolean =
        !id.contains("@") && id.count { it.isDigit() } >= 6

    /** Log in = mobile number (or email) + password, checked by Firebase. */
    suspend fun loginWithCredentials(identifier: String, password: String): Result<Unit> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("মোবাইল নম্বর লিখুন।"))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড লিখুন।"))
        }
        return when (val r = authRepo.signInWithPassword(cleanId, password)) {
            is com.example.data.repository.AuthResult.Success -> {
                _authFlowActive.value = false
                lastLoginPassword = password
                refreshKnownGmails()
                // Every number + password login asks for a Gmail again (any one: old or new).
                _gmailRequired.value = true
                prefs.setHasCompletedLogin(true)
                viewModelScope.launch { runCatching { gmailRecovery.refreshSecrets(password) } }
                syncLocalProfileFromAccount(nameInput = r.user.displayName, identifier = r.user.phoneNumber ?: cleanId)
                liveChatRepo.ensureOwnProfile(r.user.uid, r.user.displayName)
                    .onSuccess { profile -> _ownProfile.value = profile }
                Result.success(Unit)
            }
            is com.example.data.repository.AuthResult.Failure -> Result.failure(IllegalArgumentException(r.message))
            com.example.data.repository.AuthResult.Cancelled -> Result.failure(IllegalArgumentException("Login বাতিল হয়েছে।"))
        }
    }

    /**
     * Create account. Mobile number: the number must have just been verified by SMS
     * code (the login screen does that first); then the password is attached to that
     * account. Email: Firebase email/password account + verification email.
     */
    suspend fun createAccountWithCredentials(
        fullName: String,
        identifier: String,
        password: String
    ): Result<Unit> {
        val cleanName = fullName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
        val cleanId = identifier.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("আপনার নাম লিখুন।"))
        }
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("মোবাইল নম্বর লিখুন।"))
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড কমপক্ষে $MIN_PASSWORD_LENGTH অক্ষরের দিন।"))
        }
        if (isPhoneIdentifier(cleanId)) {
            val e164 = com.example.data.repository.PhoneNumbers.toE164(cleanId)
                ?: return Result.failure(IllegalArgumentException("সঠিক মোবাইল নম্বর লিখুন, যেমন: 01712345678"))
            return when (val r = authRepo.createPhonePasswordAccount(cleanId, password)) {
                is com.example.data.repository.AuthResult.Success -> {
                    authRepo.updateDisplayName(cleanName)
                    lastLoginPassword = password
                    refreshKnownGmails()
                    _gmailRequired.value = true
                    prefs.setHasCompletedLogin(true)
                    completePhoneSignIn(r.user, cleanName, phoneOverride = e164)
                    _authFlowActive.value = false
                    Result.success(Unit)
                }
                is com.example.data.repository.AuthResult.Failure -> Result.failure(IllegalArgumentException(r.message))
                com.example.data.repository.AuthResult.Cancelled -> Result.failure(IllegalArgumentException("বাতিল হয়েছে।"))
            }
        }
        return when (val r = authRepo.createEmailAccount(cleanId, password)) {
            is com.example.data.repository.AuthResult.Success -> {
                authRepo.updateDisplayName(cleanName)
                prefs.setHasCompletedLogin(true)
                syncLocalProfileFromAccount(nameInput = cleanName, identifier = cleanId)
                liveChatRepo.ensureOwnProfile(r.user.uid, cleanName).onSuccess { _ownProfile.value = it }
                _authFlowActive.value = false
                Result.success(Unit)
            }
            is com.example.data.repository.AuthResult.Failure -> Result.failure(IllegalArgumentException(r.message))
            com.example.data.repository.AuthResult.Cancelled -> Result.failure(IllegalArgumentException("বাতিল হয়েছে।"))
        }
    }

    /**
     * Forgot password. Mobile number: only after the SMS code proved ownership of the
     * number (done by the login screen first). Email: Firebase reset link.
     */
    /**
     * Forgot password (no SMS): new password is saved only after the user picks the
     * Gmail that was linked to this number's account.
     */
    suspend fun resetAccountPassword(
        activityContext: android.content.Context,
        identifier: String,
        newPassword: String
    ): Result<String> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) {
            return Result.failure(IllegalArgumentException("মোবাইল নম্বর লিখুন।"))
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return Result.failure(IllegalArgumentException("নতুন পাসওয়ার্ড কমপক্ষে $MIN_PASSWORD_LENGTH অক্ষরের দিন।"))
        }
        _authFlowActive.value = true
        try {
            val user = authRepo.resetPasswordWithGmail(activityContext, cleanId, newPassword)
                .getOrElse { return Result.failure(IllegalArgumentException(it.message)) }
            lastLoginPassword = newPassword
            refreshKnownGmails()
            _gmailRequired.value = false
            prefs.setHasCompletedLogin(true)
            syncLocalProfileFromAccount(nameInput = user.displayName, identifier = user.phoneNumber ?: cleanId)
            liveChatRepo.ensureOwnProfile(user.uid, user.displayName).onSuccess { _ownProfile.value = it }
            return Result.success("পাসওয়ার্ড বদলে গেছে।")
        } finally {
            _authFlowActive.value = false
        }
    }

    // ---- Gmail (required once after login) -------------------------------

    val gmailRecovery by lazy { com.example.data.repository.GmailRecoveryRepository(getApplication()) }

    /** Password typed at the last login (memory only) so a newly chosen Gmail can reset it later. */
    private var lastLoginPassword: String? = null

    private fun needsGmail(user: FirebaseUser?): Boolean =
        user != null && !authRepo.hasGoogle(user) && gmailRecovery.localGmails(user.uid).isEmpty()

    /** Shown after every number + password login, and whenever the account has no Gmail yet. */
    private val _gmailRequired = MutableStateFlow(needsGmail(authRepo.currentUser()))
    val gmailRequired: StateFlow<Boolean> = _gmailRequired.asStateFlow()

    private val _knownGmails = MutableStateFlow<List<String>>(emptyList())
    /** Gmails already added to the signed-in account (shown in the popup). */
    val knownGmails: StateFlow<List<String>> = _knownGmails.asStateFlow()

    private fun refreshKnownGmails() {
        val user = authRepo.currentUser() ?: return
        _knownGmails.value = gmailRecovery.localGmails(user.uid)
        viewModelScope.launch {
            val cloud = gmailRecovery.cloudGmails()
            _knownGmails.value = (cloud + gmailRecovery.localGmails(user.uid)).distinct().sorted()
            if (cloud.isNotEmpty() && _gmailRequired.value && lastLoginPassword == null && needsGmail(user).not()) {
                // Restored session (not a fresh login) that already has a Gmail: no popup.
                _gmailRequired.value = false
            }
        }
    }

    init {
        refreshKnownGmails()
    }

    /** Account has no password yet (old SMS-code login): the Gmail popup also asks for one. */
    fun currentAccountNeedsPassword(): Boolean {
        val user = authRepo.currentUser() ?: return false
        return !authRepo.hasPassword(user) && !user.phoneNumber.isNullOrBlank()
    }

    /** Opens the phone's Gmail list; the chosen Gmail is added to this account (no limit). */
    suspend fun linkGmail(activityContext: android.content.Context, password: String?): Result<String> {
        authRepo.currentUser() ?: return Result.failure(IllegalStateException("আগে Log in করুন।"))
        if (currentAccountNeedsPassword() && (password == null || password.length < MIN_PASSWORD_LENGTH)) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড কমপক্ষে $MIN_PASSWORD_LENGTH অক্ষরের দিন।"))
        }
        val gmail = authRepo.chooseGmailAddress(activityContext).getOrElse { return Result.failure(it) }
        return addGmailToAccount(gmail, password)
    }

    /** Typed Gmail (when the phone's Gmail list cannot open). Recovery then uses the emailed link. */
    suspend fun saveTypedGmail(raw: String, password: String?): Result<String> {
        authRepo.currentUser() ?: return Result.failure(IllegalStateException("আগে Log in করুন।"))
        com.example.data.repository.GmailRecoveryRepository.normalize(raw)
            ?: return Result.failure(IllegalArgumentException("সঠিক Gmail লিখুন, যেমন: name@gmail.com"))
        if (currentAccountNeedsPassword() && (password == null || password.length < MIN_PASSWORD_LENGTH)) {
            return Result.failure(IllegalArgumentException("পাসওয়ার্ড কমপক্ষে $MIN_PASSWORD_LENGTH অক্ষরের দিন।"))
        }
        return addGmailToAccount(raw, password)
    }

    private suspend fun addGmailToAccount(gmail: String, password: String?): Result<String> {
        val user = authRepo.currentUser() ?: return Result.failure(IllegalStateException("আগে Log in করুন।"))
        if (currentAccountNeedsPassword() && password != null) {
            authRepo.setPasswordForVerifiedAccount(user.phoneNumber.orEmpty(), password)
                .onFailure { return Result.failure(IllegalArgumentException(it.message)) }
            lastLoginPassword = password
        }
        return gmailRecovery.addGmail(gmail, lastLoginPassword)
            .map { it.also { _gmailRequired.value = false; refreshKnownGmails() } }
            .recoverCatching { throw IllegalArgumentException(it.message ?: "আবার চেষ্টা করুন।") }
    }

    /** Forgot password (Gmail not on this phone), step 1: email a link to [gmail]. */
    suspend fun sendResetCodeToGmail(gmail: String): Result<String> {
        _authFlowActive.value = true
        return try {
            authRepo.sendResetCodeToGmail(gmail)
                .map { "$it-এ একটা লিংক পাঠানো হয়েছে। Gmail খুলে লিংকে চাপুন (না পেলে Spam দেখুন), তারপর এখানে \"পাসওয়ার্ড বদলান\" চাপুন।" }
                .recoverCatching { throw IllegalArgumentException(it.message) }
        } finally {
            _authFlowActive.value = false
        }
    }

    /** Forgot password, step 2: after the link was clicked, save the new password. */
    suspend fun resetPasswordWithGmailCode(identifier: String, gmail: String, newPassword: String): Result<String> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty()) return Result.failure(IllegalArgumentException("মোবাইল নম্বর লিখুন।"))
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return Result.failure(IllegalArgumentException("নতুন পাসওয়ার্ড কমপক্ষে $MIN_PASSWORD_LENGTH অক্ষরের দিন।"))
        }
        _authFlowActive.value = true
        try {
            val user = authRepo.resetPasswordWithGmailCode(cleanId, gmail, newPassword)
                .getOrElse { return Result.failure(IllegalArgumentException(it.message)) }
            lastLoginPassword = newPassword
            refreshKnownGmails()
            _gmailRequired.value = false
            prefs.setHasCompletedLogin(true)
            syncLocalProfileFromAccount(nameInput = user.displayName, identifier = user.phoneNumber ?: cleanId)
            liveChatRepo.ensureOwnProfile(user.uid, user.displayName).onSuccess { _ownProfile.value = it }
            return Result.success("পাসওয়ার্ড বদলে গেছে।")
        } finally {
            _authFlowActive.value = false
        }
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

    // ---- Mobile number + SMS code (Firebase Phone Auth) -------------------

    @Volatile
    private var phoneSignInInProgress = false

    suspend fun sendPhoneLoginCode(
        activityContext: android.content.Context,
        rawPhone: String,
        displayName: String,
        resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?
    ): com.example.data.repository.PhoneCodeResult {
        val e164 = com.example.data.repository.PhoneNumbers.toE164(rawPhone)
            ?: return com.example.data.repository.PhoneCodeResult.Failure(
                "সঠিক মোবাইল নম্বর লিখুন, যেমন: 01712345678"
            )
        phoneSignInInProgress = true
        _authFlowActive.value = true
        val result = authRepo.sendPhoneCode(activityContext, e164, resendToken)
        when (result) {
            is com.example.data.repository.PhoneCodeResult.SignedIn -> completePhoneSignIn(result.user, displayName)
            is com.example.data.repository.PhoneCodeResult.Failure -> phoneSignInInProgress = false
            is com.example.data.repository.PhoneCodeResult.CodeSent -> Unit
        }
        return result
    }

    suspend fun verifyPhoneLoginCode(
        verificationId: String,
        code: String,
        displayName: String
    ): com.example.data.repository.AuthResult {
        phoneSignInInProgress = true
        val result = authRepo.verifyPhoneCode(verificationId, code)
        if (result is com.example.data.repository.AuthResult.Success) {
            completePhoneSignIn(result.user, displayName)
        } else {
            phoneSignInInProgress = false
        }
        return result
    }

    private suspend fun completePhoneSignIn(user: FirebaseUser, rawName: String, phoneOverride: String? = null) {
        try {
            val phone = phoneOverride ?: user.phoneNumber.orEmpty()
            val typedName = rawName.trim().take(ChatboxConstants.MAX_DISPLAY_NAME_LENGTH)
            val localName = localUserProfile.value.displayName.takeIf { it.isNotBlank() && it != "Chatbox User" }
            // The app opens only after the password step finishes (authFlowActive=false).
            val base = liveChatRepo.ensureOwnProfile(user.uid, typedName.ifEmpty { localName }).getOrNull()
            val existingName = base?.displayName?.takeIf { it.isNotBlank() && it != "Chatbox User" }
            val finalName = typedName.ifEmpty { existingName ?: localName ?: "Chatbox User" }
            authRepo.updateDisplayName(finalName)
            syncLocalProfileFromAccount(nameInput = finalName, identifier = phone)
            liveChatRepo.saveOwnWhatsAppProfile(
                uid = user.uid,
                currentShortId = base?.shortId.orEmpty(),
                displayName = finalName,
                about = base?.about ?: LiveChatRepository.DEFAULT_ABOUT_TEXT,
                phoneNumber = phone,
                previousPhoneNumber = ""
            ).onSuccess { (profile, _) ->
                _ownProfile.value = profile
                prefs.saveLocalUserProfile(localUserProfile.value.copy(shortId = profile.shortId))
            }.onFailure { e ->
                Log.w(LIVE_TAG, "phone profile save failed", e)
                if (base != null) _ownProfile.value = base
            }
        } finally {
            phoneSignInInProgress = false
        }
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
        viewModelScope.launch {
            prefs.setSairaNotifReplyEnabled(enabled)
            com.example.saira.SairaKeepAliveService.sync(getApplication())
        }
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
        audioDurationMs: Long? = null,
        videoPath: String? = null
    ) {
        val thread = chatboxThreads.value.firstOrNull { it.id == threadId }
        val me = currentUser.value
        if (thread != null && !thread.isGroup && thread.peerUid.isNotBlank() &&
            thread.conversationId.isBlank() && me != null
        ) {
            // Real person but the server chat isn't created yet: create it first, then send,
            // so the message really reaches them (never stays only on this phone).
            viewModelScope.launch {
                val ok = attachMutex.withLock {
                    val current = chatboxThreads.value.firstOrNull { it.id == threadId }
                    if (current != null && current.conversationId.isNotBlank()) true
                    else runCatching { attachConversation(threadId, thread.peerUid, me.uid) }
                        .onFailure { Log.w(LIVE_TAG, "attach before send failed", it) }.isSuccess
                }
                val target = prefs.chatboxThreads.first().let { list ->
                    list.firstOrNull { it.id == threadId && it.conversationId.isNotBlank() }
                        ?: list.firstOrNull { !it.isGroup && it.peerUid == thread.peerUid && it.conversationId.isNotBlank() }
                }
                if (!ok || target == null) {
                    sendChatboxMessageNow(threadId, body, timeString, kind, imageUri, audioPath, audioDurationMs, conversationOverride = null, videoPath = videoPath)
                    appendSystemNotice(threadId, "মেসেজটি পাঠানো যায়নি। ইন্টারনেট চেক করে আবার পাঠান।")
                } else {
                    sendChatboxMessageNow(target.id, body, timeString, kind, imageUri, audioPath, audioDurationMs, conversationOverride = target.conversationId, videoPath = videoPath)
                }
            }
            return
        }
        sendChatboxMessageNow(threadId, body, timeString, kind, imageUri, audioPath, audioDurationMs, conversationOverride = null, videoPath = videoPath)
    }

    private fun sendChatboxMessageNow(
        threadId: String,
        body: String,
        timeString: String,
        kind: ChatboxMessageKind,
        imageUri: String?,
        audioPath: String?,
        audioDurationMs: Long?,
        conversationOverride: String?,
        videoPath: String? = null
    ) {
        val trimmed = body.trim().take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        if (trimmed.isEmpty() && imageUri.isNullOrBlank() && audioPath.isNullOrBlank() && videoPath.isNullOrBlank()) return
        val mediaPath = when (kind) {
            ChatboxMessageKind.IMAGE -> imageUri
            ChatboxMessageKind.VOICE -> audioPath
            ChatboxMessageKind.VIDEO -> videoPath
            else -> null
        }?.takeIf { it.isNotBlank() }

        // User replied manually -> cancel any pending incoming auto-reply for this thread.
        pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()

        val senderDisplayName = ownProfile.value?.displayName?.ifBlank { null }
            ?: localUserProfile.value.displayName
        val liveThread = chatboxThreads.value.firstOrNull { it.id == threadId }
        val liveUid = currentUser.value?.uid
        val liveConversationId = conversationOverride ?: liveThread?.conversationId.orEmpty()
        val liveMessageId = if (
            liveConversationId.isNotBlank() && liveUid != null &&
            ((kind == ChatboxMessageKind.TEXT && trimmed.isNotEmpty()) || mediaPath != null)
        ) {
            runCatching { liveChatRepo.newMessageId(liveConversationId) }.getOrNull()
        } else null
        val userMsg = ChatboxMessage(
            id = liveMessageId?.let { "live_$it" }
                ?: "msg_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
            from = "me",
            senderName = senderDisplayName,
            text = trimmed,
            time = timeString,
            kind = kind,
            imageUri = imageUri,
            audioPath = audioPath,
            audioDurationMs = audioDurationMs,
            videoUrl = videoPath
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

            if (liveMessageId != null && liveUid != null) {
                // Photos / videos / voice notes: upload the file first, then send its
                // public https link so the other person's phone can show / play it.
                var remoteUrl: String? = null
                var uploadFailed = false
                if (mediaPath != null) {
                    val up = uploadChatMedia(mediaPath)
                    remoteUrl = up.getOrNull()
                    if (remoteUrl == null) {
                        uploadFailed = true
                        Log.w(LIVE_TAG, "media upload failed", up.exceptionOrNull())
                        appendSystemNotice(
                            threadId,
                            "ফাইলটি পাঠানো যায়নি (আপলোড ব্যর্থ)। ইন্টারনেট চেক করে আবার পাঠান।"
                        )
                    }
                }
                val sent = if (uploadFailed) Result.success(Unit) else liveChatRepo.sendLiveMessage(
                    conversationId = liveConversationId,
                    senderId = liveUid,
                    rawBody = if (kind == ChatboxMessageKind.VOICE) "" else trimmed,
                    imageUrl = remoteUrl.takeIf { kind == ChatboxMessageKind.IMAGE },
                    audioUrl = remoteUrl.takeIf { kind == ChatboxMessageKind.VOICE },
                    audioDurationMs = audioDurationMs.takeIf { kind == ChatboxMessageKind.VOICE },
                    videoUrl = remoteUrl.takeIf { kind == ChatboxMessageKind.VIDEO },
                    messageId = liveMessageId
                )
                if (sent.isFailure) {
                    Log.w(LIVE_TAG, "live send failed", sent.exceptionOrNull())
                    appendSystemNotice(
                        threadId,
                        "মেসেজটি পাঠানো যায়নি। ইন্টারনেট চেক করে আবার পাঠান।"
                    )
                }
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
                thread.conversationId.isBlank() &&
                thread.peerUid.isBlank() &&
                conversationOverride == null &&
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
            val ownerActive = SairaPrompts.isOwnerAccount(
                currentUser.value?.uid,
                currentUser.value?.phoneNumber,
                localUserProfile.value.phoneNumber
            )
            val ownerLabel = if (ownerActive) {
                ownProfile.value?.displayName?.ifBlank { null }
                    ?: localUserProfile.value.displayName.ifBlank { null }
                    ?: "owner"
            } else ""
            val prompt = SairaPrompts.buildTurnPrompt(
                question = userMessage,
                isFirstTurn = isFirstTurn,
                isContinuation = !isFirstTurn,
                hourOfDay = clockHourProvider(),
                ownerLabel = ownerLabel
            )
            val aiAnswer = try {
                withTimeoutOrNull(25_000L) {
                    aiCompleter(SairaPrompts.chatSystemInstructionFor(ownerActive), aiHistory, prompt)
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
            scheduleIncomingAutoReply(thread, incomingMsg.id, trimmed)
        }
    }

    /**
     * Someone messaged the owner on [thread]. Wait [autoReplyWaitMs]; if the
     * owner still hasn't answered, Saira replies on the owner's behalf. For live
     * (Firestore) chats the reply is really SENT, so the other person gets it.
     */
    private fun scheduleIncomingAutoReply(thread: ChatboxThread, incomingMsgId: String, incomingText: String) {
        val threadId = thread.id
        if (threadId == "saira" || thread.isGroup) return
        if (incomingText.startsWith(SAIRA_LIVE_PREFIX)) return // never answer another Saira (bot loop)
        if (threadId in chatboxBlockedThreads.value || threadId in dmMutedPeers.value) return
        pendingIncomingAutoReplyJobs.remove(threadId)?.cancel()

        val job = viewModelScope.launch {
            // Saira master switch OFF, or OFF for this chat (DM settings) -> never answer.
            if (!prefs.sairaChatboxOneMinEnabled.first()) return@launch
            if (threadId in prefs.sairaOffThreads.first()) return@launch

            val engagedAt = sairaAbsenceEngagedThreads[threadId]
            val isContinuation = engagedAt != null &&
                (System.currentTimeMillis() - engagedAt) <= ACTIVE_SAIRA_WINDOW_MS
            delay(if (isContinuation) continuationDebounceMs else autoReplyWaitMs)

            if (threadId in chatboxBlockedThreads.value || threadId in dmMutedPeers.value) return@launch
            val currentMessages = prefs.chatboxMessages.first()[threadId].orEmpty()
            val latest = currentMessages.lastOrNull() ?: return@launch
            // Owner already replied manually in time -> do not auto-reply!
            if (latest.id != incomingMsgId || latest.from == "me") return@launch
            // Owner is on line (Chatbox open on screen) -> they answer themselves.
            // Remember it: if they leave without answering, Saira replies then.
            if (ownerOnline()) {
                deferredWhileOnline[threadId] = incomingMsgId to incomingText
                return@launch
            }
            deferredWhileOnline.remove(threadId)

            val ownerName = ownProfile.value?.displayName?.ifBlank { null }
                ?: localUserProfile.value.displayName
            val result = evaluateAutoReplyInternal(
                senderName = thread.name,
                platform = SairaPlatform.ANY,
                incomingText = incomingText,
                peerNameForAbsence = ownerName,
                peerIdForLocation = threadId,
                isChatboxAbsence = false,
                forceContinuation = isContinuation,
                recentMessagesForHistory = currentMessages.map {
                    if (it.text.startsWith(SAIRA_LIVE_PREFIX)) it.copy(text = it.text.removePrefix(SAIRA_LIVE_PREFIX)) else it
                }
            )
            if (!result.replied || result.replyText.isBlank()) return@launch
            // A newer message came in (or the owner answered / came on line) while Saira was
            // thinking: drop this reply so only ONE answer goes out, for the latest message.
            if (!isActive) return@launch
            val stillLatest = prefs.chatboxMessages.first()[threadId].orEmpty().lastOrNull()
            if (stillLatest?.id != incomingMsgId || !prefs.sairaChatboxOneMinEnabled.first() ||
                threadId in prefs.sairaOffThreads.first()
            ) return@launch
            if (ownerOnline()) {
                deferredWhileOnline[threadId] = incomingMsgId to incomingText
                return@launch
            }

            val freshThread = chatboxThreads.value.firstOrNull { it.id == threadId } ?: thread
            val myUid = currentUser.value?.uid
            if (freshThread.conversationId.isNotBlank() && myUid != null) {
                if (!sendSairaLiveReply(freshThread, myUid, result.replyText)) return@launch
            } else {
                val replyTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                appendSairaMessageToThread(threadId, result.replyText, replyTimeStr)
            }
            sairaAbsenceEngagedThreads[threadId] = System.currentTimeMillis()
            prefs.appendAutoReplyLog(
                SairaAutoReplyLog(
                    id = "log_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
                    contactName = thread.name,
                    platformLabel = if (result.matchedRuleContact != null) "Chatbox · Rule" else "Chatbox 10s",
                    incomingPreview = incomingText.take(120),
                    sentReply = result.replyText.take(280),
                    includedLocation = result.includedLocation,
                    timestampMs = System.currentTimeMillis()
                )
            )
            pendingIncomingAutoReplyJobs.remove(threadId)
        }
        pendingIncomingAutoReplyJobs[threadId] = job
    }

    /** Sends Saira's reply as a real Firestore message from the owner's account. */
    private suspend fun sendSairaLiveReply(thread: ChatboxThread, myUid: String, replyText: String): Boolean {
        val conversationId = thread.conversationId
        val body = (SAIRA_LIVE_PREFIX + replyText.trim()).take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        val messageId = runCatching { liveChatRepo.newMessageId(conversationId) }.getOrNull() ?: return false
        val localMsg = ChatboxMessage(
            id = "live_$messageId",
            from = "me",
            senderName = "Saira",
            text = body,
            time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
            kind = ChatboxMessageKind.TEXT
        )
        chatboxMessagesMutex.withLock {
            val map = prefs.chatboxMessages.first().toMutableMap()
            map[thread.id] = map[thread.id].orEmpty() + localMsg
            prefs.saveChatboxMessages(map)
        }
        val sent = liveChatRepo.sendLiveMessage(
            conversationId = conversationId,
            senderId = myUid,
            rawBody = body,
            messageId = messageId
        )
        if (sent.isFailure) Log.w(LIVE_TAG, "Saira live auto-reply failed", sent.exceptionOrNull())
        return sent.isSuccess
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
            SairaLocationHelper.getFreshLocation(app)
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
                    }?.trim().orEmpty().let { if (AiSafeResponses.isSafetyMessage(it)) "" else it }
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
                    }?.trim().orEmpty().let { if (AiSafeResponses.isSafetyMessage(it)) "" else it }
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
        val mime = runCatching { getApplication<Application>().contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("video/")) {
            sendChatboxVideo(threadId, uri)
            return
        }
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

    fun sendChatboxVideo(threadId: String, uri: Uri) {
        viewModelScope.launch {
            val savedPath = copyVideoToAppStorage(uri)
            if (savedPath == null) {
                showUserNotice("ভিডিওটি পাঠানো যায়নি। ৫০ MB-এর ছোট ভিডিও বেছে নিন।")
                return@launch
            }
            showUserNotice("ভিডিও আপলোড হচ্ছে…")
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            sendChatboxMessage(
                threadId = threadId,
                body = "",
                timeString = now,
                kind = ChatboxMessageKind.VIDEO,
                videoPath = savedPath
            )
        }
    }

    private suspend fun copyVideoToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        runCatching {
            val mediaDir = File(app.filesDir, "chat_video").apply { mkdirs() }
            val outFile = File(mediaDir, "vid_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}.mp4")
            val input = app.contentResolver.openInputStream(uri) ?: return@runCatching null
            input.use { inp -> FileOutputStream(outFile).use { out -> inp.copyTo(out) } }
            if (outFile.length() == 0L || outFile.length() > 50L * 1024 * 1024) {
                outFile.delete()
                return@runCatching null
            }
            outFile.absolutePath
        }.getOrNull()
    }

    private suspend fun uploadChatMedia(path: String): Result<String> {
        if (path.startsWith("https://")) return Result.success(path)
        val file = File(path)
        return if (file.exists()) {
            com.example.util.MediaHost.upload(file)
        } else {
            com.example.util.MediaHost.upload(getApplication(), Uri.parse(path))
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

    /**
     * Number / 6-digit ID → ONLY a real Chatbox account. Never creates a fake
     * "Chatbox Friend (…)" contact. Returns an existing real chat's id right away;
     * otherwise looks the account up: found → opens a real chat with their name,
     * not found → shows "no Chatbox account on this number/ID".
     */
    fun openOrCreateDirectThread(name: String, identifier: String = ""): String {
        val query = identifier.trim().ifEmpty { name.trim() }
        if (query.isEmpty()) return ""
        val compact = query.removePrefix("#").replace(" ", "")
        val isId = ChatboxConstants.VALID_SHORT_ID_REGEX.matches(compact)
        val digits = query.filter { it.isDigit() }
        val isPhone = !isId && digits.length >= 7 && query.none { it.isLetter() }
        val existing = chatboxThreads.value.firstOrNull { t ->
            !t.isGroup && t.id != "saira" && t.peerUid.isNotBlank() && (
                (isId && t.shortId == compact) ||
                    (isPhone && t.phoneNumber.filter { it.isDigit() }.let { it.isNotEmpty() && (it == digits || it.takeLast(10) == digits.takeLast(10)) }) ||
                    (!isId && !isPhone && t.name.equals(query, ignoreCase = true))
                )
        }
        if (existing != null) return existing.id
        if (!isId && !isPhone) {
            showUserNotice("নম্বর বা ৬-সংখ্যার ID লিখে খুঁজুন।")
            return ""
        }
        viewModelScope.launch {
            when (val state = searchPeer(query)) {
                is PeerSearchState.Found -> {
                    val threadId = openChatWithPeer(state.profile)
                    if (isPhone) updateThreadNow(threadId) { it.copy(phoneNumber = query) }
                    selectThread(threadId)
                    selectTab(AppTab.Core(CoreAppTab.CHATBOX))
                }
                is PeerSearchState.NotFound -> showUserNotice(state.message)
                is PeerSearchState.Failed -> showUserNotice(state.message)
                PeerSearchState.NeedsLogin -> showUserNotice("আগে Log in করুন।")
                else -> Unit
            }
        }
        return ""
    }

    private fun showUserNotice(text: String) {
        android.widget.Toast.makeText(getApplication(), text, android.widget.Toast.LENGTH_LONG).show()
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

    /** Caller presses audio/video call: local dialog + ringing signal to the peer. */
    fun startCall(threadId: String, peerName: String, isVideo: Boolean) {
        if (_activeCall.value != null) return
        val callId = "call_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        _activeCall.value = ActiveChatboxCall(
            callId = callId,
            threadId = threadId,
            peerName = peerName,
            isVideo = isVideo,
            isIncoming = false,
            phase = "ringing",
            connectedAtMs = 0L
        )
        startCallEngine(callId, threadId, peerName, isVideo)
        sendCallSig(threadId, callId, "start", isVideo)
    }

    private fun startCallEngine(callId: String, threadId: String, peerName: String, isVideo: Boolean) {
        _callEngine.value?.let { if (it.callId != callId) stopCallEngine() else return }
        val app = getApplication<Application>()
        val engine = runCatching {
            com.example.call.CallEngine(app, callId, isVideo) { kind, sdp ->
                sendCallSdp(threadId, callId, isVideo, kind, sdp)
            }
        }.onFailure { Log.w(LIVE_TAG, "call engine start failed", it) }.getOrNull() ?: return
        _callEngine.value = engine
        com.example.call.CallForegroundService.start(app, peerName, isVideo)
        pendingOfferSdp?.let { (id, sdp) ->
            if (id == callId) {
                pendingOfferSdp = null
                engine.acceptOffer(sdp)
            }
        }
    }

    private fun stopCallEngine() {
        val engine = _callEngine.value
        _callEngine.value = null
        engine?.close()
        callSdpChunks.clear()
        pendingOfferSdp = null
        runCatching { com.example.call.CallForegroundService.stop(getApplication()) }
    }

    private fun clearActiveCall() {
        _activeCall.value?.callId?.let { endedCallIds.add(it) }
        _activeCall.value = null
        stopCallEngine()
    }

    /** SDP is base64-chunked into hidden call-signal messages (body limit 2000). */
    private fun sendCallSdp(threadId: String, callId: String, isVideo: Boolean, kind: String, sdp: String) {
        val thread = chatboxThreads.value.firstOrNull { it.id == threadId } ?: return
        val convId = thread.conversationId
        val myUid = currentUser.value?.uid ?: return
        if (convId.isBlank()) return
        val encoded = android.util.Base64.encodeToString(sdp.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val chunks = encoded.chunked(SDP_CHUNK_CHARS)
        viewModelScope.launch {
            chunks.forEachIndexed { i, chunk ->
                val body = CALL_SIG_PREFIX + "$callId|sdp|${if (isVideo) "1" else "0"}|$kind|$i|${chunks.size}|$chunk"
                val r = runCatching { liveChatRepo.sendLiveMessage(convId, myUid, body) }
                if (r.isFailure || r.getOrNull()?.isFailure == true) Log.w(LIVE_TAG, "sdp chunk $i send failed")
            }
        }
    }

    private fun handleCallSdpChunk(cur: ActiveChatboxCall?, callId: String, payload: String) {
        if (cur == null || cur.callId != callId) return
        val p = payload.split("|", limit = 7)
        if (p.size < 7) return
        val kind = p[3]
        val idx = p[4].toIntOrNull() ?: return
        val total = p[5].toIntOrNull() ?: return
        if (total !in 1..40 || idx !in 0 until total) return
        val key = "$callId|$kind"
        if (key in callSdpDone) return
        val arr = callSdpChunks.getOrPut(key) { arrayOfNulls(total) }
        if (arr.size != total) return
        arr[idx] = p[6]
        if (arr.any { it == null }) return
        callSdpChunks.remove(key)
        callSdpDone.add(key)
        val sdp = runCatching {
            String(android.util.Base64.decode(arr.joinToString(""), android.util.Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrNull() ?: return
        when {
            kind == "offer" && cur.isIncoming -> {
                val engine = _callEngine.value
                if (engine != null && engine.callId == callId) engine.acceptOffer(sdp)
                else pendingOfferSdp = callId to sdp
            }
            kind == "answer" && !cur.isIncoming -> {
                _callEngine.value?.takeIf { it.callId == callId }?.acceptAnswer(sdp)
            }
        }
    }

    /** Callee answers: stop ring notification, connect both sides. */
    fun acceptCall() {
        val call = _activeCall.value ?: return
        if (!call.isIncoming || call.phase != "ringing") return
        _activeCall.value = call.copy(phase = "connected", connectedAtMs = System.currentTimeMillis())
        runCatching { ChatboxNotifier.cancelIncomingCall(getApplication(), call.callId) }
        startCallEngine(call.callId, call.threadId, call.peerName, call.isVideo)
        sendCallSig(call.threadId, call.callId, "accept", call.isVideo)
    }

    /** Callee declines: caller sees a missed call. */
    fun declineCall() {
        val call = _activeCall.value ?: return
        if (!call.isIncoming) return
        runCatching { ChatboxNotifier.cancelIncomingCall(getApplication(), call.callId) }
        sendCallSig(call.threadId, call.callId, "decline", call.isVideo)
        clearActiveCall()
    }

    /**
     * Dialog closed on either side. [dismissed] = outside-tap (no local log while
     * still ringing, mirrors the old UI); [missed] = "Mark missed"; otherwise a
     * normal end with [durationLabel].
     */
    fun endCall(missed: Boolean, durationLabel: String? = null, dismissed: Boolean = false) {
        val call = _activeCall.value ?: return
        runCatching { ChatboxNotifier.cancelIncomingCall(getApplication(), call.callId) }
        if (call.phase == "ringing") {
            if (call.isIncoming) {
                sendCallSig(call.threadId, call.callId, "decline", call.isVideo)
            } else {
                if (!dismissed) {
                    logChatboxCall(call.threadId, call.peerName, call.isVideo, true, "Missed")
                }
                sendCallSig(call.threadId, call.callId, "missed", call.isVideo)
            }
        } else {
            val label = durationLabel ?: connectedDurationLabel(call)
            logChatboxCall(call.threadId, call.peerName, call.isVideo, missed, if (missed) "Missed" else label)
            sendCallSig(call.threadId, call.callId, "end", call.isVideo)
        }
        clearActiveCall()
    }

    private fun connectedDurationLabel(call: ActiveChatboxCall): String {
        val sec = ((System.currentTimeMillis() - call.connectedAtMs) / 1000L).coerceAtLeast(1L)
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
    }

    private fun sendCallSig(threadId: String, callId: String, action: String, isVideo: Boolean) {
        val thread = chatboxThreads.value.firstOrNull { it.id == threadId } ?: return
        val convId = thread.conversationId.orEmpty()
        val myUid = currentUser.value?.uid ?: return
        if (convId.isBlank()) return // local-only thread: dialog behaves as before
        val body = (CALL_SIG_PREFIX + "$callId|$action|${if (isVideo) "1" else "0"}")
            .take(ChatboxConstants.MAX_MESSAGE_BODY_LENGTH)
        viewModelScope.launch {
            runCatching { liveChatRepo.sendLiveMessage(convId, myUid, body) }
        }
    }

    /** Peer's call signal arrived over the conversation stream. Never shown in chat. */
    private fun handleCallSig(thread: ChatboxThread, msg: LiveMessage, myUid: String) {
        if (thread.isGroup) return
        val payload = msg.body.removePrefix(CALL_SIG_PREFIX)
        val parts = payload.split("|")
        val callId = parts.getOrNull(0)?.trim().orEmpty()
        val action = parts.getOrNull(1)?.trim().orEmpty()
        if (callId.isEmpty() || action.isEmpty()) return
        val isVideo = parts.getOrNull(2) == "1"
        val sentAtMs = msg.sentAt?.toDate()?.time ?: return
        val ageMs = System.currentTimeMillis() - sentAtMs
        val cur = _activeCall.value
        when (action) {
            "sdp" -> handleCallSdpChunk(cur, callId, payload)
            "start" -> {
                // Only ring for a fresh invite from the peer (never replay old ones).
                if (ageMs !in -30_000L..60_000L) return
                if (callId in endedCallIds) return
                if (cur != null) {
                    if (cur.callId != callId) sendCallSig(thread.id, callId, "decline", isVideo)
                    return
                }
                _activeCall.value = ActiveChatboxCall(
                    callId = callId,
                    threadId = thread.id,
                    peerName = thread.name,
                    isVideo = isVideo,
                    isIncoming = true,
                    phase = "ringing",
                    connectedAtMs = 0L
                )
                runCatching {
                    ChatboxNotifier.notifyIncomingCall(
                        getApplication(),
                        callId,
                        title = if (isVideo) "ভিডিও কল" else "অডিও কল",
                        body = "${thread.name} আপনাকে কল করছেন"
                    )
                }
            }
            "accept" -> {
                if (cur != null && cur.callId == callId && !cur.isIncoming && cur.phase == "ringing") {
                    _activeCall.value = cur.copy(phase = "connected", connectedAtMs = System.currentTimeMillis())
                    // Callee is ready: send the WebRTC offer so voice/video starts flowing.
                    val engine = _callEngine.value
                    if (engine == null || engine.callId != callId) {
                        startCallEngine(cur.callId, cur.threadId, cur.peerName, cur.isVideo)
                    }
                    _callEngine.value?.takeIf { it.callId == callId }?.makeOffer()
                }
            }
            "decline", "missed" -> {
                // decline → caller side; missed → callee side (caller cancelled while ringing).
                if (cur != null && cur.callId == callId && cur.phase == "ringing") {
                    clearActiveCall()
                    if (cur.isIncoming) {
                        runCatching { ChatboxNotifier.cancelIncomingCall(getApplication(), callId) }
                    }
                    logChatboxCall(cur.threadId, cur.peerName, cur.isVideo, true, "Missed")
                }
            }
            "end" -> {
                if (cur != null && cur.callId == callId) {
                    clearActiveCall()
                    when {
                        cur.phase == "connected" ->
                            logChatboxCall(cur.threadId, cur.peerName, cur.isVideo, false, connectedDurationLabel(cur))
                        cur.isIncoming ->
                            logChatboxCall(cur.threadId, cur.peerName, cur.isVideo, true, "Missed")
                    }
                }
            }
        }
    }

    fun clearChatboxCallLogs() {
        viewModelScope.launch { prefs.clearChatboxCallLogs() }
    }

    fun toggleFavoriteThread(threadId: String) {
        val clean = threadId.trim()
        if (clean.isEmpty()) return
        val currentlyFav = clean in chatboxFavoriteThreads.value
        viewModelScope.launch { prefs.setChatboxThreadFavorite(clean, !currentlyFav) }
    }

    fun scheduleCall(
        threadId: String,
        peerName: String,
        isVideo: Boolean,
        scheduledForLabel: String
    ) {
        val cleanName = peerName.trim().ifEmpty { "Chatbox Friend" }
        val cleanTime = scheduledForLabel.trim().ifEmpty { "Today, 8:00 PM" }
        val entry = ScheduledCallEntry(
            id = "sched_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            threadId = threadId,
            peerName = cleanName,
            isVideo = isVideo,
            scheduledForLabel = cleanTime
        )
        val updated = listOf(entry) + chatboxScheduledCalls.value
        viewModelScope.launch { prefs.saveScheduledCalls(updated) }
    }

    fun removeScheduledCall(callId: String) {
        val updated = chatboxScheduledCalls.value.filterNot { it.id == callId }
        viewModelScope.launch { prefs.saveScheduledCalls(updated) }
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

    // ---- Live (Firestore) DM sync ------------------------------------------
    // Local threads are bridged to conversations/{id} so messages actually
    // reach the other Chatbox user, and their messages arrive here.

    private fun stopLiveSync() {
        liveConversationsJob?.cancel()
        liveConversationsJob = null
        liveMessageJobs.values.forEach { it.cancel() }
        liveMessageJobs.clear()
        liveAcceptedConversations.clear()
        _activeCall.value?.let { call ->
            runCatching { ChatboxNotifier.cancelIncomingCall(getApplication(), call.callId) }
        }
        clearActiveCall()
    }

    private fun startLiveSync(uid: String) {
        stopLiveSync()
        liveConversationsJob = viewModelScope.launch {
            relinkLocalOnlyThreads(uid)
            while (isActive) {
                try {
                    liveChatRepo.observeUserConversations(uid).collect { conversations ->
                        for (conv in conversations) {
                            if (conv.status == "closed") continue
                            val peerUid = conv.participants.firstOrNull { it != uid } ?: continue
                            if (conv.status == "pending" && conv.createdBy != uid &&
                                liveAcceptedConversations.add(conv.id)
                            ) {
                                liveChatRepo.updateConversationStatus(conv.id, "active")
                            }
                            runCatching { ensureLiveThread(conv.id, peerUid) }
                                .onFailure { Log.w(LIVE_TAG, "ensureLiveThread failed", it) }
                            if (!liveMessageJobs.containsKey(conv.id)) {
                                liveMessageJobs[conv.id] = launch { syncLiveMessages(uid, conv.id) }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(LIVE_TAG, "conversation sync failed; retrying", e)
                }
                delay(15_000L)
            }
        }
    }

    /** Older searches created local-only "Chatbox Friend (…)" threads; link them now. */
    private suspend fun relinkLocalOnlyThreads(uid: String) {
        val all = prefs.chatboxThreads.first()
        // Fake contacts (never linked to a real account and nothing to look up): remove.
        val fakeIds = all.filter { t ->
            t.id != "saira" && !t.isGroup && t.peerUid.isBlank() && t.conversationId.isBlank() &&
                t.phoneNumber.isBlank() && !ChatboxConstants.VALID_SHORT_ID_REGEX.matches(t.shortId)
        }.map { it.id }.toSet()
        if (fakeIds.isNotEmpty()) {
            chatboxThreadsMutex.withLock {
                prefs.saveChatboxThreads(prefs.chatboxThreads.first().filterNot { it.id in fakeIds })
            }
            chatboxMessagesMutex.withLock {
                prefs.saveChatboxMessages(prefs.chatboxMessages.first() - fakeIds)
            }
        }
        val candidates = all.filter { t ->
            t.id != "saira" && !t.isGroup && t.id !in fakeIds &&
                (t.conversationId.isBlank() || t.peerUid.isBlank() || t.name.startsWith("Chatbox Friend")) &&
                (t.phoneNumber.isNotBlank() || ChatboxConstants.VALID_SHORT_ID_REGEX.matches(t.shortId))
        }
        for (t in candidates) {
            val identifier = t.phoneNumber.ifBlank { t.shortId }
            val lookup = liveChatRepo.findPeer(identifier)
            if (lookup.isSuccess && lookup.getOrNull() == null) {
                // Server confirmed nobody owns this number/ID: the placeholder
                // thread could never deliver anything, so drop it.
                chatboxThreadsMutex.withLock {
                    prefs.saveChatboxThreads(prefs.chatboxThreads.first().filterNot { it.id == t.id })
                }
                chatboxMessagesMutex.withLock {
                    prefs.saveChatboxMessages(prefs.chatboxMessages.first() - t.id)
                }
                continue
            }
            runCatching { linkThreadToRealPeer(t.id, identifier, keepCustomName = false, myUid = uid) }
                .onFailure { Log.w(LIVE_TAG, "relink failed", it) }
        }
    }

    private suspend fun linkThreadToRealPeer(
        threadId: String,
        identifier: String,
        keepCustomName: Boolean,
        myUid: String
    ) {
        val peer = liveChatRepo.lookupPeerByIdentifier(identifier) ?: return
        if (peer.uid == myUid) return
        updateThreadNow(threadId) { t ->
            val name = if (keepCustomName) t.name else peer.displayName
            t.copy(
                name = name,
                avatarLabel = initialsOf(name),
                shortId = peer.shortId.ifBlank { t.shortId },
                workBio = peer.about.ifBlank { "Chatbox member · ID ${peer.shortId.ifBlank { t.shortId }}" },
                peerUid = peer.uid
            )
        }
        attachConversation(threadId, peer.uid, myUid)
    }

    private suspend fun attachConversation(threadId: String, peerUid: String, myUid: String) {
        val conversationId = liveChatRepo.startOrOpenConversation(myUid, peerUid).getOrNull() ?: return
        val duplicate = prefs.chatboxThreads.first()
            .firstOrNull { it.conversationId == conversationId && it.id != threadId }
        if (duplicate != null) {
            chatboxThreadsMutex.withLock {
                prefs.saveChatboxThreads(prefs.chatboxThreads.first().filterNot { it.id == threadId })
            }
            if (_activeThreadId.value == threadId) _activeThreadId.value = duplicate.id
        } else {
            updateThreadNow(threadId) { it.copy(conversationId = conversationId) }
        }
    }

    // ---- WhatsApp-style search: number / 6-digit ID → real account or "not found" ----

    private fun isRemoteSearchQuery(query: String): Boolean {
        val q = query.trim().removePrefix("#").replace(" ", "")
        if (ChatboxConstants.VALID_SHORT_ID_REGEX.matches(q)) return true
        return q.none { it.isLetter() } && q.count { it.isDigit() } >= 7
    }

    suspend fun searchPeer(query: String): PeerSearchState {
        val q = query.trim()
        if (!isRemoteSearchQuery(q)) return PeerSearchState.Idle
        val isId = ChatboxConstants.VALID_SHORT_ID_REGEX.matches(q.removePrefix("#").replace(" ", ""))
        val what = if (isId) "এই ID-তে" else "এই নম্বরে"
        val me = currentUser.value ?: return PeerSearchState.NeedsLogin
        if (!effectiveIsOnline()) {
            return PeerSearchState.Failed("ইন্টারনেট নেই। সংযোগ চালু করে আবার খুঁজুন।")
        }
        val result = liveChatRepo.findPeer(q)
        val error = result.exceptionOrNull()
        if (error != null) {
            Log.w(LIVE_TAG, "peer search failed", error)
            return PeerSearchState.Failed("খোঁজা যায়নি। একটু পরে আবার চেষ্টা করুন।")
        }
        val peer = result.getOrNull()
            ?: return PeerSearchState.NotFound("$what কোনো Chatbox user নেই")
        if (peer.uid == me.uid) return PeerSearchState.NotFound("এটি আপনার নিজের account")
        return PeerSearchState.Found(peer)
    }

    /** Opens (or creates) a real, Firestore-linked chat with a found account. */
    fun openChatWithPeer(peer: UserProfile): String {
        val existing = chatboxThreads.value.firstOrNull { !it.isGroup && it.peerUid == peer.uid }
        val threadId = existing?.id ?: "dm_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}"
        val name = peer.displayName.ifBlank { "Chatbox User" }
        if (existing == null) {
            val thread = ChatboxThread(
                id = threadId,
                name = name,
                shortId = peer.shortId,
                role = "Active now",
                connectionLabel = "You're connected on Chatbox",
                locationText = "On Chatbox",
                workBio = peer.about.ifBlank { "Chatbox member · ID ${peer.shortId}" },
                avatarLabel = initialsOf(name),
                style = AvatarStyle.entries[Random.nextInt(AvatarStyle.entries.size)],
                online = true,
                readReceiptsEnabled = true,
                peerUid = peer.uid
            )
            _pendingCreatedThreads.value = listOf(thread) + _pendingCreatedThreads.value
            viewModelScope.launch {
                chatboxThreadsMutex.withLock {
                    val stored = prefs.chatboxThreads.first()
                    if (stored.none { it.id == threadId }) prefs.saveChatboxThreads(listOf(thread) + stored)
                    _pendingCreatedThreads.value = _pendingCreatedThreads.value.filterNot { it.id == threadId }
                }
            }
        } else if (existing.name != name || existing.shortId != peer.shortId) {
            updateThread(threadId) { it.copy(name = name, avatarLabel = initialsOf(name), shortId = peer.shortId) }
        }
        val me = currentUser.value
        if (me != null && existing?.conversationId.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching { attachConversation(threadId, peer.uid, me.uid) }
                    .onFailure { Log.w(LIVE_TAG, "attach conversation failed", it) }
            }
        }
        return threadId
    }

    /** Shows the login screen again (used by "login to search" prompts). */
    fun requestLogin() {
        viewModelScope.launch { prefs.setHasCompletedLogin(false) }
    }

    private suspend fun ensureLiveThread(conversationId: String, peerUid: String) {
        val stored = prefs.chatboxThreads.first()
        if (stored.any { it.conversationId == conversationId }) return
        stored.firstOrNull { !it.isGroup && it.peerUid == peerUid }?.let { existing ->
            updateThreadNow(existing.id) { it.copy(conversationId = conversationId) }
            return
        }
        val profile = liveChatRepo.fetchPeerProfile(peerUid)
        val name = profile?.displayName ?: "Chatbox User"
        val shortId = profile?.shortId.orEmpty()
        val thread = ChatboxThread(
            id = "live_$conversationId",
            name = name,
            shortId = shortId,
            role = "Active now",
            connectionLabel = "You're connected on Chatbox",
            locationText = "On Chatbox",
            workBio = profile?.about?.ifBlank { null } ?: "Chatbox member · ID $shortId",
            avatarLabel = initialsOf(name),
            style = AvatarStyle.entries[Random.nextInt(AvatarStyle.entries.size)],
            online = true,
            readReceiptsEnabled = true,
            peerUid = peerUid,
            conversationId = conversationId
        )
        chatboxThreadsMutex.withLock {
            val current = prefs.chatboxThreads.first()
            if (current.none { it.conversationId == conversationId || it.id == thread.id ||
                    (!it.isGroup && it.peerUid == peerUid) }
            ) {
                prefs.saveChatboxThreads(listOf(thread) + current)
            }
        }
    }

    private suspend fun syncLiveMessages(uid: String, conversationId: String) {
        while (currentCoroutineContext().isActive) {
            try {
                liveChatRepo.observeConversationMessages(conversationId, 200L).collect { (items, _) ->
                    mergeLiveMessages(uid, conversationId, items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LIVE_TAG, "message sync failed for $conversationId; retrying", e)
            }
            delay(20_000L)
        }
    }

    private suspend fun mergeLiveMessages(uid: String, conversationId: String, items: List<LiveMessage>) {
        val thread = prefs.chatboxThreads.first().firstOrNull { it.conversationId == conversationId } ?: return
        // Call signaling rides the same stream: handle peer control messages,
        // never render them in chat and never treat them as chat.
        if (!thread.isGroup) {
            items.forEach { m ->
                if (m.senderId != uid && m.body.startsWith(CALL_SIG_PREFIX)) {
                    runCatching { handleCallSig(thread, m, uid) }
                }
            }
        }
        val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        var incomingNew = 0
        var freshIncoming: ChatboxMessage? = null
        var notifyText: String? = null
        val freshCutoff = System.currentTimeMillis() - LIVE_AUTO_REPLY_FRESH_MS
        chatboxMessagesMutex.withLock {
            val map = prefs.chatboxMessages.first().toMutableMap()
            val current = map[thread.id].orEmpty()
            val known = current.mapTo(HashSet()) { it.id }
            val additions = items
                .filter { it.id.isNotBlank() && "live_${it.id}" !in known }
                .mapNotNull { m ->
                    // Hidden call control messages are never chat.
                    if (m.body.startsWith(CALL_SIG_PREFIX)) return@mapNotNull null
                    val img = m.imageUrl?.takeIf { it.isNotBlank() }
                    val aud = m.audioUrl?.takeIf { it.isNotBlank() }
                    val vid = m.videoUrl?.takeIf { it.isNotBlank() }
                    val text = m.body.ifBlank {
                        when {
                            img != null -> "📷 Photo"
                            vid != null -> "🎬 Video"
                            aud != null -> "🎤 Voice message"
                            else -> ""
                        }
                    }
                    if (text.isBlank()) return@mapNotNull null
                    val mine = m.senderId == uid
                    ChatboxMessage(
                        id = "live_${m.id}",
                        from = if (mine) "me" else "them",
                        senderName = if (mine) "" else thread.name,
                        text = text,
                        time = fmt.format(m.sentAt?.toDate() ?: Date()),
                        kind = when {
                            img != null -> ChatboxMessageKind.IMAGE
                            vid != null -> ChatboxMessageKind.VIDEO
                            aud != null -> ChatboxMessageKind.VOICE
                            else -> ChatboxMessageKind.TEXT
                        },
                        imageUri = img,
                        audioPath = aud,
                        audioDurationMs = m.audioDurationMs.takeIf { aud != null },
                        videoUrl = vid
                    )
                }
            if (additions.isEmpty()) return@withLock
            incomingNew = additions.count { it.from == "them" }
            val freshIds = items
                .filter { m -> m.senderId != uid && (m.sentAt?.toDate()?.time ?: System.currentTimeMillis()) >= freshCutoff }
                .mapTo(HashSet()) { "live_${it.id}" }
            freshIncoming = additions.lastOrNull()?.takeIf { it.from == "them" && it.id in freshIds }
            notifyText = additions.lastOrNull { it.from == "them" }?.text
            map[thread.id] = current + additions
            prefs.saveChatboxMessages(map)
        }
        if (incomingNew > 0) {
            // The real person answered — drop any simulated absence auto-reply.
            pendingAbsenceJobs.remove(thread.id)?.cancel()
            if (_activeThreadId.value != thread.id) {
                updateThread(thread.id) { it.copy(unreadCount = it.unreadCount + incomingNew) }
            }
            // Heads-up notification with sound unless the user is already in
            // this very thread on the foreground.
            val body = notifyText
            if (body != null &&
                (!com.example.util.AppPresence.foreground || _activeThreadId.value != thread.id)
            ) {
                runCatching {
                    ChatboxNotifier.notifyMessage(getApplication(), thread.id, thread.name, body)
                }
            }
        }
        freshIncoming?.let { msg ->
            if (msg.kind == ChatboxMessageKind.TEXT && msg.text.isNotBlank()) {
                scheduleIncomingAutoReply(thread, msg.id, msg.text)
            }
        }
    }

    private suspend fun appendSystemNotice(threadId: String, text: String) {
        val notice = ChatboxMessage(
            id = "sys_${System.currentTimeMillis()}_${Random.nextInt(100, 999)}",
            from = "system",
            text = text,
            time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        )
        chatboxMessagesMutex.withLock {
            val map = prefs.chatboxMessages.first().toMutableMap()
            map[threadId] = map[threadId].orEmpty() + notice
            prefs.saveChatboxMessages(map)
        }
    }

    private suspend fun updateThreadNow(threadId: String, transform: (ChatboxThread) -> ChatboxThread) {
        _pendingCreatedThreads.value = _pendingCreatedThreads.value.map {
            if (it.id == threadId) transform(it) else it
        }
        chatboxThreadsMutex.withLock {
            val list = prefs.chatboxThreads.first().map { if (it.id == threadId) transform(it) else it }
            prefs.saveChatboxThreads(list)
        }
    }

    private fun initialsOf(name: String): String =
        name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifEmpty { name.take(2).uppercase() }

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
        private const val LIVE_TAG = "ChatboxLiveSync"
        const val DEFAULT_AUTO_REPLY_WAIT_MS = 10_000L
        /** Marks Saira-written live messages so the other side's Saira never answers them. */
        const val SAIRA_LIVE_PREFIX = "🤖 Saira: "
        /** Hidden control message used to ring/accept/end calls. Never shown in chat. */
        const val CALL_SIG_PREFIX = "\u0001CALL\u0001"
        private const val SDP_CHUNK_CHARS = 1700
        // Messages that arrived while the app was closed (up to 6h) still get Saira's reply.
        private const val LIVE_AUTO_REPLY_FRESH_MS = 6 * 60 * 60 * 1000L
        const val DEFAULT_CONTINUATION_WAIT_MS = 1_500L
        private const val ACTIVE_SAIRA_WINDOW_MS = 15 * 60_000L
        /** Firebase's minimum password length. */
        const val MIN_PASSWORD_LENGTH = 6
    }
}
