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
        connected = this
        configureAppCheck(applicationContext, null)
        SairaKeepAliveService.sync(applicationContext)
        serviceScope.launch {
            SairaAiClient.warmUp()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (connected === this) connected = null
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
        // Measure the 10s from when the FIRST message actually arrived (postTime),
        // even if WhatsApp re-posted/updated the notification since.
        val postedAtMs = target.postTime.takeIf { it in 1..nowMs } ?: nowMs
        val firstSeenMs = previousPending?.firstSeenMs
            ?: recentlyRemoved.remove(sbnKey)?.takeIf { nowMs - it.removedAtMs < REPOST_GRACE_MS }?.firstSeenMs
            ?: postedAtMs

        val senderTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val cleanSender = SairaNotificationMatcher.cleanContactTitle(senderTitle)
        val platform = SairaNotificationMatcher.platformForPackage(target.packageName)
        val contactKey = if (platform != null && cleanSender.isNotEmpty()) {
            "${platform.name}::${cleanSender.lowercase()}"
        } else {
            ""
        }

        recordStatus(this, "${platform?.label ?: "Message"} · $cleanSender: মেসেজ পেয়েছি, অপেক্ষা করছি…")

        // Keep CPU awake while the screen is off during the 10s wait, AI reply call, and RemoteInput dispatch
        val wakeLock = acquirePartialWakeLock(PENDING_WAKE_LOCK_TIMEOUT_MS)

        // Use the waiting time: wake the radio / TLS connection and fetch location
        // in parallel so the reply goes out right at the 10s mark.
        prewarm(extras)

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
                // Owner opened/answered the chat during the wait -> WhatsApp removed
                // the notification. Only then skip (a re-post keeps it active).
                val stillActive = runCatching {
                    activeNotifications?.any { it.key == sbnKey }
                }.getOrNull() ?: true
                if (!stillActive) {
                    recordStatus(applicationContext, "$cleanSender: আপনি নিজে মেসেজটি খুলেছেন/উত্তর দিয়েছেন — সায়রা উত্তর দেয়নি")
                    return@launch
                }
                // Never let one stuck reply (network/location) block later messages.
                val finished = withTimeoutOrNull(PROCESS_TIMEOUT_MS) { processNotification(target); true }
                if (finished == null) recordStatus(applicationContext, "$cleanSender: উত্তর দিতে বেশি সময় লাগছিল — বাতিল, পরের মেসেজে আবার চেষ্টা করবে")
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
        // Do NOT cancel here: WhatsApp/Messenger often remove + re-post the same
        // notification, which used to restart the 10s timer or drop the reply.
        // The pending job re-checks activeNotifications when the 10s are up.
        val target = sbn ?: return
        val sbnKey = target.key ?: "${target.packageName}:${target.id}"
        pendingReplyJobs[sbnKey]?.let { entry ->
            recentlyRemoved[sbnKey] = RemovedEntry(entry.firstSeenMs, System.currentTimeMillis())
        }
    }

    private val recentlyRemoved = ConcurrentHashMap<String, RemovedEntry>()

    private data class RemovedEntry(val firstSeenMs: Long, val removedAtMs: Long)

    private fun prewarm(extras: Bundle) {
        serviceScope.launch {
            runCatching {
                withTimeoutOrNull(6_000L) {
                    val conn = java.net.URL("https://generativelanguage.googleapis.com/").openConnection()
                        as java.net.HttpURLConnection
                    conn.connectTimeout = 5_000
                    conn.readTimeout = 5_000
                    conn.requestMethod = "GET"
                    runCatching { conn.responseCode }
                    runCatching { (conn.errorStream ?: conn.inputStream)?.use { it.readBytes() } }
                }
            }
        }
        val text = buildString {
            append(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            append(' ')
            append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty())
        }
        if (SairaTimeGreeting.isLocationRequest(text) && SairaLocationHelper.hasLocationPermission(this)) {
            serviceScope.launch { runCatching { SairaLocationHelper.getFreshLocation(applicationContext) } }
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
            ) ?: run {
                recordStatus(applicationContext, "$preliminarySender: নতুন মেসেজ নেই (শেষটা আপনার) — বাদ")
                return
            }
            if (isIgnoredSystemStatusText(payload.text)) return
            if (!notifEnabled) {
                recordStatus(applicationContext, "$preliminarySender: অটো-রিপ্লাই সুইচ বন্ধ — বাদ")
                return
            }

            val match = SairaNotificationMatcher.match(
                notifAutoReplyEnabled = notifEnabled,
                liveLocationEnabled = locationEnabled,
                locationPermissionGranted = hasLocPerm,
                packageName = packageName,
                senderTitle = senderTitle,
                incomingText = payload.text,
                isGroupSummary = isGroupSummary,
                rules = rules
            ) ?: run {
                recordStatus(applicationContext, "$preliminarySender: আপনার সেট করা নামের সাথে মেলেনি — বাদ")
                return
            }

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
                    SairaLocationHelper.getFreshLocation(applicationContext)
                } else {
                    null
                }

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                // Photos: Saira really looks at them (from the notification) before answering.
                val photos = loadNotificationPhotos(payload, extras)
                val photoSent = photos.isNotEmpty() || payload.imageUris.isNotEmpty() ||
                    SairaPrompts.looksLikePhotoMessage(normalizedIncoming)
                val photoReply = if (photos.isNotEmpty() && match.rule.useAiReply &&
                    NetworkMonitor.isCurrentlyConnected(applicationContext)
                ) {
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
                    ) + SairaPrompts.visionGuidance
                    SairaAiClient.completeWithImages(SairaPrompts.autoReplySystemInstruction, historySnapshot, prompt, photos)
                        .takeUnless { AiSafeResponses.isSafetyMessage(it) }.orEmpty()
                } else ""
                val finalReply = if (photoReply.isNotEmpty()) {
                    if (!isContinuation) {
                        SairaNotificationMatcher.buildFirstBoxReply(
                            senderName = match.normalizedSender,
                            rule = match.rule,
                            locationSnapshot = locationSnapshot,
                            hourOfDay = hour
                        ) + "\n\n" + photoReply
                    } else photoReply
                } else if (!isContinuation) {
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
                        ) + (if (photoSent) SairaPrompts.photoUnavailableNote else "")
                        // Hard cap: a slow/blocked AI call must never hold this contact's lock.
                        withTimeoutOrNull(AI_REPLY_TIMEOUT_MS) {
                            requestAiAutoReplyWithRetry(historySnapshot, prompt)
                        }.orEmpty()
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

                val replyToSend = if (locationSnapshot != null && !finalReply.contains("maps.google.com")) {
                    "$finalReply\n${locationSnapshot.toShareText()}"
                } else {
                    finalReply
                }
                val locationNote = when {
                    !SairaTimeGreeting.isLocationRequest(normalizedIncoming) -> ""
                    !locationEnabled -> " (লোকেশন চেয়েছিল, কিন্তু লাইভ লোকেশন সুইচ বন্ধ)"
                    !hasLocPerm -> " (লোকেশন চেয়েছিল, কিন্তু Location অনুমতি নেই)"
                    !match.rule.allowLocationShare -> " (লোকেশন চেয়েছিল, কিন্তু এই ব্যক্তির জন্য Location বন্ধ)"
                    locationSnapshot == null -> " (লোকেশন চেয়েছিল, ফোনের Location/GPS বন্ধ থাকায় পাওয়া যায়নি)"
                    else -> " + লোকেশন ✓"
                }
                val sentOk = dispatchRemoteInputReply(replyAction.action, replyAction.remoteInputs, replyToSend)
                recordStatus(
                    applicationContext,
                    if (sentOk) "${match.platform.label} · ${match.normalizedSender}: সায়রা উত্তর পাঠিয়েছে ✓$locationNote"
                    else "${match.platform.label} · ${match.normalizedSender}: উত্তর পাঠানো যায়নি (WhatsApp নোটিফিকেশন বন্ধ হয়ে গেছে)"
                )
                if (sentOk) {
                    val sentAtMs = System.currentTimeMillis()
                    lastContactStates[contactKey] = ContactReplyState(
                        lastFingerprint = fingerprint,
                        lastIncomingText = normalizedIncoming,
                        lastSentReply = replyToSend,
                        repliedAtMs = sentAtMs
                    )

                    historyDeque.addLast(AiChatMessage("user", normalizedIncoming.take(300)))
                    historyDeque.addLast(AiChatMessage("model", replyToSend.take(400)))
                    while (historyDeque.size > MAX_HISTORY_MESSAGES) {
                        historyDeque.removeFirst()
                    }

                    prefs.appendAutoReplyLog(
                        SairaAutoReplyLog(
                            id = "log_${sentAtMs}_${Random.nextInt(100, 999)}",
                            contactName = match.normalizedSender,
                            platformLabel = match.platform.label,
                            incomingPreview = normalizedIncoming.take(120),
                            sentReply = replyToSend.take(280),
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

    /**
     * Pictures of the new messages: MessagingStyle data uris (WhatsApp/Messenger photo messages)
     * or a BigPicture. Unreadable ones are skipped — then Saira says she couldn't open it.
     */
    private suspend fun loadNotificationPhotos(payload: IncomingPayload, extras: Bundle): List<com.example.data.ai.AiImage> {
        val out = mutableListOf<com.example.data.ai.AiImage>()
        for (uri in payload.imageUris.takeLast(MAX_PHOTOS)) {
            com.example.util.AiImagePrep.fromUri(applicationContext, uri)?.let { out += it }
        }
        if (out.isEmpty()) {
            @Suppress("DEPRECATION")
            val big = runCatching { extras.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_PICTURE) }.getOrNull()
            big?.let { com.example.util.AiImagePrep.fromBitmap(it) }?.let { out += it }
        }
        if (payload.imageUris.isNotEmpty()) {
            recordStatus(applicationContext, "ছবি ${payload.imageUris.size}টা · Saira দেখতে পেরেছে ${out.size}টা")
        }
        return out
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
                // Photo messages carry the picture as a data uri (+ "image/…" mime type).
                val dataMime = bundle.getString("type").orEmpty()
                @Suppress("DEPRECATION")
                val dataUri = if (dataMime.startsWith("image/")) bundle.getParcelable<android.net.Uri>("uri") else null
                val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    .ifEmpty { if (dataUri != null) "📷 Photo" else "" }
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
                ParsedMessageEntry(text = msgText, timeMs = timeMs, isSelf = isSelf, imageUri = dataUri)
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
                        messageTimeMs = latestTimeMs,
                        imageUris = chronological.mapNotNull { it.imageUri }.takeLast(MAX_PHOTOS)
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
        if (connected === this) connected = null
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
        val messageTimeMs: Long,
        /** Photos attached to the new incoming messages (MessagingStyle data uris). */
        val imageUris: List<android.net.Uri> = emptyList()
    )

    private data class ParsedMessageEntry(
        val text: String,
        val timeMs: Long,
        val isSelf: Boolean,
        val imageUri: android.net.Uri? = null
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
        private const val MAX_PHOTOS = 3

        @Volatile
        private var connected: SairaNotificationListenerService? = null

        private val ANSWER_WORDS = listOf("answer", "accept", "pick up", "উত্তর", "গ্রহণ", "রিসিভ", "ধরুন", "জবাব", "join")
        private val DECLINE_WORDS = listOf(
            "decline", "reject", "hang up", "end call", "end", "dismiss", "কেটে", "প্রত্যাখ্যান", "বাতিল", "শেষ", "কাটুন"
        )

        /**
         * Presses Answer / Decline on whatever call is ringing (phone dialer, WhatsApp,
         * Messenger, Telegram, imo …) through its notification. Returns the app's name, or
         * null when no call notification with such a button was found.
         */
        fun fireCallAction(context: Context, answer: Boolean): String? {
            val svc = connected ?: return null
            val words = if (answer) ANSWER_WORDS else DECLINE_WORDS
            val active = runCatching { svc.activeNotifications }.getOrNull() ?: return null
            val calls = active.filter { sbn ->
                val n = sbn.notification
                n.category == Notification.CATEGORY_CALL ||
                    n.extras?.containsKey("android.callType") == true ||
                    n.actions?.any { a -> ANSWER_WORDS.any { w -> a.title?.toString()?.lowercase()?.contains(w) == true } } == true
            }.sortedByDescending { it.postTime }
            for (sbn in calls) {
                val action = sbn.notification.actions?.firstOrNull { a ->
                    val t = a.title?.toString()?.lowercase().orEmpty()
                    words.any { t.contains(it) }
                } ?: continue
                if (SairaPhoneActions.sendAction(context, action)) {
                    return runCatching {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName)
                }
            }
            return null
        }
        private const val WAKE_LOCK_TAG = "Chatbox:SairaAutoReplyWakeLock"
        private const val PENDING_WAKE_LOCK_TIMEOUT_MS = 75_000L
        private const val PROCESS_WAKE_LOCK_TIMEOUT_MS = 60_000L
        private const val RADIO_WAKE_RETRY_MS = 1_200L
        private const val INITIAL_ABSENCE_WAIT_MS = 10_000L
        private const val CONTINUATION_DEBOUNCE_MS = 1_500L
        private const val ACTIVE_CONVERSATION_WINDOW_MS = 15 * 60_000L
        private const val MIN_REPLY_GAP_MS = 1_000L
        private const val AI_REPLY_TIMEOUT_MS = 7_000L
        private const val REPOST_GRACE_MS = 5_000L
        private const val PROCESS_TIMEOUT_MS = 40_000L
        private const val STATUS_PREFS = "saira_autoreply_status"
        private const val STATUS_KEY = "last"

        /** Last auto-reply event, shown on the Auto-Reply card for quick diagnosis. */
        fun recordStatus(context: Context, text: String) {
            runCatching {
                val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
                context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(STATUS_KEY, "$time — $text").apply()
            }
        }

        fun lastStatus(context: Context): String = runCatching {
            context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).getString(STATUS_KEY, null)
        }.getOrNull().orEmpty()
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

        /** OEMs (Xiaomi, Oppo, Vivo, Realme, Huawei…) kill cleared apps unless "Autostart" is allowed. */
        fun hasOemAutostartScreen(): Boolean = oemAutostartComponents().isNotEmpty()

        fun openOemAutostartSettings(context: Context) {
            for (component in oemAutostartComponents()) {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
            openAppDetailsSettings(context)
        }

        private fun oemAutostartComponents(): List<ComponentName> {
            val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any { it in brand } -> listOf(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                )
                listOf("oppo", "realme", "oneplus").any { it in brand } -> listOf(
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                )
                "vivo" in brand || "iqoo" in brand -> listOf(
                    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                )
                "huawei" in brand || "honor" in brand -> listOf(
                    // Newer Honor (MagicOS) phones: Battery -> App launch
                    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                )
                "infinix" in brand || "tecno" in brand || "itel" in brand -> listOf(
                    ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")
                )
                else -> emptyList()
            }
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
