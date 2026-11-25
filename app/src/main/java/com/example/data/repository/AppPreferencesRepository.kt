package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ChatboxCatalog
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxReportEntry
import com.example.data.model.ChatboxThread
import com.example.data.model.LocalSairaSessionData
import com.example.data.model.SAIRA_CHAT_RETENTION_MS
import com.example.data.model.LocalUserProfile
import com.example.data.model.ScheduledCallEntry
import com.example.data.model.WorldLanguages
import com.example.saira.SairaDirectoryUser
import com.example.saira.SairaNotificationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "chatbox_app_prefs")

class AppPreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val loginLanguageKey = stringPreferencesKey("login_language")
    private val quickReactionKey = stringPreferencesKey("dm_quick_reaction")
    private val sairaNotificationRulesKey = stringPreferencesKey("saira_notification_rules_v1")
    private val sairaNotifReplyEnabledKey = booleanPreferencesKey("saira_notif_reply_enabled_v1")
    private val sairaChatboxOneMinEnabledKey = booleanPreferencesKey("saira_chatbox_one_min_enabled_v1")
    private val sairaLiveLocationEnabledKey = booleanPreferencesKey("saira_live_location_enabled_v1")
    private val sairaLiveLocationAllowedPeersKey = stringPreferencesKey("saira_live_location_peers_v1")
    private val sairaAutoReplyLogsKey = stringPreferencesKey("saira_auto_reply_logs_v1")
    private val sairaUserDirectoryKey = stringPreferencesKey("saira_user_directory_v1")
    private val rememberMeEnabledKey = booleanPreferencesKey("login_remember_me_v1")
    private val rememberedPhoneKey = stringPreferencesKey("login_remembered_phone_v1")
    private val aiFallbackProviderKindKey = stringPreferencesKey("ai_fallback_provider_v1")
    private val aiFallbackApiKeyKey = stringPreferencesKey("ai_fallback_api_key_v1")
    private val aiFallbackBaseUrlKey = stringPreferencesKey("ai_fallback_base_url_v1")
    private val aiFallbackModelKey = stringPreferencesKey("ai_fallback_model_v1")

    private val chatboxThreadsKey = stringPreferencesKey("chatbox_threads_v1")
    private val chatboxMessagesKey = stringPreferencesKey("chatbox_messages_v1")
    private val chatboxBackupPinKey = stringPreferencesKey("chatbox_backup_pin_v1")
    private val chatboxBlockedThreadsKey = stringPreferencesKey("chatbox_blocked_threads_v1")
    private val chatboxCallLogsKey = stringPreferencesKey("chatbox_call_logs_v1")
    private val chatboxFavoriteThreadsKey = stringPreferencesKey("chatbox_favorite_threads_v1")
    private val chatboxScheduledCallsKey = stringPreferencesKey("chatbox_scheduled_calls_v1")
    private val chatboxReportsKey = stringPreferencesKey("chatbox_reports_v1")
    private val localUserProfileKey = stringPreferencesKey("local_user_profile_v1")
    private val localSairaSessionsKey = stringPreferencesKey("local_saira_sessions_v1")

    private val profileAvatarsKey = stringPreferencesKey("profile_avatars_v1")
    private val dmPeerNicknamesKey = stringPreferencesKey("dm_peer_nicknames_v1")
    private val dmMutedPeersKey = stringPreferencesKey("dm_muted_peers_v1")
    // v2: users who only had the old local-only login see the login screen once more
    // so they can sign in for real (mobile number + SMS code).
    private val hasCompletedLoginKey = booleanPreferencesKey("has_completed_login_v2")
    private val accountIdentifierKey = stringPreferencesKey("account_identifier_v1")
    private val accountPasswordHashKey = stringPreferencesKey("account_password_hash_v1")

    val loginLanguage: Flow<String> =
        context.appDataStore.data.map { prefs ->
            WorldLanguages.normalizeCode(prefs[loginLanguageKey] ?: "en-US")
        }

    val quickReaction: Flow<String> =
        context.appDataStore.data.map { prefs ->
            val saved = prefs[quickReactionKey]?.trim().orEmpty()
            if (saved.isNotEmpty() && saved.length <= 8) saved else "👍"
        }

    val sairaNotificationRules: Flow<List<SairaNotificationRule>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[sairaNotificationRulesKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<SairaNotificationRule>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val sairaNotifReplyEnabled: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[sairaNotifReplyEnabledKey] ?: false
        }

    val sairaChatboxOneMinEnabled: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[sairaChatboxOneMinEnabledKey] ?: true
        }

    val sairaLiveLocationEnabled: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[sairaLiveLocationEnabledKey] ?: false
        }

    val sairaLiveLocationAllowedPeers: Flow<Set<String>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[sairaLiveLocationAllowedPeersKey] ?: return@map emptySet()
            try {
                json.decodeFromString<Set<String>>(raw)
            } catch (_: Exception) {
                emptySet()
            }
        }

    val sairaAutoReplyLogs: Flow<List<com.example.saira.SairaAutoReplyLog>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[sairaAutoReplyLogsKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<com.example.saira.SairaAutoReplyLog>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val sairaUserDirectory: Flow<List<SairaDirectoryUser>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[sairaUserDirectoryKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<SairaDirectoryUser>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val rememberMeEnabled: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[rememberMeEnabledKey] ?: false
        }

    val rememberedPhone: Flow<String> =
        context.appDataStore.data.map { prefs ->
            if (prefs[rememberMeEnabledKey] == true) {
                prefs[rememberedPhoneKey].orEmpty()
            } else {
                ""
            }
        }

    val aiFallbackProviderKind: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[aiFallbackProviderKindKey] ?: "groq"
        }

    val aiFallbackApiKey: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[aiFallbackApiKeyKey].orEmpty()
        }

    val aiFallbackBaseUrl: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[aiFallbackBaseUrlKey].orEmpty()
        }

    val aiFallbackModel: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[aiFallbackModelKey].orEmpty()
        }

    val chatboxThreads: Flow<List<ChatboxThread>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxThreadsKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<ChatboxThread>>(raw)
                    .filterNot { it.id in ChatboxCatalog.DEMO_THREAD_IDS }
            } catch (_: Exception) {
                emptyList()
            }
        }

    val chatboxMessages: Flow<Map<String, List<ChatboxMessage>>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxMessagesKey] ?: return@map emptyMap()
            try {
                json.decodeFromString<Map<String, List<ChatboxMessage>>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

    val chatboxBackupPin: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[chatboxBackupPinKey]?.takeIf { it.length >= 4 } ?: "123456"
        }

    val chatboxBlockedThreads: Flow<Set<String>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxBlockedThreadsKey] ?: return@map emptySet()
            try {
                json.decodeFromString<Set<String>>(raw)
            } catch (_: Exception) {
                emptySet()
            }
        }

    val chatboxCallLogs: Flow<List<ChatboxCallEntry>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxCallLogsKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<ChatboxCallEntry>>(raw)
                    .filterNot { it.id in ChatboxCatalog.DEMO_CALL_LOG_IDS }
            } catch (_: Exception) {
                emptyList()
            }
        }

    val chatboxFavoriteThreads: Flow<Set<String>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxFavoriteThreadsKey] ?: return@map emptySet()
            try {
                json.decodeFromString<Set<String>>(raw)
                    .filterNot { it in ChatboxCatalog.DEMO_THREAD_IDS }
                    .toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }

    val chatboxScheduledCalls: Flow<List<ScheduledCallEntry>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxScheduledCallsKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<ScheduledCallEntry>>(raw)
                    .filterNot { it.threadId in ChatboxCatalog.DEMO_THREAD_IDS }
            } catch (_: Exception) {
                emptyList()
            }
        }

    val chatboxReports: Flow<List<ChatboxReportEntry>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[chatboxReportsKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<ChatboxReportEntry>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val localUserProfile: Flow<LocalUserProfile> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[localUserProfileKey] ?: return@map LocalUserProfile()
            try {
                json.decodeFromString<LocalUserProfile>(raw)
            } catch (_: Exception) {
                LocalUserProfile()
            }
        }

    val localSairaSessions: Flow<List<LocalSairaSessionData>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[localSairaSessionsKey] ?: return@map emptyList()
            try {
                val cutoff = System.currentTimeMillis() - SAIRA_CHAT_RETENTION_MS
                json.decodeFromString<List<LocalSairaSessionData>>(raw)
                    .filter { it.updatedAtMs >= cutoff }
                    .sortedByDescending { it.updatedAtMs }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun observeUserAvatarPath(uid: String): Flow<String?> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[profileAvatarsKey] ?: return@map null
            try {
                val map = json.decodeFromString<Map<String, String>>(raw)
                map[uid]?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

    val dmPeerNicknames: Flow<Map<String, String>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[dmPeerNicknamesKey] ?: return@map emptyMap()
            try {
                json.decodeFromString<Map<String, String>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

    val dmMutedPeers: Flow<Set<String>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[dmMutedPeersKey] ?: return@map emptySet()
            try {
                json.decodeFromString<Set<String>>(raw)
            } catch (_: Exception) {
                emptySet()
            }
        }

    val hasCompletedLogin: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[hasCompletedLoginKey] ?: false
        }

    val storedAccountIdentifier: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[accountIdentifierKey].orEmpty()
        }

    val storedAccountPasswordHash: Flow<String> =
        context.appDataStore.data.map { prefs ->
            prefs[accountPasswordHashKey].orEmpty()
        }

    suspend fun setLoginLanguage(languageCode: String) {
        val normalized = WorldLanguages.normalizeCode(languageCode)
        context.appDataStore.edit { prefs ->
            prefs[loginLanguageKey] = normalized
        }
    }

    suspend fun setQuickReaction(reaction: String) {
        val normalized = reaction.trim().take(8).ifEmpty { "👍" }
        context.appDataStore.edit { prefs ->
            prefs[quickReactionKey] = normalized
        }
    }

    suspend fun saveSairaNotificationRules(rules: List<SairaNotificationRule>) {
        val encoded = json.encodeToString(rules)
        context.appDataStore.edit { prefs ->
            prefs[sairaNotificationRulesKey] = encoded
        }
    }

    suspend fun setSairaNotifReplyEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[sairaNotifReplyEnabledKey] = enabled
        }
    }

    suspend fun setSairaChatboxOneMinEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[sairaChatboxOneMinEnabledKey] = enabled
        }
    }

    suspend fun setSairaLiveLocationEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[sairaLiveLocationEnabledKey] = enabled
        }
    }

    suspend fun setPeerLocationAllowed(peerUid: String, allowed: Boolean) {
        val clean = peerUid.trim()
        if (clean.isEmpty()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[sairaLiveLocationAllowedPeersKey]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
            }.orEmpty()
            val updated = if (allowed) current + clean else current - clean
            prefs[sairaLiveLocationAllowedPeersKey] = json.encodeToString(updated)
        }
    }

    suspend fun appendAutoReplyLog(entry: com.example.saira.SairaAutoReplyLog) {
        context.appDataStore.edit { prefs ->
            val current = prefs[sairaAutoReplyLogsKey]?.let {
                runCatching { json.decodeFromString<List<com.example.saira.SairaAutoReplyLog>>(it) }.getOrNull()
            }.orEmpty()
            val updated = (listOf(entry) + current).take(20)
            prefs[sairaAutoReplyLogsKey] = json.encodeToString(updated)
        }
    }

    suspend fun clearAutoReplyLogs() {
        context.appDataStore.edit { prefs ->
            prefs.remove(sairaAutoReplyLogsKey)
        }
    }

    suspend fun saveSairaUserDirectory(users: List<SairaDirectoryUser>) {
        if (users.isEmpty()) {
            context.appDataStore.edit { prefs ->
                prefs.remove(sairaUserDirectoryKey)
            }
            return
        }
        val encoded = json.encodeToString(users)
        context.appDataStore.edit { prefs ->
            prefs[sairaUserDirectoryKey] = encoded
        }
    }

    suspend fun saveAiFallbackConfig(
        providerKindId: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ) {
        context.appDataStore.edit { prefs ->
            prefs[aiFallbackProviderKindKey] = providerKindId
            prefs[aiFallbackApiKeyKey] = apiKey.trim()
            prefs[aiFallbackBaseUrlKey] = baseUrl.trim()
            prefs[aiFallbackModelKey] = model.trim()
        }
    }

    suspend fun saveRememberedLoginPhone(rememberMe: Boolean, phone: String) {
        val cleaned = phone.trim().take(24)
        context.appDataStore.edit { prefs ->
            prefs[rememberMeEnabledKey] = rememberMe
            if (rememberMe && cleaned.isNotEmpty()) {
                prefs[rememberedPhoneKey] = cleaned
            } else {
                prefs.remove(rememberedPhoneKey)
            }
        }
    }

    suspend fun saveChatboxThreads(threads: List<ChatboxThread>) {
        val encoded = json.encodeToString(threads)
        context.appDataStore.edit { prefs ->
            prefs[chatboxThreadsKey] = encoded
        }
    }

    suspend fun saveChatboxMessages(messagesByThread: Map<String, List<ChatboxMessage>>) {
        val encoded = json.encodeToString(messagesByThread)
        context.appDataStore.edit { prefs ->
            prefs[chatboxMessagesKey] = encoded
        }
    }

    suspend fun saveChatboxBackupPin(pin: String) {
        val cleaned = pin.filter { it.isDigit() }.take(8)
        if (cleaned.length < 4) return
        context.appDataStore.edit { prefs ->
            prefs[chatboxBackupPinKey] = cleaned
        }
    }

    suspend fun setChatboxThreadBlocked(threadId: String, blocked: Boolean) {
        val clean = threadId.trim()
        if (clean.isEmpty()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[chatboxBlockedThreadsKey]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
            }.orEmpty()
            val updated = if (blocked) current + clean else current - clean
            prefs[chatboxBlockedThreadsKey] = json.encodeToString(updated)
        }
    }

    suspend fun saveChatboxCallLogs(entries: List<ChatboxCallEntry>) {
        val encoded = json.encodeToString(entries.take(50))
        context.appDataStore.edit { prefs ->
            prefs[chatboxCallLogsKey] = encoded
        }
    }

    suspend fun clearChatboxCallLogs() {
        context.appDataStore.edit { prefs ->
            prefs.remove(chatboxCallLogsKey)
        }
    }

    suspend fun setChatboxThreadFavorite(threadId: String, favorite: Boolean) {
        val clean = threadId.trim()
        if (clean.isEmpty()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[chatboxFavoriteThreadsKey]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
            }.orEmpty()
            val updated = if (favorite) current + clean else current - clean
            prefs[chatboxFavoriteThreadsKey] = json.encodeToString(updated)
        }
    }

    suspend fun saveScheduledCalls(entries: List<ScheduledCallEntry>) {
        val encoded = json.encodeToString(entries.take(30))
        context.appDataStore.edit { prefs ->
            prefs[chatboxScheduledCallsKey] = encoded
        }
    }

    suspend fun appendChatboxReport(entry: ChatboxReportEntry) {
        context.appDataStore.edit { prefs ->
            val current = prefs[chatboxReportsKey]?.let {
                runCatching { json.decodeFromString<List<ChatboxReportEntry>>(it) }.getOrNull()
            }.orEmpty()
            val updated = (listOf(entry) + current).take(30)
            prefs[chatboxReportsKey] = json.encodeToString(updated)
        }
    }

    suspend fun saveLocalUserProfile(profile: LocalUserProfile) {
        val encoded = json.encodeToString(profile)
        context.appDataStore.edit { prefs ->
            prefs[localUserProfileKey] = encoded
        }
    }

    suspend fun saveLocalSairaSessions(sessions: List<LocalSairaSessionData>) {
        val cutoff = System.currentTimeMillis() - SAIRA_CHAT_RETENTION_MS
        val encoded = json.encodeToString(sessions.filter { it.updatedAtMs >= cutoff }.take(50))
        context.appDataStore.edit { prefs ->
            prefs[localSairaSessionsKey] = encoded
        }
    }

    suspend fun saveUserAvatarPath(uid: String, path: String?) {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[profileAvatarsKey]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }.orEmpty().toMutableMap()
            if (path.isNullOrBlank()) {
                current.remove(cleanUid)
            } else {
                current[cleanUid] = path.trim()
            }
            prefs[profileAvatarsKey] = json.encodeToString(current)
        }
    }

    suspend fun setDmPeerNickname(peerUid: String, nickname: String) {
        val cleanUid = peerUid.trim()
        if (cleanUid.isEmpty()) return
        val cleanNick = nickname.trim().take(48)
        context.appDataStore.edit { prefs ->
            val current = prefs[dmPeerNicknamesKey]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }.orEmpty().toMutableMap()
            if (cleanNick.isEmpty()) {
                current.remove(cleanUid)
            } else {
                current[cleanUid] = cleanNick
            }
            prefs[dmPeerNicknamesKey] = json.encodeToString(current)
        }
    }

    suspend fun setDmPeerMuted(peerUid: String, muted: Boolean) {
        val cleanUid = peerUid.trim()
        if (cleanUid.isEmpty()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[dmMutedPeersKey]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrNull()
            }.orEmpty()
            val updated = if (muted) current + cleanUid else current - cleanUid
            prefs[dmMutedPeersKey] = json.encodeToString(updated)
        }
    }

    suspend fun setHasCompletedLogin(completed: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[hasCompletedLoginKey] = completed
        }
    }

    suspend fun saveAccountCredentials(identifier: String, passwordHash: String) {
        context.appDataStore.edit { prefs ->
            prefs[accountIdentifierKey] = identifier.trim()
            prefs[accountPasswordHashKey] = passwordHash
            prefs[hasCompletedLoginKey] = true
        }
    }
}
