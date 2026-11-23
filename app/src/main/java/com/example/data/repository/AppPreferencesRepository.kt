package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
