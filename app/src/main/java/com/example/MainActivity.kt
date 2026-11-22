package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.repository.ChatboxFirestoreProvider
import com.example.notifications.NotificationHelper
import com.example.ui.ChatboxAppRoot

class MainActivity : ComponentActivity() {
    private var pendingConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAppCheck(this, intent)
        ChatboxFirestoreProvider.getInstance(this)
        NotificationHelper.ensureMessageChannel(this)
        pendingConversationId = extractConversationId(intent)
        setContent {
            ChatboxAppRoot(
                pendingConversationId = pendingConversationId,
                onConsumePendingConversation = { pendingConversationId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractConversationId(intent)?.let { id ->
            pendingConversationId = id
        }
    }

    private fun extractConversationId(intent: Intent?): String? {
        return intent?.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
