package com.example.data.repository

import com.example.data.model.SparkCommandProposal

object SparkAdminCommands {
    private val themeLavenderRegex = Regex(
        """^(?:(?:আমার\s+)?হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:ল্যাভেন্ডার|lavender)\s*(?:কালার|রঙ)?\s*(?:করো|করুন|করে\s*দাও)|(?:set|make)\s+(?:the\s+)?home(?:\s+page)?\s+(?:to\s+)?lavender|theme\s*:\s*lavender)[।.!]?$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val themeDefaultRegex = Regex(
        """^(?:theme\s*:\s*default|হোম(?:\s*পেজ(?:টা|টি)?)?\s*(?:আগের|ডিফল্ট)\s*(?:রঙ|কালার)?\s*(?:করো|করুন|করে\s*দাও))[।.!]?$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val menuTopRegex = Regex(
        """^(?:menu\s*:\s*top|মেনু(?:বার|টি|টা)?\s*(?:উপরে|উপরে\s*নিয়ে|উপরে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val menuBottomRegex = Regex(
        """^(?:menu\s*:\s*bottom|মেনু(?:বার|টি|টা)?\s*(?:নিচে|নিচে\s*নিয়ে|নিচে\s*নিয়ে)\s*(?:আনো|রাখো|করো|দাও))[।.!]?$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val directWelcomeRegex = Regex(
        """^(?:welcome|স্বাগতম)\s*:\s*([\s\S]+)$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private const val QUOTE_GROUP =
        """(?:"([^"\n\r]*)"|“([^”\n\r]*)”|'([^'\n\r]*)'|‘([^’\n\r]*)’)"""
    private val englishWelcomeRegex = Regex(
        """^(?:please\s+)?(?:set|change|update)\s+(?:the\s+)?welcome(?:\s+message)?\s+to\s+$QUOTE_GROUP\s*[.!]?\s*$""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val bengaliWelcomeRegex = Regex(
        """^(?:স্বাগত|স্বাগতম)(?:\s+বার্তা(?:টি)?)?\s+$QUOTE_GROUP\s+(?:করো|করুন|করে\s+দাও|করে\s+দিন)\s*[।.!]?\s*$"""
    )
    private val validGmailRegex = Regex("""^[a-z0-9][a-z0-9._%+-]{0,63}@gmail\.com$""")

    fun parseSparkCommand(input: String): SparkCommandProposal {
        val text = input.trim()
        if (text.isEmpty()) return SparkCommandProposal.Unsupported
        if (themeLavenderRegex.matches(text)) return SparkCommandProposal.SetTheme("lavender")
        if (themeDefaultRegex.matches(text)) return SparkCommandProposal.SetTheme("default")
        if (menuTopRegex.matches(text)) return SparkCommandProposal.SetMenu("top")
        if (menuBottomRegex.matches(text)) return SparkCommandProposal.SetMenu("bottom")

        val directMatch = directWelcomeRegex.matchEntire(text)
        val rawValue = directMatch?.groupValues?.getOrNull(1)
            ?: (englishWelcomeRegex.matchEntire(text) ?: bengaliWelcomeRegex.matchEntire(text))
                ?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotEmpty() }

        val value = rawValue?.trim()
        if (value.isNullOrEmpty() || value.length > 160) {
            return SparkCommandProposal.Unsupported
        }
        return SparkCommandProposal.SetWelcome(value)
    }

    fun validSparkTarget(input: String): Boolean =
        validGmailRegex.matches(input.trim().lowercase())
}
