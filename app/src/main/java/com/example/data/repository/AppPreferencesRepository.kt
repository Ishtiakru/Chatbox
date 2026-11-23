package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.DemoCatalog
import com.example.data.model.DemoMessage
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
    private val demoMessagesKey = stringPreferencesKey("demo_messages_v1")
    private val profileAvatarsKey = stringPreferencesKey("profile_avatars_v1")

    val loginLanguage: Flow<String> =
        context.appDataStore.data.map { prefs ->
            WorldLanguages.normalizeCode(prefs[loginLanguageKey] ?: "en-US")
        }

    val quickReaction: Flow<String> =
        context.appDataStore.data.map { prefs ->
            val saved = prefs[quickReactionKey]?.trim().orEmpty()
            if (saved.isNotEmpty() && saved.length <= 8) saved else "💜"
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

    val demoMessages: Flow<Map<String, List<DemoMessage>>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[demoMessagesKey] ?: return@map DemoCatalog.defaultSeeds
            try {
                val decoded = json.decodeFromString<Map<String, List<DemoMessage>>>(raw)
                DemoCatalog.defaultSeeds + decoded
            } catch (_: Exception) {
                DemoCatalog.defaultSeeds
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

    suspend fun setLoginLanguage(languageCode: String) {
        val normalized = WorldLanguages.normalizeCode(languageCode)
        context.appDataStore.edit { prefs ->
            prefs[loginLanguageKey] = normalized
        }
    }

    suspend fun setQuickReaction(reaction: String) {
        val normalized = reaction.trim().take(8).ifEmpty { "💜" }
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

    suspend fun saveDemoMessages(messagesByContact: Map<String, List<DemoMessage>>) {
        val encoded = json.encodeToString(messagesByContact)
        context.appDataStore.edit { prefs ->
            prefs[demoMessagesKey] = encoded
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
}
