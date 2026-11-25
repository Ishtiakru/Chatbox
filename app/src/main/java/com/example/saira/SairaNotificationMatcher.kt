package com.example.saira

import java.util.Calendar

data class NotificationMatchResult(
    val rule: SairaNotificationRule,
    val platform: SairaPlatform,
    val normalizedSender: String,
    val shouldAttachLocation: Boolean
)

object SairaNotificationMatcher {

    private val WHATSAPP_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private val MESSENGER_PACKAGES = setOf(
        "com.facebook.orca",
        "com.facebook.mlite"
    )

    fun platformForPackage(packageName: String?): SairaPlatform? {
        val pkg = packageName?.trim()?.lowercase().orEmpty()
        return when {
            pkg in WHATSAPP_PACKAGES -> SairaPlatform.WHATSAPP
            pkg in MESSENGER_PACKAGES -> SairaPlatform.MESSENGER
            else -> null
        }
    }

    /**
     * Evaluates whether Saira is permitted to auto-reply to an external notification.
     * Enforces:
     * 1. Master toggle (`notifAutoReplyEnabled == true`).
     * 2. Supported package (WhatsApp or Messenger only).
     * 3. Non-group-summary, non-empty sender title and message body.
     * 4. An explicitly enabled [SairaNotificationRule] with an explicit contact name that
     *    matches the notification sender on this platform (a designated message is required).
     * 5. Location sharing only when master `liveLocationEnabled == true`, OS permission is
     *    granted, the matched contact rule has `allowLocationShare == true`, and incoming
     *    text asks for location.
     */
    fun match(
        notifAutoReplyEnabled: Boolean,
        liveLocationEnabled: Boolean,
        locationPermissionGranted: Boolean,
        packageName: String?,
        senderTitle: String?,
        incomingText: String?,
        isGroupSummary: Boolean,
        rules: List<SairaNotificationRule>
    ): NotificationMatchResult? {
        if (!notifAutoReplyEnabled || isGroupSummary) return null

        val platform = platformForPackage(packageName) ?: return null
        val cleanSender = cleanContactTitle(senderTitle)
        val cleanText = incomingText?.trim().orEmpty()
        if (cleanSender.isEmpty() || cleanText.isEmpty()) return null

        val matchedRule = rules
            .sortedBy { if (isEveryoneRule(it.contactName)) 1 else 0 }
            .firstOrNull { rule ->
                rule.enabled &&
                    rule.contactName.isNotBlank() &&
                    rule.taughtReplyGuidance.isNotBlank() &&
                    (rule.platform == SairaPlatform.ANY || rule.platform == platform) &&
                    namesMatch(rule.contactName, cleanSender)
            } ?: return null

        val wantsLocation = matchedRule.allowLocationShare &&
            liveLocationEnabled &&
            locationPermissionGranted &&
            SairaTimeGreeting.isLocationRequest(cleanText)

        return NotificationMatchResult(
            rule = matchedRule,
            platform = platform,
            normalizedSender = cleanSender,
            shouldAttachLocation = wantsLocation
        )
    }

    /**
     * Matches an enabled taught rule by contact name (and optional platform filter) for
     * in-app Chatbox DMs and the Saira auto-reply test runner.
     */
    fun findInAppRule(
        contactName: String,
        platform: SairaPlatform = SairaPlatform.ANY,
        rules: List<SairaNotificationRule>
    ): SairaNotificationRule? {
        val cleanSender = cleanContactTitle(contactName)
        if (cleanSender.isEmpty()) return null
        return rules.sortedBy { if (isEveryoneRule(it.contactName)) 1 else 0 }.firstOrNull { rule ->
            rule.enabled &&
                rule.contactName.isNotBlank() &&
                rule.taughtReplyGuidance.isNotBlank() &&
                (platform == SairaPlatform.ANY || rule.platform == SairaPlatform.ANY || rule.platform == platform) &&
                namesMatch(rule.contactName, cleanSender)
        }
    }

