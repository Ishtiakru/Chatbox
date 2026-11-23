package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DemoCatalog
import com.example.data.ai.AiFallbackProviderKind
import com.example.data.ai.SairaAiClient
import com.example.data.model.AdminAuthStatus
import com.example.data.model.AppTab
import com.example.data.model.CoreAppTab
import com.example.data.model.DemoMessage
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkAppUpdate
import com.example.data.model.SparkDynamicConfig
import com.example.data.model.UserProfile
import com.example.data.repository.AppPreferencesRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.LiveChatRepository
import com.example.data.repository.SairaSessionRepository
import com.example.data.repository.SparkAdminRepository
import com.example.saira.SairaAutoReplyLog
import com.example.saira.SairaNotificationRule
import com.example.util.NetworkMonitor
import com.example.util.friendlyFirestoreMessage
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val rememberMeEnabled: StateFlow<Boolean> = prefs.rememberMeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val rememberedPhone: StateFlow<String> = prefs.rememberedPhone
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val quickReaction: StateFlow<String> = prefs.quickReaction
        .stateIn(viewModelScope, SharingStarted.Eagerly, "💜")

    val oneMinAutoReplyEnabled: StateFlow<Boolean> = prefs.sairaChatboxOneMinEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notifAutoReplyEnabled: StateFlow<Boolean> = prefs.sairaNotifReplyEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val liveLocationEnabled: StateFlow<Boolean> = prefs.sairaLiveLocationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val liveLocationAllowedPeers: StateFlow<Set<String>> = prefs.sairaLiveLocationAllowedPeers
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

    val demoMessages: StateFlow<Map<String, List<DemoMessage>>> = prefs.demoMessages
        .stateIn(viewModelScope, SharingStarted.Eagerly, DemoCatalog.defaultSeeds)

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

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _ownProfile = MutableStateFlow<UserProfile?>(null)
    val ownProfile: StateFlow<UserProfile?> = _ownProfile.asStateFlow()

    private val _selectedTab = MutableStateFlow<AppTab>(AppTab.Core(CoreAppTab.LIVE))
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var lastLoadedUid: String? = null

    init {
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
                        _selectedTab.value = AppTab.Core(CoreAppTab.LIVE)
                    }
                } else if (lastLoadedUid != user.uid) {
                    lastLoadedUid = user.uid
                    refreshUserBootState(user)
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

    fun openProfileDialog(open: Boolean) {
        _showProfileDialog.value = open
    }

    fun openUpdateDialog(open: Boolean) {
        _showUpdateDialog.value = open
    }

    fun updateOwnProfile(profile: UserProfile) {
        _ownProfile.value = profile
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_authLoading.value) return
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            try {
                when (val res = authRepo.signInWithGoogleInteractive(activityContext)) {
                    is AuthResult.Success -> _authError.value = null
                    is AuthResult.Cancelled -> _authError.value = null
                    is AuthResult.Failure -> _authError.value = res.message
                }
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun trySilentSignIn(activityContext: Context) {
        if (currentUser.value != null || _authLoading.value) return
        viewModelScope.launch {
            authRepo.trySilentGoogleSignIn(activityContext)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
        }
    }

    fun setLoginLanguage(code: String) {
        viewModelScope.launch { prefs.setLoginLanguage(code) }
    }

    fun saveRememberedLoginPhone(rememberMe: Boolean, phone: String) {
        viewModelScope.launch { prefs.saveRememberedLoginPhone(rememberMe, phone) }
    }

    fun setQuickReaction(emoji: String) {
        viewModelScope.launch { prefs.setQuickReaction(emoji) }
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

    fun sendDemoMessage(contactId: String, body: String, timeString: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val currentMap = demoMessages.value.toMutableMap()
        val list = currentMap[contactId].orEmpty().toMutableList()
        list.add(DemoMessage(from = "me", text = trimmed, time = timeString))
        if (contactId == "saira") {
            list.add(
                DemoMessage(
                    from = "them",
                    text = DemoCatalog.getSairaOfflineGuideReply(trimmed),
                    time = timeString
                )
            )
        }
        currentMap[contactId] = list
        viewModelScope.launch { prefs.saveDemoMessages(currentMap) }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        authRepo.sendPasswordReset(email).recoverCatching { err ->
            throw Exception(friendlyFirestoreMessage(err), err)
        }
}
