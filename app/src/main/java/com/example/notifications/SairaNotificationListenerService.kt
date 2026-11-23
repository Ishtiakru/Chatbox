package com.example.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.repository.AppPreferencesRepository
import com.example.saira.SairaAssistantEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SairaNotificationListenerService : NotificationListenerService() {

    private companion object {
        const val TAG = "SairaNotifListener"
        const val MAX_CACHE_ENTRIES = 120
        const val CONTACT_COOLDOWN_MS = 10_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsRepository by lazy { AppPreferencesRepository(applicationContext) }
    private val recentProcessedFingerprints = LinkedHashMap<String, Long>(MAX_CACHE_ENTRIES, 0.75f, true)
    private val lastReplyByContact = mutableMapOf<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        if (SairaAssistantEngine.detectPlatform(sbn.packageName) == null) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val incomingText = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()

        if (senderTitle.isBlank() || incomingText.isBlank()) return

        val fingerprint = "${sbn.packageName}|${sbn.key}|$senderTitle|$incomingText|${sbn.postTime}"
        val now = System.currentTimeMillis()
        synchronized(recentProcessedFingerprints) {
            if (recentProcessedFingerprints.containsKey(fingerprint)) return
            recentProcessedFingerprints[fingerprint] = now
            if (recentProcessedFingerprints.size > MAX_CACHE_ENTRIES) {
                val eldestKey = recentProcessedFingerprints.entries.firstOrNull()?.key
                if (eldestKey != null) recentProcessedFingerprints.remove(eldestKey)
            }
        }

        serviceScope.launch {
            val autoReplyEnabled = runCatching { prefsRepository.sairaNotifReplyEnabled.first() }.getOrDefault(false)
            if (!autoReplyEnabled) return@launch

            val rules = runCatching { prefsRepository.sairaNotificationRules.first() }.getOrDefault(emptyList())
            val matchedRule = SairaAssistantEngine.matchNotificationRule(
                packageName = sbn.packageName,
                senderTitle = senderTitle,
                conversationTitle = conversationTitle,
                rules = rules
            ) ?: return@launch

            val contactCooldownKey = "${sbn.packageName}:${matchedRule.id}"
            synchronized(lastReplyByContact) {
                val previousReplyAt = lastReplyByContact[contactCooldownKey] ?: 0L
                if (System.currentTimeMillis() - previousReplyAt < CONTACT_COOLDOWN_MS) {
                    return@launch
                }
                lastReplyByContact[contactCooldownKey] = System.currentTimeMillis()
            }

            val replyText = SairaAssistantEngine.generateTargetedNotificationReply(
                rule = matchedRule,
                senderName = senderTitle,
                incomingMessage = incomingText
            )
            val sent = SairaAssistantEngine.dispatchNotificationReply(
                context = applicationContext,
                sbn = sbn,
                replyText = replyText
            )
            Log.d(TAG, "Targeted notification reply for ${matchedRule.contactName}: sent=$sent")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    object Helper {
        fun isNotificationAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun componentName(context: Context): ComponentName {
            return ComponentName(context, SairaNotificationListenerService::class.java)
        }
    }
}
