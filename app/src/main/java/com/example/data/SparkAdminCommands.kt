package com.example.data

import com.example.data.model.ChatboxConstants

sealed interface SparkCommandProposal {
    data class SetWelcome(val value: String) : SparkCommandProposal
    data class SetTheme(val value: String) : SparkCommandProposal
    data class SetMenu(val value: String) : SparkCommandProposal
    data class SetAppTitle(val value: String) : SparkCommandProposal
    data class SetAnnouncement(val value: String) : SparkCommandProposal
    data class RemoveMenu(val idOrLabel: String) : SparkCommandProposal
    data class SetAppUpdate(
        val active: Boolean,
        val version: String,
        val note: String,
        val downloadUrl: String
    ) : SparkCommandProposal
    data object Unsupported : SparkCommandProposal
}

object SparkAdminCommands {
    private val themeLavenderRegex = Regex(
        """^(?:(?:আমার\s+)?(?:হোম(?:\s*পেজ(?:টা|টি)?)?|অ্যাপ(?:ের)?)\s*(?:ল্যাভেন্ডার|lavender)\s*(?:থিম|কালার|রঙ)?\s*(?:করো|করুন|করে\s*দাও)?|(?:থিম|theme)\s*(?::|\s+)?\s*(?:ল্যাভেন্ডার|lavender)(?:\s+(?:করো|করুন|koro))?|(?:set|make)\s+(?:the\s+)?(?:home(?:\s+page)?|theme)\s+(?:to\s+)?lavender|home\s+lavender\s+koro)[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val themeDefaultRegex = Regex(
        """^(?:(?:theme|থিম)\s*(?::|\s+)?\s*(?:default|ডিফল্ট|আগের)(?:\s+(?:করো|করুন|koro))?|(?:আমার\s+)?(?:হোম(?:\s*পেজ(?:টা|টি)?)?|থিম)\s*(?:আগের|ডিফল্ট|default)\s*(?:রঙ|কালার|থিম)?\s*(?:করো|করুন|করে\s*দাও)?|home\s+default\s+koro|(?:set|reset)\s+(?:the\s+)?(?:home(?:\s+page)?|theme)\s+(?:to\s+)?default)[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val menuTopRegex = Regex(
        """^(?:(?:menu|মেনু(?:বার|টি|টা)?)\s*(?::|\s+)?\s*(?:top|উপরে(?:\s*নিয়ে|\s*নিয়ে)?)(?:\s+(?:আনো|আসো|নাও|রাখো|করো|দাও|nao|koro))?|(?:set|move)\s+(?:the\s+)?menu\s+(?:to\s+)?top|menu\s+upore\s+(?:nao|koro))[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val menuBottomRegex = Regex(
        """^(?:(?:menu|মেনু(?:বার|টি|টা)?)\s*(?::|\s+)?\s*(?:bottom|নিচে(?:\s*নিয়ে|\s*নিয়ে)?)(?:\s+(?:আনো|আসো|নাও|রাখো|করো|দাও|nao|koro))?|(?:set|move)\s+(?:the\s+)?menu\s+(?:to\s+)?bottom|menu\s+niche\s+(?:nao|koro))[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val updateCommandRegex = Regex(
        """^(?:update|app\s*update)\s*:\s*([\s\S]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val directWelcomeRegex = Regex(
        """^(?:welcome|স্বাগতম)\s*:\s*([\s\S]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val directTitleRegex = Regex(
        """^(?:title|app\s*title|app\s*name)\s*:\s*([\s\S]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val directAnnouncementRegex = Regex(
        """^(?:notice|announcement|ঘোষণা|নোটিশ)\s*:\s*([\s\S]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val removeMenuRegex = Regex(
        """^(?:(?:remove|delete)\s+menu\s+([a-zA-Z0-9_\-\s]{2,24})|([^\s]{2,20})\s+মেনু\s*(?:ডিলিট|রিমুভ|বাদ)\s*(?:করো|করুন|দাও)?)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): SparkCommandProposal {
        val text = input.trim().removePrefix("/")
        if (text.isEmpty()) return SparkCommandProposal.Unsupported
        if (themeLavenderRegex.matches(text)) return SparkCommandProposal.SetTheme("lavender")
        if (themeDefaultRegex.matches(text)) return SparkCommandProposal.SetTheme("default")
        if (menuTopRegex.matches(text)) return SparkCommandProposal.SetMenu("top")
        if (menuBottomRegex.matches(text)) return SparkCommandProposal.SetMenu("bottom")

        updateCommandRegex.matchEntire(text)?.let { match ->
            val raw = match.groupValues[1].trim()
            val lower = raw.lowercase()
            if (lower == "off" || lower == "clear" || lower == "none" || lower == "hide") {
                return SparkCommandProposal.SetAppUpdate(
                    active = false,
                    version = "",
                    note = "",
                    downloadUrl = ""
                )
            }
            val parts = raw.split("|").map { it.trim() }
            val version = parts.getOrElse(0) { "" }.take(ChatboxConstants.MAX_UPDATE_VERSION_LENGTH)
            val note = parts.getOrElse(1) { "" }.take(ChatboxConstants.MAX_UPDATE_NOTE_LENGTH)
            val downloadUrl = parts.getOrElse(2) { "" }.take(ChatboxConstants.MAX_UPDATE_URL_LENGTH)
            if (version.isEmpty() || (downloadUrl.isNotEmpty() && !downloadUrl.startsWith("https://"))) {
                return SparkCommandProposal.Unsupported
            }
            return SparkCommandProposal.SetAppUpdate(
                active = true,
                version = version,
                note = note,
                downloadUrl = downloadUrl
            )
        }

        directWelcomeRegex.matchEntire(text)?.let { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotEmpty() && value.length <= ChatboxConstants.MAX_WELCOME_LENGTH) {
                return SparkCommandProposal.SetWelcome(value)
            }
        }

        directTitleRegex.matchEntire(text)?.let { match ->
            val value = match.groupValues[1].trim().take(40)
            if (value.isNotEmpty()) return SparkCommandProposal.SetAppTitle(value)
        }

        directAnnouncementRegex.matchEntire(text)?.let { match ->
            val value = match.groupValues[1].trim().take(240)
            return SparkCommandProposal.SetAnnouncement(
                if (value.equals("off", true) || value.equals("clear", true)) "" else value
            )
        }

        removeMenuRegex.matchEntire(text)?.let { match ->
            val target = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
            if (target.isNotEmpty()) return SparkCommandProposal.RemoveMenu(target)
        }

        return SparkCommandProposal.Unsupported
    }

    fun isValidSparkTarget(input: String): Boolean =
        ChatboxConstants.VALID_GMAIL_REGEX.matches(input.trim().lowercase())
}