    fun buildFirstBoxReply(
        senderName: String,
        rule: SairaNotificationRule,
        locationSnapshot: SharedLocationSnapshot?,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String = buildDeterministicReply(
        senderName = senderName,
        rule = rule,
        locationSnapshot = locationSnapshot,
        hourOfDay = hourOfDay,
        isContinuation = false,
        incomingText = ""
    )

    fun buildDeterministicReply(
        senderName: String,
        rule: SairaNotificationRule,
        locationSnapshot: SharedLocationSnapshot?,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        isContinuation: Boolean = false,
        incomingText: String = ""
    ): String {
        val info = SairaTimeGreeting.infoForHour(hourOfDay)
        val guidance = rule.taughtReplyGuidance.trim()
        val rawIncoming = incomingText.trim()
        val lowerIncoming = rawIncoming.lowercase()
        val base = if (isContinuation) {
            when {
                locationSnapshot != null ->
                    "হ্যাঁ $senderName, এই মুহূর্তের অনুমোদিত লোকেশন নিচে দিলাম:"
                lowerIncoming.contains("kemon") ||
                    lowerIncoming.contains("কেমন") ||
                    lowerIncoming.contains("how are") ->
                    "ভালো আছি $senderName! আমি সায়রা বলছি—আপনি কেমন আছেন? কিছু জানাতে বা জিজ্ঞাসা করতে চাইলে লিখুন।"
                lowerIncoming.contains("ki kor") ||
                    lowerIncoming.contains("কী কর") ||
                    lowerIncoming.contains("কি কর") ||
                    lowerIncoming.contains("what are you") ->
                    "আমি সায়রা, এখন মেসেজগুলো দেখছি। আপনার জরুরি কোনো কথা বা প্রশ্ন থাকলে আমাকে বলুন।"
                lowerIncoming.contains("kokhon") ||
                    lowerIncoming.contains("কখন") ||
                    lowerIncoming.contains("when") ||
                    lowerIncoming.contains("kothay") ||
                    lowerIncoming.contains("কোথায়") ||
                    lowerIncoming.contains("কোথায়") ||
                    lowerIncoming.contains("koi") ||
                    lowerIncoming.contains("কই") ||
                    lowerIncoming.contains("where") ->
                    "ঠিক কখন বা কোথায় আছেন তা এই মুহূর্তে নিশ্চিত জানা নেই $senderName—ফ্রি হলেই আপনার সাথে যোগাযোগ করা হবে। অন্য কিছু জানতে চাইলে বলুন।"
                lowerIncoming in setOf("ok", "okay", "accha", "আচ্ছা", "hm", "hmm", "thanks", "thank you", "ধন্যবাদ") ->
                    "ঠিক আছে $senderName, প্রয়োজনে আবার মেসেজ দেবেন। ভালো থাকবেন!"
                rawIncoming.contains("?") || rawIncoming.contains("কে") || rawIncoming.contains("কী") || rawIncoming.contains("কি") ->
                    "হ্যাঁ $senderName, আপনার \"${rawIncoming.take(60)}\" প্রশ্নটি পড়েছি—এই মুহূর্তে ইন্টারনেট বা AI সংযোগ ধীর থাকায় বিস্তারিত জানানো যাচ্ছে না, কিছুক্ষণ পর চেষ্টা করুন অথবা বার্তা রেখে যান।"
                else ->
                    "বুঝেছি $senderName, আপনার \"${rawIncoming.take(60)}\" বার্তাটি রেখে দিলাম—আরও কিছু বলার থাকলে নির্দ্বিধায় লিখুন।"
            }
        } else {
            "হ্যালো $senderName, ${info.bengaliGreeting}! $guidance"
        }
        return if (locationSnapshot != null) {
            "$base\n${locationSnapshot.toShareText()}"
        } else {
            base
        }
    }

    internal fun cleanContactTitle(rawTitle: String?): String {
        val trimmed = stripBidiMarks(rawTitle).trim()
        if (trimmed.isEmpty()) return ""
        // Strip unread counter suffixes like "Sajib (3 messages)", "Sajib (2 new messages)",
        // "Sajib (2)", "Sajib · 2 messages", or Bengali "Sajib (২টি মেসেজ)"
        val withoutCount = trimmed
            .replace(
                Regex(
                    """\s*\([\d\u09E6-\u09EF]+(?:\s*(?:new\s*)?(?:messages?|msgs?|unread|টি\s*(?:নতুন\s*)?(?:মেসেজ|বার্তা)))?\)$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    """\s*[·•-]\s*[\d\u09E6-\u09EF]+\s*(?:new\s*)?(?:messages?|msgs?|unread|টি\s*(?:নতুন\s*)?(?:মেসেজ|বার্তা))$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
        return withoutCount.trim()
    }

    internal fun namesMatch(ruleContact: String, notificationSender: String): Boolean {
        val target = stripBidiMarks(ruleContact).trim().lowercase()
        val incoming = cleanContactTitle(notificationSender).trim().lowercase()
        if (target.isEmpty() || incoming.isEmpty()) return false
        if (isEveryoneRule(target)) return true
        if (containsNameTokens(incoming, target)) return true
        return incoming == target ||
            incoming.startsWith("$target:") ||
            incoming.endsWith(": $target") ||
            incoming.startsWith("$target (") ||
            incoming.startsWith("$target ·")
    }

    private val EVERYONE_NAMES = setOf("*", "all", "everyone", "anyone", "সবাই", "সবার জন্য", "সকলে", "sobai")

    const val DEFAULT_EVERYONE_TEXT = "আমি এখন একটু ব্যস্ত আছি, একটু পরেই উত্তর দেব। জরুরি কিছু থাকলে এখানে লিখে রাখুন।"

    /** Used when the owner turned auto-reply on but set no contacts. */
    fun defaultEveryoneRule(): SairaNotificationRule = SairaNotificationRule(
        id = "default_everyone",
        contactName = "সবাই",
        platform = SairaPlatform.ANY,
        taughtReplyGuidance = DEFAULT_EVERYONE_TEXT,
        enabled = true,
        allowLocationShare = true,
        useAiReply = true
    )

    /** A rule whose contact is "সবাই" / "everyone" / "*" answers every 1:1 sender. */
    fun isEveryoneRule(ruleContact: String): Boolean =
        stripBidiMarks(ruleContact).trim().lowercase() in EVERYONE_NAMES

    /** "Sajib" matches "Sajib Ahmed" / "Md Sajib" (whole words, in order). */
    private fun containsNameTokens(incoming: String, target: String): Boolean {
        val t = target.split(Regex("\\s+")).filter { it.isNotBlank() }
        val i = incoming.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (t.isEmpty() || i.size < t.size) return false
        if (t.size == 1 && t[0].length < 3) return false
        for (start in 0..(i.size - t.size)) {
            if ((t.indices).all { i[start + it] == t[it] }) return true
        }
        return false
    }

    private fun stripBidiMarks(raw: String?): String =
        raw.orEmpty().replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "")
}
