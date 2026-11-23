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

        val matchedRule = rules.firstOrNull { rule ->
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
        val lowerIncoming = incomingText.trim().lowercase()
        val base = if (isContinuation) {
            when {
                lowerIncoming.contains("?") ||
                    lowerIncoming.contains("kemon") ||
                    lowerIncoming.contains("কেমন") ||
                    lowerIncoming.contains("kokhon") ||
                    lowerIncoming.contains("কখন") ->
                    "হ্যাঁ $senderName, আপনার প্রশ্নটি দেখেছি—$guidance"
                else ->
                    "$senderName, আপনার মেসেজটি পেয়েছি—$guidance"
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
        return incoming == target ||
            incoming.startsWith("$target:") ||
            incoming.endsWith(": $target") ||
            incoming.startsWith("$target (") ||
            incoming.startsWith("$target ·")
    }

    private fun stripBidiMarks(raw: String?): String =
        raw.orEmpty().replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]"), "")
}
