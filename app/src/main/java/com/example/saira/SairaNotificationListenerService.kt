package com.example.saira

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.ai.AiChatMessage
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
import com.example.data.repository.AppPreferencesRepository
import com.example.util.NetworkMonitor
import com.example.util.configureAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class SairaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contactMutexes = ConcurrentHashMap<String, Mutex>()
    private val lastContactStates = ConcurrentHashMap<String, ContactReplyState>()
    private val conversationHistory = ConcurrentHashMap<String, ArrayDeque<AiChatMessage>>()
    private val pendingReplyJobs = ConcurrentHashMap<String, PendingReplyEntry>()

    override fun onCreate() {
        super.onCreate()
        configureAppCheck(applicationContext, null)
        serviceScope.launch {
            SairaAiClient.warmUp()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val target = sbn ?: return
        val notification = target.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = notification.extras ?: return
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return
        if (findReplyAction(notification) == null) return
        if (SairaNotificationMatcher.platformForPackage(target.packageName) == null) return

        val sbnKey = target.key ?: "${target.packageName}:${target.id}"
        val nowMs = System.currentTimeMillis()
        val previousPending = pendingReplyJobs.remove(sbnKey)
        previousPending?.job?.cancel()
        val firstSeenMs = previousPending?.firstSeenMs ?: nowMs

        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val cleanSender = SairaNotificationMatcher.cleanContactTitle(senderTitle)
        val platform = SairaNotificationMatcher.platformForPackage(target.packageName)
        val contactKey = if (platform != null && cleanSender.isNotEmpty()) {
            "${platform.name}::${cleanSender.lowercase()}"
        } else {
            ""
        }

        val job = serviceScope.launch {
            val prevState = if (contactKey.isNotEmpty()) lastContactStates[contactKey] else null
            val isActiveConversation = prevState != null &&
                (System.currentTimeMillis() - prevState.repliedAtMs) <= ACTIVE_CONVERSATION_WINDOW_MS

            val targetDelayMs = if (isActiveConversation) {
                CONTINUATION_DEBOUNCE_MS
            } else {
                // Wait up to 10 seconds from first message so owner can reply first;
                // if owner does not answer within 10s, Saira steps in automatically.
                val elapsedMs = (System.currentTimeMillis() - firstSeenMs).coerceAtLeast(0L)
                (INITIAL_ABSENCE_WAIT_MS - elapsedMs).coerceAtLeast(CONTINUATION_DEBOUNCE_MS)
            }
            delay(targetDelayMs)
            pendingReplyJobs.remove(sbnKey)
            processNotification(target)
        }
        pendingReplyJobs[sbnKey] = PendingReplyEntry(firstSeenMs = firstSeenMs, job = job)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val target = sbn ?: return
        val sbnKey = target.key ?: "${target.packageName}:${target.id}"
        pendingReplyJobs.remove(sbnKey)?.job?.cancel()
    }

    internal suspend fun processNotification(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val extras = notification.extras ?: return
        val isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroupSummary || isGroupConversation) return

        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val replyAction = findReplyAction(notification) ?: return
        val packageName = sbn.packageName

        val prefs = AppPreferencesRepository(applicationContext)
        val notifEnabled = prefs.sairaNotifReplyEnabled.first()
        val locationEnabled = prefs.sairaLiveLocationEnabled.first()
        val rules = prefs.sairaNotificationRules.first()
        val chatboxUsers = prefs.sairaUserDirectory.first()
        val hasLocPerm = SairaLocationHelper.hasLocationPermission(applicationContext)

        val preliminaryPlatform = SairaNotificationMatcher.platformForPackage(packageName) ?: return
        val preliminarySender = SairaNotificationMatcher.cleanContactTitle(senderTitle)
        val preliminaryKey = "${preliminaryPlatform.name}::${preliminarySender.lowercase()}"
        val prevStateHint = lastContactStates[preliminaryKey]

        val payload = extractIncomingPayload(
            extras = extras,
            notificationWhenMs = notification.`when`,
            lastSentReply = prevStateHint?.lastSentReply
        ) ?: return
        if (isIgnoredSystemStatusText(payload.text)) return

        val match = SairaNotificationMatcher.match(
            notifAutoReplyEnabled = notifEnabled,
            liveLocationEnabled = locationEnabled,
            locationPermissionGranted = hasLocPerm,
            packageName = packageName,
            senderTitle = senderTitle,
            incomingText = payload.text,
            isGroupSummary = isGroupSummary,
            rules = rules
        ) ?: return

        val contactKey = "${match.platform.name}::${match.normalizedSender.lowercase()}"
        val mutex = contactMutexes.getOrPut(contactKey) { Mutex() }

        mutex.withLock {
            val nowMs = System.currentTimeMillis()
            val prevState = lastContactStates[contactKey]
            val normalizedIncoming = payload.text.trim()
            val fingerprint = "${normalizedIncoming.lowercase()}::${payload.messageTimeMs}"

            if (prevState != null) {
                // Skip self-reply echoes or duplicate notification updates for the exact same message
                if (normalizedIncoming.equals(prevState.lastSentReply.trim(), ignoreCase = true) &&
                    nowMs - prevState.repliedAtMs < ECHO_SUPPRESS_WINDOW_MS
                ) {
                    return
                }
                if (fingerprint == prevState.lastFingerprint) {
                    return
                }
                if (normalizedIncoming.equals(prevState.lastIncomingText, ignoreCase = true) &&
                    nowMs - prevState.repliedAtMs < DUPLICATE_TEXT_WINDOW_MS
                ) {
                    return
                }
                val sinceLastReply = nowMs - prevState.repliedAtMs
                if (sinceLastReply in 0 until MIN_REPLY_GAP_MS) {
                    delay(MIN_REPLY_GAP_MS - sinceLastReply)
                }
            }

            val historyDeque = conversationHistory.getOrPut(contactKey) { ArrayDeque() }
            if (historyDeque.isEmpty()) {
                val recentLogs = runCatching { prefs.sairaAutoReplyLogs.first() }.getOrDefault(emptyList())
                val cutoffMs = nowMs - HISTORY_RETENTION_MS
                recentLogs
                    .asSequence()
                    .filter {
                        it.contactName.equals(match.normalizedSender, ignoreCase = true) &&
                            it.platformLabel == match.platform.label &&
                            it.timestampMs >= cutoffMs &&
                            it.incomingPreview.isNotBlank() &&
                            it.sentReply.isNotBlank()
                    }
                    .take(MAX_HISTORY_TURNS)
                    .toList()
                    .asReversed()
                    .forEach { entry ->
                        historyDeque.addLast(AiChatMessage("user", entry.incomingPreview))
                        historyDeque.addLast(AiChatMessage("model", entry.sentReply))
                    }
            }

            val historySnapshot = historyDeque.toList()
            val isContinuation = historySnapshot.isNotEmpty()

            val locationSnapshot = if (match.shouldAttachLocation) {
                SairaLocationHelper.getBestAvailableLocation(applicationContext)
            } else {
                null
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val finalReply = if (match.rule.useAiReply && NetworkMonitor.isCurrentlyConnected(applicationContext)) {
                configureAppCheck(applicationContext, null)
                val prompt = SairaPrompts.buildNotificationAutoReplyPrompt(
                    senderName = match.normalizedSender,
                    platformLabel = match.platform.label,
                    incomingMessage = normalizedIncoming,
                    taughtGuidance = match.rule.taughtReplyGuidance,
                    locationText = locationSnapshot?.toShareText(),
                    chatboxUsers = chatboxUsers,
                    hourOfDay = hour,
                    isContinuation = isContinuation
                )
                val aiAttempt = requestAiAutoReplyWithRetry(historySnapshot, prompt)
                aiAttempt.ifEmpty {
                    SairaNotificationMatcher.buildDeterministicReply(
                        senderName = match.normalizedSender,
                        rule = match.rule,
                        locationSnapshot = locationSnapshot,
                        hourOfDay = hour,
                        isContinuation = isContinuation,
                        incomingText = normalizedIncoming
                    )
                }
            } else {
                SairaNotificationMatcher.buildDeterministicReply(
                    senderName = match.normalizedSender,
                    rule = match.rule,
                    locationSnapshot = locationSnapshot,
                    hourOfDay = hour,
                    isContinuation = isContinuation,
                    incomingText = normalizedIncoming
                )
            }

            val sentOk = dispatchRemoteInputReply(replyAction.action, replyAction.remoteInputs, finalReply)
            if (sentOk) {
                val sentAtMs = System.currentTimeMillis()
                lastContactStates[contactKey] = ContactReplyState(
                    lastFingerprint = fingerprint,
                    lastIncomingText = normalizedIncoming,
                    lastSentReply = finalReply,
                    repliedAtMs = sentAtMs
                )

                historyDeque.addLast(AiChatMessage("user", normalizedIncoming.take(300)))
                historyDeque.addLast(AiChatMessage("model", finalReply.take(400)))
                while (historyDeque.size > MAX_HISTORY_MESSAGES) {
                    historyDeque.removeFirst()
                }

                prefs.appendAutoReplyLog(
                    SairaAutoReplyLog(
                        id = "log_${sentAtMs}_${Random.nextInt(100, 999)}",
                        contactName = match.normalizedSender,
                        platformLabel = match.platform.label,
                        incomingPreview = normalizedIncoming.take(120),
                        sentReply = finalReply.take(280),
                        includedLocation = locationSnapshot != null,
                        timestampMs = sentAtMs
                    )
                )
            }
        }
    }

    private suspend fun requestAiAutoReplyWithRetry(
        history: List<AiChatMessage>,
        prompt: String
    ): String {
        for (attempt in 0..1) {
            try {
                val response = SairaAiClient.complete(
                    systemInstruction = SairaPrompts.autoReplySystemInstruction,
                    history = history,
                    prompt = prompt
                ).trim()
                if (response.isNotEmpty()) return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saira AI auto-reply attempt ${attempt + 1} failed", e)
                if (attempt == 0) {
                    runCatching { SairaAiClient.warmUp() }
                    delay(500L)
                }
            }
        }
        return ""
    }

    private fun extractIncomingPayload(
        extras: Bundle,
        notificationWhenMs: Long,
        lastSentReply: String?
    ): IncomingPayload? {
        @Suppress("DEPRECATION")
        val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!rawMessages.isNullOrEmpty()) {
            val selfPerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
            } else {
                null
            }
            val selfPersonKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) selfPerson?.key else null
            val selfPersonName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                selfPerson?.name?.toString()?.trim().orEmpty()
            } else {
                ""
            }

            val parsed = rawMessages.mapNotNull { item ->
                val bundle = item as? Bundle ?: return@mapNotNull null
                val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                if (msgText.isEmpty()) return@mapNotNull null
                val rawTimeMs = bundle.getLong("time", 0L)
                val timeMs = if (rawTimeMs > 0L) rawTimeMs else notificationWhenMs
                val senderPerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable<Person>("sender_person")
                } else {
                    null
                }
                val senderName = (senderPerson?.name ?: bundle.getCharSequence("sender"))
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                val matchesSelfPerson = (selfPersonKey != null && senderPerson?.key == selfPersonKey) ||
                    (selfPersonName.isNotEmpty() && senderName.equals(selfPersonName, ignoreCase = true))
                val matchesLastReply = !lastSentReply.isNullOrBlank() &&
                    msgText.equals(lastSentReply.trim(), ignoreCase = true)
                val isSelf = matchesSelfPerson ||
                    matchesLastReply ||
                    senderName.equals("You", ignoreCase = true) ||
                    senderName.equals("Me", ignoreCase = true) ||
                    senderName == "আপনি"
                ParsedMessageEntry(text = msgText, timeMs = timeMs, isSelf = isSelf)
            }
            if (parsed.isNotEmpty()) {
                // If WhatsApp/Messenger re-posted showing our own just-sent reply as the newest message, skip
                if (parsed.last().isSelf) return null
                // Collect consecutive trailing unread incoming messages after any self message
                val trailingIncoming = mutableListOf<ParsedMessageEntry>()
                for (entry in parsed.asReversed()) {
                    if (entry.isSelf) break
                    trailingIncoming.add(entry)
                }
                if (trailingIncoming.isNotEmpty()) {
                    val chronological = trailingIncoming.asReversed()
                    val combinedText = chronological.joinToString("\n") { it.text }.take(450)
                    val latestTimeMs = chronological.last().timeMs.takeIf { it > 0L } ?: notificationWhenMs
                    return IncomingPayload(
                        text = combinedText,
                        messageTimeMs = latestTimeMs
                    )
                }
            }
        }

        val fallbackText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: return null

        return IncomingPayload(
            text = fallbackText,
            messageTimeMs = notificationWhenMs
        )
    }

    private fun isIgnoredSystemStatusText(text: String): Boolean {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return true
        if (clean in SYSTEM_STATUS_TEXTS) return true
        return NEW_MESSAGES_SUMMARY_REGEX.matches(clean)
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
        pendingReplyJobs.values.forEach { it.job.cancel() }
        pendingReplyJobs.clear()
        serviceScope.cancel()
    }

    private data class PendingReplyEntry(
        val firstSeenMs: Long,
        val job: Job
    )

    private data class IncomingPayload(
        val text: String,
        val messageTimeMs: Long
    )

    private data class ParsedMessageEntry(
        val text: String,
        val timeMs: Long,
        val isSelf: Boolean
    )

    private data class ContactReplyState(
        val lastFingerprint: String,
        val lastIncomingText: String,
        val lastSentReply: String,
        val repliedAtMs: Long
    )

    private data class ReplyTarget(
        val action: Notification.Action,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private const val TAG = "SairaNotifListener"
        private const val INITIAL_ABSENCE_WAIT_MS = 10_000L
        private const val CONTINUATION_DEBOUNCE_MS = 1_500L
        private const val ACTIVE_CONVERSATION_WINDOW_MS = 10 * 60_000L
        private const val MIN_REPLY_GAP_MS = 1_000L
        private const val DUPLICATE_TEXT_WINDOW_MS = 6_000L
        private const val ECHO_SUPPRESS_WINDOW_MS = 30_000L
        private const val HISTORY_RETENTION_MS = 2 * 60 * 60_000L
        private const val MAX_HISTORY_TURNS = 7
        private const val MAX_HISTORY_MESSAGES = MAX_HISTORY_TURNS * 2

        private val NEW_MESSAGES_SUMMARY_REGEX = Regex("""^\d+\s+new\s+messages?$""")
        private val SYSTEM_STATUS_TEXTS = setOf(
            "checking for new messages",
            "new message",
            "new messages",
            "calling…",
            "calling...",
            "ringing…",
            "ringing...",
            "ongoing voice call",
            "ongoing video call",
            "incoming voice call",
            "incoming video call",
            "missed voice call",
            "missed video call"
        )

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
