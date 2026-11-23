package com.example.data.ai

import com.example.data.SparkAdminCommands
import com.example.data.SparkCommandProposal
import com.example.data.model.ChatboxConstants
import com.example.data.model.CoreAppTab
import com.example.data.model.SparkAppearance
import com.example.data.model.SparkDynamicConfig
import com.example.data.model.UserProfile

sealed interface SairaCommandAction {
    data class Reply(
        val text: String,
        val openHistory: Boolean = false,
        val openProfile: Boolean = false,
        val openBackupDialog: Boolean = false,
        val switchSubTab: String? = null,
        val navigateToCoreTab: CoreAppTab? = null,
        val toggleOneMinAutoReply: Boolean? = null
    ) : SairaCommandAction

    data object StartNewThread : SairaCommandAction

    data class ExecuteAdminProposal(val proposal: SparkCommandProposal) : SairaCommandAction

    data class ExecuteAdminNaturalInstruction(val instruction: String) : SairaCommandAction

    data object NotACommand : SairaCommandAction
}

object SairaCommandProcessor {
    private val helpRegex = Regex(
        """^(?:/?(?:help|commands?|cmds?|কমান্ড|কমান্ড\s*লিস্ট)|saira\s+commands?)[।.?!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val newThreadRegex = Regex(
        """^/(?:new|clear|reset)$|^(?:new\s+chat|clear\s+chat|নতুন\s+চ্যাট)$""",
        RegexOption.IGNORE_CASE
    )
    private val historyRegex = Regex(
        """^/history$|^(?:saved\s+chats?|chat\s+history|সেভ\s*চ্যাট)$""",
        RegexOption.IGNORE_CASE
    )
    private val myIdRegex = Regex(
        """^(?:/id|my\s*id|amar\s*id|আমার\s*(?:আইডি|কোড|id)|show\s+my\s+id)[।.?!]?$""",
        RegexOption.IGNORE_CASE
    )
    private val profileRegex = Regex(
        """^(?:/profile|open\s+profile|my\s+profile|প্রোফাইল\s*খোলো)$""",
        RegexOption.IGNORE_CASE
    )
    private val autoReplyOnRegex = Regex(
        """^/?auto[\s\-_]?reply\s+(?:on|enable|true|চালু)$|^অটো\s*রিপ্লাই\s*চালু(?:\s*করো)?$""",
        RegexOption.IGNORE_CASE
    )
    private val autoReplyOffRegex = Regex(
        """^/?auto[\s\-_]?reply\s+(?:off|disable|false|বন্ধ)$|^অটো\s*রিপ্লাই\s*বন্ধ(?:\s*করো)?$""",
        RegexOption.IGNORE_CASE
    )
    private val rulesTabRegex = Regex(
        """^/(?:rules|autoreply)$|^(?:open\s+rules|auto[\s\-]?reply\s+settings)$""",
        RegexOption.IGNORE_CASE
    )
    private val backupRegex = Regex(
        """^/(?:backup|apikey|key)$|^(?:backup\s+ai|set\s+backup\s+key)$""",
        RegexOption.IGNORE_CASE
    )
    private val statusRegex = Regex(
        """^/(?:status|info|theme\?|config)$""",
        RegexOption.IGNORE_CASE
    )
    private val navTabRegex = Regex(
        """^/(demo|live|calls|admin)$""",
        RegexOption.IGNORE_CASE
    )
    private val explicitAdminPrefixRegex = Regex(
        """^/(?:admin|cmd|app|build)\s+([\s\S]+)$""",
        RegexOption.IGNORE_CASE
    )
    private val naturalAppBuilderHintRegex = Regex(
        """(?:মেনু\s*বার|কাস্টম\s*মেনু|নতুন\s*মেনু|মেনু\s*(?:যোগ|ডিলিট|রিমুভ|উপরে|নিচে)|হোম\s*পেজ|থিম\s*(?:পরিবর্তন|বদলাও|ল্যাভেন্ডার|ডিফল্ট)|অ্যাপ(?:ের)?\s*নাম|স্বাগতম\s*বার্তা|আপডেট\s*বাটন|nemu\s*bar|menu\s*bar\s*add|add\s+(?:a\s+)?(?:new\s+)?menu|remove\s+menu|delete\s+menu|change\s+app\s+title)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        rawInput: String,
        isAdmin: Boolean,
        ownProfile: UserProfile?,
        appearance: SparkAppearance,
        dynamicConfig: SparkDynamicConfig
    ): SairaCommandAction {
        val text = rawInput.trim()
        if (text.isEmpty()) return SairaCommandAction.NotACommand

        if (helpRegex.matches(text)) {
            return SairaCommandAction.Reply(buildHelpMessage(isAdmin))
        }
        if (newThreadRegex.matches(text)) {
            return SairaCommandAction.StartNewThread
        }
        if (historyRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "আপনার সেভ করা Saira চ্যাট থ্রেডগুলো খোলা হচ্ছে।",
                openHistory = true
            )
        }
        if (myIdRegex.matches(text)) {
            val code = ownProfile?.shortId?.takeIf { it.isNotBlank() } ?: "Generating…"
            val name = ownProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Chatbox User"
            return SairaCommandAction.Reply(
                text = "আপনার প্রোফাইল তথ্য:\n• নাম: $name\n• ৬-ডিজিট Chatbox ID: $code\n\nবন্ধুরা Live ট্যাবে '+' চেপে এই ৬-ডিজিট আইডি দিয়ে আপনাকে সরাসরি অ্যাড করতে পারবে।"
            )
        }
        if (profileRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "আপনার প্রোফাইল উইন্ডো খোলা হচ্ছে।",
                openProfile = true
            )
        }
        if (autoReplyOnRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "Chatbox 1-Min Absence Auto-Reply চালু (ON) করা হয়েছে।",
                toggleOneMinAutoReply = true
            )
        }
        if (autoReplyOffRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "Chatbox 1-Min Absence Auto-Reply বন্ধ (OFF) করা হয়েছে।",
                toggleOneMinAutoReply = false
            )
        }
        if (rulesTabRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "Auto-Reply ও Backup AI সেটিংস খোলা হচ্ছে।",
                switchSubTab = "autoReply"
            )
        }
        if (backupRegex.matches(text)) {
            return SairaCommandAction.Reply(
                text = "Backup AI Provider (Groq / OpenRouter / Custom) সেটিংস খোলা হচ্ছে।",
                openBackupDialog = true
            )
        }
        if (statusRegex.matches(text)) {
            val menuSummary = if (dynamicConfig.customMenus.isEmpty()) {
                "কোনো কাস্টম মেনু নেই"
            } else {
                dynamicConfig.customMenus.joinToString(", ") { it.label }
            }
            return SairaCommandAction.Reply(
                text = "বর্তমান অ্যাপ কনফিগারেশন:\n• অ্যাপ নাম: ${dynamicConfig.appTitle}\n• ভার্সন: v${ChatboxConstants.APP_VERSION}\n• থিম: ${appearance.theme}\n• মেনু অবস্থান: ${appearance.menu}\n• কাস্টম মেনু: $menuSummary"
            )
        }
        navTabRegex.matchEntire(text)?.let { match ->
            val targetName = match.groupValues[1].lowercase()
            val tab = when (targetName) {
                "demo" -> CoreAppTab.DEMO
                "live" -> CoreAppTab.LIVE
                "calls" -> CoreAppTab.CALLS
                "admin" -> CoreAppTab.ADMIN
                else -> CoreAppTab.LIVE
            }
            if (tab == CoreAppTab.ADMIN && !isAdmin) {
                return SairaCommandAction.Reply(
                    "Admin Panel শুধুমাত্র অনুমোদিত অ্যাডমিন জিমেইল অ্যাকাউন্টের জন্য সংরক্ষিত।"
                )
            }
            return SairaCommandAction.Reply(
                text = "${targetName.replaceFirstChar { it.uppercase() }} ট্যাবে নেওয়া হচ্ছে।",
                navigateToCoreTab = tab
            )
        }

        // Check direct Spark Admin commands (theme: lavender, menu: top, welcome:, update:, title:, notice:, remove menu, Bengali commands)
        val proposal = SparkAdminCommands.parse(text)
        if (proposal !is SparkCommandProposal.Unsupported) {
            return if (isAdmin) {
                SairaCommandAction.ExecuteAdminProposal(proposal)
            } else {
                SairaCommandAction.Reply(
                    "এই অ্যাপ-কন্ট্রোল কমান্ডটি শুধুমাত্র অনুমোদিত অ্যাডমিন জিমেইল দিয়ে লগইন করলে কাজ করবে।"
                )
            }
        }

        // Check explicit /admin or /cmd prefix or natural-language app builder commands
        explicitAdminPrefixRegex.matchEntire(text)?.let { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) {
                return if (isAdmin) {
                    val innerProposal = SparkAdminCommands.parse(inner)
                    if (innerProposal !is SparkCommandProposal.Unsupported) {
                        SairaCommandAction.ExecuteAdminProposal(innerProposal)
                    } else {
                        SairaCommandAction.ExecuteAdminNaturalInstruction(inner)
                    }
                } else {
                    SairaCommandAction.Reply(
                        "অ্যাডমিন কমান্ড চালাতে অনুমোদিত অ্যাডমিন জিমেইল দিয়ে সাইন ইন করুন।"
                    )
                }
            }
        }

        if (isAdmin && naturalAppBuilderHintRegex.containsMatchIn(text)) {
            return SairaCommandAction.ExecuteAdminNaturalInstruction(text)
        }

        return SairaCommandAction.NotACommand
    }

    fun buildHelpMessage(isAdmin: Boolean): String = buildString {
        appendLine("Saira AI চালু থাকা কমান্ডসমূহ:")
        appendLine("• /id — আপনার ৬-ডিজিট Chatbox আইডি দেখুন")
        appendLine("• /profile — প্রোফাইল ও ফোন নম্বর এডিট করুন")
        appendLine("• /new বা /clear — নতুন চ্যাট থ্রেড শুরু করুন")
        appendLine("• /history — সেভ করা চ্যাট হিস্ট্রি খুলুন")
        appendLine("• /autoreply on বা /autoreply off — অটো-রিপ্লাই চালু/বন্ধ")
        appendLine("• /rules — Auto-Reply ও Backup AI সেটিংস খুলুন")
        appendLine("• /backup — Backup AI API কী যোগ করুন")
        appendLine("• /status — বর্তমান থিম, মেনু ও ভার্সন দেখুন")
        if (isAdmin) {
            appendLine()
            appendLine("অ্যাডমিন লাইভ কমান্ড (সরাসরি Saira-তে লিখুন):")
            appendLine("• আমার হোম পেজটা ল্যাভেন্ডার কালার করো / theme: default")
            appendLine("• মেনুবার উপরে নিয়ে আসো / menu: bottom")
            appendLine("• welcome: নতুন স্বাগতম বার্তা")
            appendLine("• title: Chatbox Pro")
            appendLine("• notice: নতুন ঘোষণা")
            appendLine("• update: 1.0.1 | নতুন আপডেট | https://...")
            appendLine("• update: off")
            append("• একটা নতুন মেনু বার যোগ করো নাম Notice ...")
        }
    }.trim()
}
