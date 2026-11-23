package com.example.data

import com.example.data.model.ChatboxConstants

sealed interface SparkCommandProposal {
    data class SetWelcome(val value: String) : SparkCommandProposal
    data class SetTheme(val value: String) : SparkCommandProposal
    data class SetMenu(val value: String) : SparkCommandProposal
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
        """^(?:(?:আমার\s+)?হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:ল্যাভেন্ডার|lavender)\s*(?:কালার|রঙ)?\s*(?:করো|করুন|করে\s*দাও)|(?:set|make)\s+(?:the\s+)?home(?:\s+page)?\s+(?:to\s+)?lavender|theme\s*:\s*lavender)[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val themeDefaultRegex = Regex(
        """^(?:theme\s*:\s*default|হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:আগের|ডিফল্ট)\s*(?:রঙ|কালার)?\s*(?:করো|করুন|করে\s*দাও))[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val menuTopRegex = Regex(
        """^(?:menu\s*:\s*top|মেনু(?:বার|টি|টা)?\s*(?:উপরে|উপরে\s*নিয়ে|উপরে\s*নিয়ে)\s*(?:আনো|আসো|নাও|রাখো|করো|দাও))[।.!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val menuBottomRegex = Regex(
        """^(?:menu\s*:\s*bottom|মেনু(?:বার|টি|টা)?\s*(?:নিচে|নিচে\s*নিয়ে|নিচে\s*নিয়ে)\s*(?:আনো|আসো|নাও|রাখো|করো|দাও))[।.!]?$""",
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

    fun parse(input: String): SparkCommandProposal {
        val text = input.trim()
        if (text.isEmpty()) return SparkCommandProposal.Unsupported
        if (themeLavenderRegex.matches(text)) return SparkCommandProposal.SetTheme("lavender")
        if (themeDefaultRegex.matches(text)) return SparkCommandProposal.SetTheme("default")
        if (menuTopRegex.matches(text)) return SparkCommandProposal.SetMenu("top")
        if (menuBottomRegex.matches(text)) return SparkCommandProposal.SetMenu("bottom")

        updateCommandRegex.matchEntire(text)?.let { match ->
            val raw = match.groupValues[1].trim()
            val lower = raw.lowercase()
            if (lower == "off" || lower == "clear" || lower == "none") {
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
        return SparkCommandProposal.Unsupported
    }

    fun isValidSparkTarget(input: String): Boolean =
        ChatboxConstants.VALID_GMAIL_REGEX.matches(input.trim().lowercase())
}
