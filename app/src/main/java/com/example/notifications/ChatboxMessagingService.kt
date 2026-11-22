package com.example.notifications

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatboxMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenRepository(applicationContext).saveTokenForCurrentUser(token)
                .onFailure { error ->
                    Log.w(TAG, "Failed to persist refreshed FCM token", error)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val conversationId = data["conversationId"]?.trim()?.takeIf { it.isNotEmpty() }
        val senderId = data["senderId"]?.trim()
        val currentUid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
        if (!senderId.isNullOrEmpty() && senderId == currentUid) {
            return
        }
        if (ActiveConversationTracker.isLiveChatsVisible &&
            conversationId != null &&
            ActiveConversationTracker.activeConversationId == conversationId
        ) {
            return
        }

        val senderName = data["senderName"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "New message"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: "Sent you a message"

        NotificationHelper.showNewMessageNotification(
            context = applicationContext,
            conversationId = conversationId,
            senderName = senderName,
            messageBody = body
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ChatboxFCM"
    }
}
