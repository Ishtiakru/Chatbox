package com.example.saira

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiSafeResponses
import com.example.data.ai.SairaAiClient
import com.example.data.ai.SairaPrompts
import com.example.data.repository.AppPreferencesRepository
import com.example.util.NetworkMonitor
import com.example.util.configureAppCheck
import com.google.firebase.appcheck.FirebaseAppCheck
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        configureAppCheck(applicationContext, null)
        serviceScope.launch {
            SairaAiClient.warmUp()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            requestRebind(ComponentName(this, SairaNotificationListenerService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val target = sbn ?: return
        val notification = target.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = notification.extras ?: return
        if (isGroupConversation(extras)) return
        if (findReplyAction(notification) == null) return
        if (SairaNotificationMatcher.platformForPackage(target.packageName) == null) return

        val sbnKey = target.key ?: "${target.packageName}:${target.id}"
        val nowMs = System.currentTimeMillis()
        val previousPending = pendingReplyJobs.remove(sbnKey)
        previousPending?.job?.cancel()
        releaseWakeLock(previousPending?.wakeLock)
        val firstSeenMs = previousPending?.firstSeenMs ?: nowMs

        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val cleanSender = SairaNotificationMatcher.cleanContactTitle(senderTitle)
        val platform = SairaNotificationMatcher.platformForPackage(target.packageName)
        val contactKey = if (platform != null && cleanSender.isNotEmpty()) {
            "${platform.name}::${cleanSender.lowercase()}"
        } else {
            ""
        }

        // Keep CPU awake while the screen is off during the 10s wait, AI reply call, and RemoteInput dispatch
        val wakeLock = acquirePartialWakeLock(PENDING_WAKE_LOCK_TIMEOUT_MS)

        val job = serviceScope.launch {
            try {
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
            } finally {
                releaseWakeLock(wakeLock)
            }
        }
        pendingReplyJobs[sbnKey] = PendingReplyEntry(
            firstSeenMs = firstSeenMs,
            job = job,
            wakeLock = wakeLock
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val target = sbn ?: return
        val sbnKey = target.key ?: "${target.packageName}:${target.id}"
        pendingReplyJobs.remove(sbnKey)?.let { entry ->
            entry.job.cancel()
            releaseWakeLock(entry.wakeLock)
        }
    }

    internal suspend fun processNotification(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        val extras = notification.extras ?: return
        val isGroupConversation = isGroupConversation(extras)
        if (isGroupSummary || isGroupConversation) return

        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val replyAction = findReplyAction(notification) ?: return
        val packageName = sbn.packageName

        val processWakeLock = acquirePartialWakeLock(PROCESS_WAKE_LOCK_TIMEOUT_MS)
        try {
            val prefs = AppPreferencesRepository(applicationContext)
            val notifEnabled = prefs.sairaNotifReplyEnabled.first()
            val locationEnabled = prefs.sairaLiveLocationEnabled.first()
            val savedRules = prefs.sairaNotificationRules.first()
            // No contacts configured -> reply to everyone with a polite default,
            // so auto-reply works right after the one-time permission.
            val rules = if (savedRules.none { it.enabled && it.contactName.isNotBlank() && it.taughtReplyGuidance.isNotBlank() }) {
                listOf(SairaNotificationMatcher.defaultEveryoneRule())
            } else {
                savedRules
            }
            val chatboxUsers = prefs.sairaUserDirectory.first()
            val hasLocPerm = SairaLocationHelper.hasLocationPermission(applicationContext)

            val preliminaryPlatform = SairaNotificationMatcher.platformForPackage(packageName) ?: return
            val preliminarySender = SairaNotificationMatcher.cleanContactTitle(senderTitle)
            val preliminaryKey = "${preliminaryPlatform.name}::${preliminarySender.lowercase()}"
            val prevStateHint = lastContactStates[preliminaryKey]

            val payload = extractIncomingPayload(
                extras = extras,
                notificationWhenMs = notification.`when`,
                lastSentReply = prevStateHint?.lastSentReply,
                senderNameHint = preliminarySender
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
                        return@withLock
                    }
                    if (fingerprint == prevState.lastFingerprint) {
                        return@withLock
                    }
                    if (normalizedIncoming.equals(prevState.lastIncomingText, ignoreCase = true) &&
                        nowMs - prevState.repliedAtMs < DUPLICATE_TEXT_WINDOW_MS
                    ) {
                        return@withLock
                    }
                    val sinceLastReply = nowMs - prevState.repliedAtMs
                    if (sinceLastReply in 0 until MIN_REPLY_GAP_MS) {
                        delay(MIN_REPLY_GAP_MS - sinceLastReply)
                    }
                }

                val historyDeque = conversationHistory.getOrPut(contactKey) { ArrayDeque() }
                if (historyDeque.isEmpty()) {
                    val recentLogs = runCatching { prefs.sairaAutoReplyLogs.first() }.getOrDefault(emptyList())
                    val cutoffMs = nowMs - ACTIVE_CONVERSATION_WINDOW_MS
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
                } else if (prevState != null && nowMs - prevState.repliedAtMs > ACTIVE_CONVERSATION_WINDOW_MS) {
                    // Start a fresh conversation turn after prolonged inactivity so the box SMS greets them again
                    historyDeque.clear()
                }

                val historySnapshot = historyDeque.toList()
                val isContinuation = historySnapshot.isNotEmpty()

                val locationSnapshot = if (match.shouldAttachLocation) {
                    SairaLocationHelper.getBestAvailableLocation(applicationContext)
                } else {
                    null
                }

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val finalReply = if (!isContinuation) {
                    // Turn 1: Always send the owner's designated box message as the first SMS
                    SairaNotificationMatcher.buildFirstBoxReply(
                        senderName = match.normalizedSender,
                        rule = match.rule,
                        locationSnapshot = locationSnapshot,
                        hourOfDay = hour
                    )
                } else if (match.rule.useAiReply) {
                    // Turn 2+: Read the user's new SMS and respond intelligently according to what they asked
                    var connected = NetworkMonitor.isCurrentlyConnected(applicationContext)
                    if (!connected) {
                        delay(RADIO_WAKE_RETRY_MS)
                        connected = NetworkMonitor.isCurrentlyConnected(applicationContext)
                    }
                    val aiAttempt = if (connected) {
                        configureAppCheck(applicationContext, null)
                        val prompt = SairaPrompts.buildNotificationAutoReplyPrompt(
                            senderName = match.normalizedSender,
                            platformLabel = match.platform.label,
                            incomingMessage = normalizedIncoming,
                            taughtGuidance = match.rule.taughtReplyGuidance,
                            locationText = locationSnapshot?.toShareText(),
                            chatboxUsers = chatboxUsers,
                            hourOfDay = hour,
                            isContinuation = true
                        )
                        requestAiAutoReplyWithRetry(historySnapshot, prompt)
                    } else {
                        ""
                    }
                    aiAttempt.ifEmpty {
                        SairaNotificationMatcher.buildDeterministicReply(
                            senderName = match.normalizedSender,
                            rule = match.rule,
                            locationSnapshot = locationSnapshot,
                            hourOfDay = hour,
                            isContinuation = true,
                            incomingText = normalizedIncoming
                        )
                    }
                } else {
                    SairaNotificationMatcher.buildDeterministicReply(
                        senderName = match.normalizedSender,
                        rule = match.rule,
                        locationSnapshot = locationSnapshot,
                        hourOfDay = hour,
                        isContinuation = true,
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
        } finally {
            releaseWakeLock(processWakeLock)
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
                if (response.isNotEmpty() && !AiSafeResponses.isSafetyMessage(response)) return response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saira AI auto-reply attempt ${attempt + 1} failed", e)
                if (attempt == 0) {
                    runCatching {
                        withTimeoutOrNull(6_000L) {
                            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                        }
                    }
                    runCatching { SairaAiClient.warmUp() }
                    delay(600L)
                }
            }
        }
        return ""
    }

    private fun extractIncomingPayload(
        extras: Bundle,
        notificationWhenMs: Long,
        lastSentReply: String?,
        senderNameHint: String
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
                val senderPersonName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) senderPerson?.name else null
                val senderPersonKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) senderPerson?.key else null
                val senderName = (senderPersonName ?: bundle.getCharSequence("sender"))
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                val matchesSelfPerson = (selfPersonKey != null && senderPersonKey == selfPersonKey) ||
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

        // Fallback for InboxStyle multi-line notifications (common when multiple messages arrive while screen is off)
        val rawLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!rawLines.isNullOrEmpty()) {
            val cleanedLines = rawLines.mapNotNull { line ->
                val text = line?.toString()?.trim().orEmpty()
                if (text.isEmpty() || isIgnoredSystemStatusText(text)) return@mapNotNull null
                val isSelfLine = text.startsWith("You:", ignoreCase = true) ||
                    text.startsWith("Me:", ignoreCase = true) ||
                    text.startsWith("আপনি:") ||
                    (!lastSentReply.isNullOrBlank() && text.equals(lastSentReply.trim(), ignoreCase = true))
                text to isSelfLine
            }
            if (cleanedLines.isNotEmpty()) {
                if (cleanedLines.last().second) return null
                val trailing = mutableListOf<String>()
                for ((lineText, isSelfLine) in cleanedLines.asReversed()) {
                    if (isSelfLine) break
                    trailing.add(stripSenderPrefix(lineText, senderNameHint))
                }
                if (trailing.isNotEmpty()) {
                    return IncomingPayload(
                        text = trailing.asReversed().joinToString("\n").take(450),
                        messageTimeMs = notificationWhenMs
                    )
                }
            }
        }

        val primaryText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val resolvedText = when {
            bigText.isNotEmpty() && (primaryText.isEmpty() || isIgnoredSystemStatusText(primaryText)) -> bigText
            primaryText.isNotEmpty() && !isIgnoredSystemStatusText(primaryText) -> primaryText
            bigText.isNotEmpty() && !isIgnoredSystemStatusText(bigText) -> bigText
            else -> return null
        }

        return IncomingPayload(
            text = stripSenderPrefix(resolvedText, senderNameHint),
            messageTimeMs = notificationWhenMs
        )
    }

    private fun stripSenderPrefix(text: String, senderNameHint: String): String {
        val clean = text.trim()
        if (senderNameHint.isNotBlank() && clean.startsWith("$senderNameHint:", ignoreCase = true)) {
            return clean.substring(senderNameHint.length + 1).trim().ifEmpty { clean }
        }
        return clean
    }

    private fun isGroupConversation(extras: Bundle): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        } else {
            extras.getBoolean("android.isGroupConversation", false)
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

    private fun acquirePartialWakeLock(timeoutMs: Long): PowerManager.WakeLock? {
        return runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        }.getOrNull()
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock == null) return
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingReplyJobs.values.forEach { entry ->
            entry.job.cancel()
            releaseWakeLock(entry.wakeLock)
        }
        pendingReplyJobs.clear()
        serviceScope.cancel()
    }

    private data class PendingReplyEntry(
        val firstSeenMs: Long,
        val job: Job,
        val wakeLock: PowerManager.WakeLock?
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
        private const val WAKE_LOCK_TAG = "Chatbox:SairaAutoReplyWakeLock"
        private const val PENDING_WAKE_LOCK_TIMEOUT_MS = 75_000L
        private const val PROCESS_WAKE_LOCK_TIMEOUT_MS = 60_000L
        private const val RADIO_WAKE_RETRY_MS = 1_200L
        private const val INITIAL_ABSENCE_WAIT_MS = 10_000L
        private const val CONTINUATION_DEBOUNCE_MS = 1_500L
        private const val ACTIVE_CONVERSATION_WINDOW_MS = 15 * 60_000L
        private const val MIN_REPLY_GAP_MS = 1_000L
        private const val DUPLICATE_TEXT_WINDOW_MS = 6_000L
        private const val ECHO_SUPPRESS_WINDOW_MS = 30_000L
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

        const val FULL_EDITION_URL = "https://github.com/Ishtiakru/Chatbox/releases/latest/download/chatbox-full.apk"

        /** Opens Google Play Protect settings (to switch off "Scan apps" before installing the full APK). */
        fun openPlayProtectSettings(context: Context) {
            val candidates = listOf(
                Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS"),
                Intent().setClassName(
                    "com.google.android.gms",
                    "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"
                ),
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
                    .setPackage("com.android.vending"),
                context.packageManager.getLaunchIntentForPackage("com.android.vending")
            )
            for (intent in candidates) {
                if (intent == null) continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }

        /**
         * True only in the "full" APK, where the listener is declared in the
         * manifest. The standard APK leaves it out so Play Protect lets it install.
         */
        fun isListenerAvailable(context: Context): Boolean = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(
                ComponentName(context, SairaNotificationListenerService::class.java),
                0
            )
            true
        }.getOrDefault(false)

        /** Asks Android to (re)connect the listener — some phones drop it after updates. */
        fun ensureBound(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, SairaNotificationListenerService::class.java))
            }
        }

        /** Opens Chatbox's own Notification Access toggle directly (Android 11+), else the list. */
        fun openNotificationAccessForApp(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val component = ComponentName(context, SairaNotificationListenerService::class.java)
                val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(detail) }.isSuccess) return
            }
            openNotificationAccessSettings(context)
        }

        /** One system "Allow" dialog so replies keep working with the screen off. */
        @android.annotation.SuppressLint("BatteryLife")
        fun requestIgnoreBatteryOptimization(context: Context) {
            if (isBatteryOptimizationIgnored(context)) return
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(request) }.isSuccess) return
            openBatteryOptimizationSettings(context)
        }

        /** App info page — where "Allow restricted settings" lives on Android 13+. */
        fun openAppDetailsSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun openBatteryOptimizationSettings(context: Context) {
            val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(settingsIntent) }
                .onFailure {
                    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(appDetails) }
                }
        }
    }
}
