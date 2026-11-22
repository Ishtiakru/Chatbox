package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DemoMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.demoDataStore: DataStore<Preferences> by preferencesDataStore(name = "chatbox_demo_prefs")

class DemoPreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val savedMessagesKey = stringPreferencesKey("saira_demo_messages_v1")
    private val loginLanguageKey = stringPreferencesKey("login_language")
    private val quickReactionKey = stringPreferencesKey("dm_quick_reaction")

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
}
