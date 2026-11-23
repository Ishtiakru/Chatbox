package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ai.AiBackupProvider
import com.example.data.ai.AiBackupProviderKind
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
    private val rememberMeEnabledKey = booleanPreferencesKey("login_remember_me_v1")
    private val rememberedPhoneKey = stringPreferencesKey("login_remembered_phone_v1")
    private val aiFallbackProviderKindKey = stringPreferencesKey("ai_fallback_provider_v1")
    private val aiFallbackApiKeyKey = stringPreferencesKey("ai_fallback_api_key_v1")
    private val aiFallbackBaseUrlKey = stringPreferencesKey("ai_fallback_base_url_v1")
    private val aiFallbackModelKey = stringPreferencesKey("ai_fallback_model_v1")
    private val aiBackupProvidersKey = stringPreferencesKey("ai_backup_providers_v2")

    val loginLanguage: Flow<String> =
        context.appDataStore.data.map { prefs ->
            com.example.data.model.WorldLanguages.normalizeCode(prefs[loginLanguageKey] ?: "en-US")
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

    /** Ordered backup chain. Falls back to the old single-provider keys once. */
    val aiBackupProviders: Flow<List<AiBackupProvider>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[aiBackupProvidersKey]
            if (!raw.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<List<AiBackupProvider>>(raw)
                }.getOrDefault(emptyList())
            } else {
                val legacyKey = prefs[aiFallbackApiKeyKey]?.trim().orEmpty()
                if (legacyKey.isNotBlank()) {
                    val kind = AiBackupProviderKind.fromId(prefs[aiFallbackProviderKindKey])
                    listOf(
                        AiBackupProvider(
                            kindId = kind.id,
                            apiKey = legacyKey,
                            baseUrl = prefs[aiFallbackBaseUrlKey].orEmpty(),
                            model = prefs[aiFallbackModelKey].orEmpty()
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }

    suspend fun setLoginLanguage(languageCode: String) {
        val normalized = com.example.data.model.WorldLanguages.normalizeCode(languageCode)
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

    suspend fun saveAiBackupProviders(providers: List<AiBackupProvider>) {
        val encoded = json.encodeToString(providers)
        context.appDataStore.edit { prefs ->
            prefs[aiBackupProvidersKey] = encoded
            // Drop the legacy single-provider keys so they cannot resurrect.
            prefs.remove(aiFallbackProviderKindKey)
            prefs.remove(aiFallbackApiKeyKey)
            prefs.remove(aiFallbackBaseUrlKey)
            prefs.remove(aiFallbackModelKey)
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
}
