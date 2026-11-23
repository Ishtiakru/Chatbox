package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DemoMessage
import com.example.saira.SairaNotificationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.demoDataStore: DataStore<Preferences> by preferencesDataStore(name = "chatbox_demo_prefs")

class DemoPreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val savedMessagesKey = stringPreferencesKey("saira_demo_messages_v1")
    private val loginLanguageKey = stringPreferencesKey("login_language")
    private val quickReactionKey = stringPreferencesKey("dm_quick_reaction")
    private val sairaNotificationRulesKey = stringPreferencesKey("saira_notification_rules_v1")
    private val sairaNotifReplyEnabledKey = booleanPreferencesKey("saira_notif_reply_enabled_v1")
    private val sairaChatboxOneMinEnabledKey = booleanPreferencesKey("saira_chatbox_one_min_enabled_v1")
    private val rememberMeEnabledKey = booleanPreferencesKey("login_remember_me_v1")
    private val rememberedPhoneKey = stringPreferencesKey("login_remembered_phone_v1")

    val savedMessages: Flow<Map<String, List<DemoMessage>>> =
        context.demoDataStore.data.map { prefs ->
            val raw = prefs[savedMessagesKey] ?: return@map emptyMap()
            try {
                json.decodeFromString<Map<String, List<DemoMessage>>>(raw)
            } catch (_: Exception) {
                emptyMap()
            }
        }

    val loginLanguage: Flow<String> =
        context.demoDataStore.data.map { prefs ->
            com.example.data.model.WorldLanguages.normalizeCode(prefs[loginLanguageKey] ?: "en-US")
        }

    val quickReaction: Flow<String> =
        context.demoDataStore.data.map { prefs ->
            val saved = prefs[quickReactionKey]?.trim().orEmpty()
            if (saved.isNotEmpty() && saved.length <= 8) saved else "💜"
        }

    val sairaNotificationRules: Flow<List<SairaNotificationRule>> =
        context.demoDataStore.data.map { prefs ->
            val raw = prefs[sairaNotificationRulesKey] ?: return@map emptyList()
            try {
                json.decodeFromString<List<SairaNotificationRule>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val sairaNotifReplyEnabled: Flow<Boolean> =
        context.demoDataStore.data.map { prefs ->
            prefs[sairaNotifReplyEnabledKey] ?: false
        }

    val sairaChatboxOneMinEnabled: Flow<Boolean> =
        context.demoDataStore.data.map { prefs ->
            prefs[sairaChatboxOneMinEnabledKey] ?: true
        }

    val rememberMeEnabled: Flow<Boolean> =
        context.demoDataStore.data.map { prefs ->
            prefs[rememberMeEnabledKey] ?: false
        }

    val rememberedPhone: Flow<String> =
        context.demoDataStore.data.map { prefs ->
            if (prefs[rememberMeEnabledKey] == true) {
                prefs[rememberedPhoneKey].orEmpty()
            } else {
                ""
            }
        }

    suspend fun saveDemoMessages(messages: Map<String, List<DemoMessage>>) {
        val encoded = json.encodeToString(messages)
        context.demoDataStore.edit { prefs ->
            prefs[savedMessagesKey] = encoded
        }
    }

    suspend fun setLoginLanguage(languageCode: String) {
        val normalized = com.example.data.model.WorldLanguages.normalizeCode(languageCode)
        context.demoDataStore.edit { prefs ->
            prefs[loginLanguageKey] = normalized
        }
    }

    suspend fun setQuickReaction(reaction: String) {
        val normalized = reaction.trim().take(8).ifEmpty { "💜" }
        context.demoDataStore.edit { prefs ->
            prefs[quickReactionKey] = normalized
        }
    }

    suspend fun saveSairaNotificationRules(rules: List<SairaNotificationRule>) {
        val encoded = json.encodeToString(rules)
        context.demoDataStore.edit { prefs ->
            prefs[sairaNotificationRulesKey] = encoded
        }
    }

    suspend fun setSairaNotifReplyEnabled(enabled: Boolean) {
        context.demoDataStore.edit { prefs ->
            prefs[sairaNotifReplyEnabledKey] = enabled
        }
    }

    suspend fun setSairaChatboxOneMinEnabled(enabled: Boolean) {
        context.demoDataStore.edit { prefs ->
            prefs[sairaChatboxOneMinEnabledKey] = enabled
        }
    }

    suspend fun saveRememberedLoginPhone(rememberMe: Boolean, phone: String) {
        val cleaned = phone.trim().take(24)
        context.demoDataStore.edit { prefs ->
            prefs[rememberMeEnabledKey] = rememberMe
            if (rememberMe && cleaned.isNotEmpty()) {
                prefs[rememberedPhoneKey] = cleaned
            } else {
                prefs.remove(rememberedPhoneKey)
            }
        }
    }
}
