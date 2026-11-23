package com.example.saira

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
import com.example.data.repository.AppPreferencesRepository
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class SairaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentReplyTimestamps = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val target = sbn ?: return
        serviceScope.launch {
            processNotification(target)
        }
    }

    internal suspend fun processNotification(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        val extras = notification.extras ?: return
        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val replyAction = findReplyAction(notification) ?: return
        val packageName = sbn.packageName

        val prefs = AppPreferencesRepository(applicationContext)
        val notifEnabled = prefs.sairaNotifReplyEnabled.first()
        val locationEnabled = prefs.sairaLiveLocationEnabled.first()
        val rules = prefs.sairaNotificationRules.first()
        val hasLocPerm = SairaLocationHelper.hasLocationPermission(applicationContext)

        val match = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = notifEnabled,
            liveLocationEnabled = locationEnabled,
            locationPermissionGranted = hasLocPerm,
            packageName = packageName,
            senderTitle = senderTitle,
            incomingText = bodyText,
            isGroupSummary = isGroupSummary,
            rules = rules
        ) ?: return

        val cooldownKey = "${match.platform.name}::${match.normalizedSender.lowercase()}"
        val nowMs = System.currentTimeMillis()
        val prevMs = recentReplyTimestamps[cooldownKey] ?: 0L
        if (nowMs - prevMs < REPLY_COOLDOWN_MS) return
        recentReplyTimestamps[cooldownKey] = nowMs

        val locationSnapshot = if (match.shouldAttachLocation) {
            SairaLocationHelper.getBestAvailableLocation(applicationContext)
        } else {
            null
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val finalReply = if (match.rule.useAiReply && NetworkMonitor.isCurrentlyConnected(applicationContext)) {
            val aiAttempt = runCatching {
                val prompt = SairaPrompts.buildNotificationAutoReplyPrompt(
                    senderName = match.normalizedSender,
                    platformLabel = match.platform.label,
                    incomingMessage = bodyText.orEmpty(),
                    taughtGuidance = match.rule.taughtReplyGuidance,
                    locationText = locationSnapshot?.toShareText(),
                    hourOfDay = hour
                )
                SairaAiClient.complete(SairaPrompts.autoReplySystemInstruction, emptyList(), prompt)
            }.getOrNull()?.trim().orEmpty()

            if (aiAttempt.isNotEmpty()) aiAttempt
            else SairaNotificationMatcher.buildDeterministicReply(
                senderName = match.normalizedSender,
                rule = match.rule,
                locationSnapshot = locationSnapshot,
                hourOfDay = hour
            )
        } else {
            SairaNotificationMatcher.buildDeterministicReply(
                senderName = match.normalizedSender,
                rule = match.rule,
                locationSnapshot = locationSnapshot,
                hourOfDay = hour
            )
        }

        val sentOk = dispatchRemoteInputReply(replyAction.action, replyAction.remoteInputs, finalReply)
        if (sentOk) {
            prefs.appendAutoReplyLog(
                SairaAutoReplyLog(
                    id = "log_${nowMs}_${Random.nextInt(100, 999)}",
                    contactName = match.normalizedSender,
                    platformLabel = match.platform.label,
                    incomingPreview = bodyText.orEmpty().take(120),
                    sentReply = finalReply.take(280),
                    includedLocation = locationSnapshot != null,
                    timestampMs = nowMs
                )
            )
        }
    }

    private fun dispatchRemoteInputReply(
        action: Notification.Action,
        remoteInputs: Array<RemoteInput>,
        replyText: String
    ): Boolean {
        return try {
            val fillInIntent = Intent()
            val results = Bundle()
            for (input in remoteInputs) {
                results.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)
            action.actionIntent.send(applicationContext, 0, fillInIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch RemoteInput notification reply", e)
            false
        }
    }

    private fun findReplyAction(notification: Notification): ReplyTarget? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val inputs = action.remoteInputs ?: continue
            val editableInputs = inputs.filter { it.allowFreeFormInput }.toTypedArray()
            if (editableInputs.isNotEmpty() && action.actionIntent != null) {
                return ReplyTarget(action, editableInputs)
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private data class ReplyTarget(
        val action: Notification.Action,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private const val TAG = "SairaNotifListener"
        private const val REPLY_COOLDOWN_MS = 30_000L

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val target = ComponentName(context, SairaNotificationListenerService::class.java)
            return flat.split(":").any { item ->
                val parsed = ComponentName.unflattenFromString(item.trim())
                parsed == target || item.contains(SairaNotificationListenerService::class.java.name)
            }
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
