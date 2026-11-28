package com.example.saira

/**
 * Pure (Android-free) understanding of "do something on my phone" requests that the
 * user types or says to Saira, e.g.
 *  - "01712345678 নম্বরে কল দাও" / "call 01712345678"
 *  - "whatsapp e karim ke call dao" / "করিমকে হোয়াটসঅ্যাপে ভিডিও কল দাও"
 *  - "আম্মুকে মেসেজ দাও যে আমি আসছি" / "message karim on telegram: I'm late"
 *  - "call ta dhoro" / "কেটে দাও" / "screen off koro" / "youtube kholo"
 *
 * Anything it is not confident about returns null so the normal AI chat answers.
 */
enum class PhoneAction { CALL, VIDEO_CALL, MESSAGE, ANSWER, DECLINE, OPEN_APP, SCREEN_ON, SCREEN_OFF }

enum class PhoneApp(val label: String, val packages: List<String>) {
    PHONE("Phone", emptyList()),
    SMS("SMS", emptyList()),
    WHATSAPP("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    MESSENGER("Messenger", listOf("com.facebook.orca", "com.facebook.mlite")),
    TELEGRAM("Telegram", listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
    IMO("imo", listOf("com.imo.android.imoim", "com.imo.android.imoimbeta")),
    VIBER("Viber", listOf("com.viber.voip")),
    OTHER("App", emptyList())
}

data class PhoneCommand(
    val action: PhoneAction,
    val app: PhoneApp = PhoneApp.PHONE,
    /** Phone number or contact name (or the app name for OPEN_APP). */
    val target: String = "",
    /** Message body for MESSAGE. */
    val text: String = ""
) {
    val targetIsNumber: Boolean
        get() = target.isNotBlank() && target.none { it.isLetter() } &&
            SairaPhoneCommandParser.digitsOf(target).length >= 3

    val needsTarget: Boolean
        get() = target.isBlank() && action in setOf(
            PhoneAction.CALL, PhoneAction.VIDEO_CALL, PhoneAction.MESSAGE, PhoneAction.OPEN_APP
        )

    val needsText: Boolean get() = action == PhoneAction.MESSAGE && text.isBlank()

    /** Calls and messages always need the user's "yes" first. */
    val needsConfirmation: Boolean
        get() = action in setOf(PhoneAction.CALL, PhoneAction.VIDEO_CALL, PhoneAction.MESSAGE)
}

/** Regex whose Bengali literals are normalised the same way user text is (য় / ড় / ঢ় forms). */
internal fun rx(pattern: String, vararg options: RegexOption): Regex =
    Regex(SairaPhoneCommandParser.normalizePattern(pattern), options.toSet())

object SairaPhoneCommandParser {

    private const val BN = "\\u0980-\\u09FF"
    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    /** Bengali-script word boundary (Java's \b does not understand Bengali letters). */
    private fun bnWord(w: String) = "(?<![$BN])$w"

    // ---- apps -------------------------------------------------------------------------
    private val APP_PATTERNS: List<Pair<PhoneApp, Regex>> = listOf(
        PhoneApp.WHATSAPP to rx(
            "what'?s ?app|whatsap|watsapp|wahtsapp|whtsapp|\\bwapp\\b|টস ?অ্যাপ|টস ?অ্যাপ|টস ?আপ|টস ?এপ|টসঅ্যাপ|হোয়াটস|ওয়াটস"
        ),
        PhoneApp.MESSENGER to rx("messenger|massenger|mesenger|মেসেঞ্জার|ম্যাসেঞ্জার|facebook|ফেসবুক|\\bfb\\b"),
        PhoneApp.TELEGRAM to rx("telegram|telegarm|টেলিগ্রাম"),
        PhoneApp.IMO to rx("\\bimo\\b|${bnWord("ইমো")}"),
        PhoneApp.VIBER to rx("viber|ভাইবার"),
        PhoneApp.SMS to rx("\\bsms\\b|এসএমএস|text message|টেক্সট মেসেজ")
    )

    // ---- actions ----------------------------------------------------------------------
    private val VIDEO = rx("video ?(call|kol|cal)|ভিডিও ?কল|ভিডিও ?কোল")
    private val CALL = rx(
        "\\bcall\\b|\\bcal\\b|\\bkol\\b|\\bkoll\\b|\\bcalll?\\b|${bnWord("কল")}|${bnWord("কোল")}|" +
            "\\b(phone|fon|fone) (dao|daw|dew|de|koro|kor|dio|diyo|den|din|lagao)\\b|" +
            "${bnWord("ফোন")} ?(দাও|দে|দেও|করো|কর|দিও|দিন|লাগাও|করুন)|\\bdial\\b|\\bring\\b"
    )
    private val MESSAGE_STRONG = rx(
        "message|messege|massage|masage|mesage|\\bmsg\\b|\\bsms\\b|\\btext\\b|\\btxt\\b|" +
            "মেসেজ|ম্যাসেজ|মেসেস|ম্যাসেস|বার্তা|এসএমএস|টেক্সট"
    )
    private val MESSAGE_WRITE = rx(
        "\\blikh(o|e|a|en|un|e dao|e pathao)\\b|${bnWord("লিখ")}|${bnWord("লেখ")}|\\bwrite\\b|\\bjanao\\b|জানাও|জানিয়ে দাও|\\btell\\b"
    )
    private val ANSWER = rx(
        "receive|recive|resive|recieve|riciv|রিসিভ|\\banswer\\b|pick ?up|\\bpick\\b|\\baccept\\b|" +
            "\\bdhoro\\b|\\bdhor\\b|\\bdhoro\\b|${bnWord("ধরো")}|${bnWord("ধরুন")}|${bnWord("ধর")}(?![$BN])|${bnWord("তোলো")}|\\btolo\\b"
    )
    private val DECLINE = rx(
        "\\bkete ?(dao|daw|dew|de|din|dio)\\b|\\bkate ?(dao|daw|dew)\\b|\\bkato\\b|কেটে|${bnWord("কাটো")}|" +
            "\\breject\\b|\\bdecline\\b|\\bcut\\b|hang ?up|end (the )?call|call (end|sesh|shesh|off|bondho|rakho)|" +
            "কল (শেষ|বন্ধ|রাখো)|রেখে দাও|\\brekhe dao\\b|ফোন রাখো|\\bphone rakho\\b"
    )
    /** Imperative "give / do / send" verbs — distinguish "call dao" (make) from "call dhoro" (answer). */
    private val GIVE = rx(
        "\\b(dao|daw|dew|de|dio|diyo|den|din|lagao|koro|kor|pathao|pathaw|send|make|place)\\b|" +
            "${bnWord("দাও")}|${bnWord("দে")}(?![$BN])|${bnWord("দেও")}|${bnWord("দিও")}|${bnWord("দিন")}|${bnWord("লাগাও")}|" +
            "${bnWord("করো")}|${bnWord("করুন")}|${bnWord("পাঠাও")}|${bnWord("পাঠান")}|${bnWord("কর")}(?![$BN])"
    )
    private val SCREEN_ON = rx(
        "screen ?(on|chalu|jalao|jalaw|kholo)|display ?(on|chalu)|স্ক্রিন ?(অন|চালু|জ্বালাও|খোলো)|ফোন ?(জাগাও|অন করো)|\\bwake ?up\\b"
    )
    private val SCREEN_OFF = rx(
        "screen ?(off|bondho|bonddho|nivao|nibhao|lock)|display ?(off|bondho)|lock (the )?(phone|screen)|phone ?lock|" +
            "স্ক্রিন ?(অফ|বন্ধ|নিভাও|লক)|ফোন ?(লক|বন্ধ করো)"
    )
    private val OPEN_APP = rx(
        "^(?:please |plz |saira |সায়রা )?(.{2,30}?)(?: app| অ্যাপ| এপ)?(?: ta| টা| ti| টি)?(?: e| te| এ| তে)? " +
            "(kholo|khulo|khol|khule dao|khule daw|open koro|open kor|open karo|open kore dao|open|chalu koro|chalu kor|on koro|on kor|" +
            "jao|jaw|dhuko|launch koro|খোলো|খুলো|খোল|খুলে দাও|ওপেন করো|ওপেন কর|ওপেন করে দাও|চালু করো|চালু কর|অন করো|অন কর|যাও|ঢোকো)$|" +
            "^(?:please |plz )?(?:open|launch|start|go to) (?:the )?(.{2,30}?)(?: app)?$"
    )
    private val OPEN_TAIL = rx("(?:[ ,]+(?:to|toh|na|please|plz|ektu|একটু|তো|না|প্লিজ|এখনই|ekhon|ekhoni))+$")
    private val GO_VERBS = setOf("jao", "jaw", "dhuko", "যাও", "ঢোকো")
    /** Apps "… e jao" may refer to (a go-verb alone is too common in normal speech). */
    private val KNOWN_APPS = rx(
        "^(youtube|youtub|facebook|fb|settings?|chrome|camera|gallery|maps?|google maps|play ?store|tiktok|instagram|insta|gmail|bkash|nagad|" +
            "rocket|upay|telegram|whatsapp|imo|viber|messenger|chatbox|x|twitter|snapchat|zoom|drive|calculator|clock|calendar|files?|music|" +
            "ইউটিউব|ফেসবুক|সেটিংস|সেটিং|ক্রোম|ক্যামেরা|গ্যালারি|গ্যালারী|ম্যাপ|ম্যাপস|প্লে স্টোর|টিকটক|ইনস্টাগ্রাম|জিমেইল|বিকাশ|নগদ|" +
            "চ্যাটবক্স|ক্যালকুলেটর|ঘড়ি|ক্যালেন্ডার|ফাইল|মিউজিক|হোয়াটসঅ্যাপ|টেলিগ্রাম|ইমো|ভাইবার|মেসেঞ্জার)$"
    )
    private val NOT_APPS = setOf("screen", "স্ক্রিন", "call", "কল", "phone call", "light", "ফোন কল")

    // Speech-to-text adds commas / dandas freely ("হ্যাঁ, দাও।") — [yesNoText] turns them into spaces first.
    private val YES = rx(
        "^(ha+|haa+|hya+|hyaa+|ji+|jee|hm+|hmm+|yes|yeah|yep|ok|okay|okey|sure|thik ache|thik ase|thikache|koro|kor|dao|daw|confirm|" +
            "kore dao|pathao|korun|din|হ্যাঁ|হ্যা|হা|হুম|জি|জ্বি|ঠিক আছে|আচ্ছা|করো|দাও|ওকে|অবশ্যই|করে দাও|পাঠাও|করুন|দিন|পাঠান)" +
            "( (koro|kor|dao|kore dao|pathao|korun|din|please|plz|saira|ekhoni|করো|দাও|করে দাও|পাঠাও|করুন|দিন|পাঠান|সায়রা|এখনই)){0,2}$"
    )
    private val NO = rx(
        "^(na+|no|nope|cancel|thak|baad dao|bad dao|stop|dorkar nai|lagbe na|ekhon na|না|নাহ|বাতিল|থাক|বাদ দাও|লাগবে না|দরকার নেই|এখন না)" +
            "( (thak|dao|lagbe na|koro na|dorkar nai|saira|ekhon na|থাক|দাও|লাগবে না|করো না|দরকার নেই|সায়রা|এখন না)){0,2}$"
    )

    private fun yesNoText(raw: String): String =
        normalize(raw).lowercase().replace(Regex("[,،;:!?।.…\"'“”‘’\\-–—]+"), " ").replace(Regex("\\s+"), " ").trim()

    private val NUMBER = rx("(?<![\\d+])(\\+?\\d[\\d\\s-]{4,17}\\d)(?!\\d)")

    private val STOP_WORDS: Set<String> = setOf(
        "e", "a", "te", "y", "ke", "k", "er", "r", "on", "in", "via", "the", "to", "my", "please", "plz", "pls",
        "saira", "সায়রা", "সাইরা", "ektu", "ekta", "akta", "amar", "ekhon", "number", "nombor", "nambar", "numbare",
        "numbere", "number-e", "now", "app", "and", "aktu", "ekbar", "ebar",
        "এ", "তে", "একটু", "একটা", "আমার", "এখন", "নম্বরে", "নাম্বারে", "নম্বর", "নাম্বার", "একবার", "এবার", "আর",
        "call", "kol", "video", "message", "msg", "sms", "text", "dao", "daw", "dew", "koro", "kor", "send", "pathao",
        "কল", "ভিডিও", "মেসেজ", "দাও", "করো", "পাঠাও", "hey", "hi", "oi", "ওই", "acha", "আচ্ছা", "theke", "থেকে", "je", "যে",
        "ta", "টা", "ami", "আমি", "kal", "কাল", "aj", "আজ", "tumi", "তুমি", "ekhuni", "এখুনি", "abar", "আবার"
    ).map { normalizePattern(it) }.toSet()

    private val PRONOUNS: Set<String> = setOf(
        "তাকে", "ওকে", "ওনাকে", "আমাকে", "তোমাকে", "তোকে", "আপনাকে", "কাকে", "যাকে", "একে", "এঁকে", "সবাইকে",
        "take", "oke", "onake", "amake", "tomake", "toke", "apnake", "kake", "jake", "ake", "make", "like", "bike",
        "joke", "smoke", "cake", "lake", "poke", "woke", "awake", "spoke", "broke", "choke", "duke", "nuke", "quake",
        "sobaike", "shobaike"
    ).map { normalizePattern(it) }.toSet()

    /** Statements about calls ("ami kal call korbo", "ke call korechilo") rather than orders. */
    private val STATEMENT = rx(
        "\\b(korbo|korbi|korlam|korechi|korechilo|korsilo|korse|korche|dibo|dimu|dilam|dise|dilo|diyechi|diyeche|disilo|" +
            "will|would|did|called|was|asked)\\b|করব|করবো|করলাম|করেছি|করেছিল|করছে|দিব|দেবো|দিলাম|দিয়েছি|দিয়েছে|দিয়েছিল|দিল(?![$BN])"
    )

    // ---- helpers ------------------------------------------------------------------------
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            val i = BN_DIGITS.indexOf(c)
            sb.append(if (i >= 0) ('0' + i) else c)
        }
        return sb.toString()
            .replace("\u09AF\u09BC", "\u09DF") // য + ় → য়
            .replace("\u09A1\u09BC", "\u09DC") // ড + ় → ড়
            .replace("\u09A2\u09BC", "\u09DD") // ঢ + ় → ঢ়
            .replace("\u200C", "").replace("\u200D", "")
            .replace('\u00A0', ' ')
            .replace('’', '\'')
            .replace(rx("\\s+"), " ")
            .trim()
    }

    internal fun normalizePattern(p: String): String =
        p.replace("\u09AF\u09BC", "\u09DF").replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD")

    fun digitsOf(s: String): String = s.filter { it.isDigit() }

    fun isYes(raw: String): Boolean = YES.matches(yesNoText(raw))
    fun isNo(raw: String): Boolean = NO.matches(yesNoText(raw))

    fun detectApp(low: String): PhoneApp? = APP_PATTERNS.firstOrNull { it.second.containsMatchIn(low) }?.first

    fun extractNumber(s: String): String? {
        for (m in NUMBER.findAll(s)) {
            val raw = m.groupValues[1]
            val digits = digitsOf(raw)
            if (digits.length in 5..15) return (if (raw.trim().startsWith("+")) "+" else "") + digits
        }
        return null
    }

    /** True when the message is probably a phone request the local rules could not fully read. */
    fun mightBeCommand(raw: String): Boolean {
        val low = normalize(raw).lowercase()
        if (low.length > 400) return false
        val hasAction = CALL.containsMatchIn(low) || VIDEO.containsMatchIn(low) || MESSAGE_STRONG.containsMatchIn(low)
        val hasApp = detectApp(low) != null
        return hasAction && (hasApp || GIVE.containsMatchIn(low) || extractNumber(low) != null)
    }

    fun parse(raw: String): PhoneCommand? {
        val s = normalize(raw)
        if (s.isEmpty() || s.length > 600) return null
        val low = s.lowercase()
        val words = low.split(' ').filter { it.isNotBlank() }
        val app = detectApp(low)
        val number = extractNumber(s)
        val hasVideo = VIDEO.containsMatchIn(low)
        val hasCall = hasVideo || CALL.containsMatchIn(low)
        val hasGive = GIVE.containsMatchIn(low)

        // Screen on / off
        if (SCREEN_OFF.containsMatchIn(low) && number == null) return PhoneCommand(PhoneAction.SCREEN_OFF, PhoneApp.OTHER)
        if (SCREEN_ON.containsMatchIn(low) && number == null) return PhoneCommand(PhoneAction.SCREEN_ON, PhoneApp.OTHER)

        // Answer / decline a ringing call
        val name = if (number == null) extractName(s)?.takeUnless {
            val l = it.lowercase(); ANSWER.containsMatchIn(l) || DECLINE.containsMatchIn(l)
        } else null
        if (number == null && name == null && !hasVideo) {
            val shortOrAboutCall = hasCall || words.size <= 3
            if (shortOrAboutCall && DECLINE.containsMatchIn(low)) return PhoneCommand(PhoneAction.DECLINE, app ?: PhoneApp.PHONE)
            if (shortOrAboutCall && ANSWER.containsMatchIn(low) && !hasGiveOnly(low)) {
                return PhoneCommand(PhoneAction.ANSWER, app ?: PhoneApp.PHONE)
            }
        }

        val strongMsg = MESSAGE_STRONG.containsMatchIn(low)
        val writeMsg = MESSAGE_WRITE.containsMatchIn(low)
        val target = number ?: name ?: ""
        val text = extractText(s)

        val action: PhoneAction? = when {
            hasVideo -> PhoneAction.VIDEO_CALL
            strongMsg && (target.isNotEmpty() || app != null) -> PhoneAction.MESSAGE
            writeMsg && (number != null || (app != null && app != PhoneApp.OTHER && target.isNotEmpty())) -> PhoneAction.MESSAGE
            hasCall && !(strongMsg && !CALL.containsMatchIn(low)) -> PhoneAction.CALL
            else -> null
        }
        if (action == null) {
            if (!hasCall && !strongMsg) parseOpenApp(low)?.let { return it }
            return null
        }

        if (!hasGive && STATEMENT.containsMatchIn(low)) return null
        // Without a target we only act on a clear order ("call dao", "whatsapp e message dao").
        if (target.isEmpty() && !(hasGive && (app != null || words.size <= 4))) return null
        // A sentence that is only *about* calling ("ke call korechilo?") is not an order.
        if (target.isEmpty() && low.contains("?")) return null

        val resolvedApp = when {
            app == PhoneApp.SMS && action != PhoneAction.MESSAGE -> PhoneApp.PHONE
            app != null -> app
            action == PhoneAction.MESSAGE -> PhoneApp.SMS
            else -> PhoneApp.PHONE
        }
        return PhoneCommand(
            action = action,
            app = resolvedApp,
            target = target,
            text = if (action == PhoneAction.MESSAGE) text else ""
        )
    }

    private fun hasGiveOnly(low: String): Boolean =
        rx("\\b(dao|daw|dew|dio|lagao)\\b|${bnWord("দাও")}|${bnWord("দিও")}|${bnWord("লাগাও")}").containsMatchIn(low) &&
            !ANSWER.containsMatchIn(low.replace(rx("\\b(dao|daw|dew)\\b|দাও"), ""))

    /** Maps the AI extractor's JSON fields to a command (null for "none"). */
    /** "whatsapp kholo", "হোয়াটসঅ্যাপে যাও", "open youtube.", "ইউটিউব খুলে দাও তো" → OPEN_APP. */
    fun parseOpenApp(lowRaw: String): PhoneCommand? {
        val low = lowRaw.trim().trimEnd('.', '!', '।', '?', ',', ' ').replace(OPEN_TAIL, "").trimEnd('.', '!', '।', ',', ' ')
        val m = OPEN_APP.find(low) ?: return null
        var name = m.groupValues[1].ifBlank { m.groupValues[3] }.trim().trim(',', '.')
        val verb = m.groupValues[2]
        // "হোয়াটসঅ্যাপে" / "whatsapp-e" → the app name without the locative ending
        name = name.replace(rx("(?:-e|-te)$"), "")
        // fillers / classifiers: "ক্যামেরাটা একটু" → "ক্যামেরা", "youtube ta ektu" → "youtube"
        name = name.split(' ').filterNot { it in setOf("ektu", "একটু", "ta", "টা", "ti", "টি", "amar", "আমার") }
            .joinToString(" ").replace(rx("(টা|টি)$"), "").trim()
        if (name.any { it in '\u0980'..'\u09FF' }) {
            val bnApp = detectApp(name)
            if (bnApp != null && name.endsWith("ে")) name = name.dropLast(1)
        }
        if (name.isEmpty() || name.split(' ').size > 3 || name in NOT_APPS) return null
        if (verb in GO_VERBS && !KNOWN_APPS.matches(name) && detectApp(name) == null) return null
        val detected = detectApp(name)
        val facebook = name.contains("facebook") || name.contains("ফেসবুক") || name == "fb"
        val app = when {
            facebook && !name.contains("messenger") && !name.contains("মেসেঞ্জার") -> PhoneApp.OTHER
            detected != null && detected != PhoneApp.PHONE && detected != PhoneApp.SMS -> detected
            else -> PhoneApp.OTHER
        }
        return PhoneCommand(PhoneAction.OPEN_APP, app, target = name)
    }

    fun fromFields(action: String?, app: String?, target: String?, text: String?): PhoneCommand? {
        val act = when (action?.trim()?.lowercase()) {
            "call" -> PhoneAction.CALL
            "video_call", "videocall", "video" -> PhoneAction.VIDEO_CALL
            "message", "sms", "text" -> PhoneAction.MESSAGE
            "answer", "receive" -> PhoneAction.ANSWER
            "decline", "reject", "end" -> PhoneAction.DECLINE
            "open_app", "open" -> PhoneAction.OPEN_APP
            "screen_on" -> PhoneAction.SCREEN_ON
            "screen_off" -> PhoneAction.SCREEN_OFF
            else -> return null
        }
        val a = when (app?.trim()?.lowercase()) {
            "whatsapp" -> PhoneApp.WHATSAPP
            "messenger", "facebook" -> PhoneApp.MESSENGER
            "telegram" -> PhoneApp.TELEGRAM
            "imo" -> PhoneApp.IMO
            "viber" -> PhoneApp.VIBER
            "sms" -> PhoneApp.SMS
            "phone", "", null -> if (act == PhoneAction.MESSAGE) PhoneApp.SMS else PhoneApp.PHONE
            else -> if (act == PhoneAction.OPEN_APP) PhoneApp.OTHER else PhoneApp.PHONE
        }
        val t = target?.trim().orEmpty().let { tg ->
            val n = extractNumber(normalize(tg))
            if (n != null && tg.count { it.isLetter() } == 0) n else tg
        }
        return PhoneCommand(
            act,
            if (a == PhoneApp.SMS && act != PhoneAction.MESSAGE) PhoneApp.PHONE else a,
            t,
            text?.trim().orEmpty()
        )
    }

    // ---- name -------------------------------------------------------------------------
    internal fun extractName(s: String): String? {
        val tokens = s.split(' ').filter { it.isNotBlank() }
        val lowTokens = tokens.map { it.lowercase().trim(',', '.', '!', '?', '।', ':') }

        fun isStop(t: String): Boolean {
            if (t.isEmpty()) return true
            if (t in STOP_WORDS) return true
            if (APP_PATTERNS.any { it.second.containsMatchIn(t) }) return true
            if (CALL.containsMatchIn(t) || VIDEO.containsMatchIn(t) || MESSAGE_STRONG.containsMatchIn(t)) return true
            return false
        }

        fun collectBefore(endExclusive: Int, first: String?): String? {
            val parts = ArrayList<String>()
            if (first != null) parts.add(first)
            var i = endExclusive - 1
            while (i >= 0 && parts.size < 3) {
                val t = lowTokens[i]
                if (isStop(t) || t in PRONOUNS) break
                parts.add(0, tokens[i].trim(',', '.', '!', '?', '।', ':'))
                i--
            }
            val name = parts.joinToString(" ").trim()
            return name.ifEmpty { null }
        }

        // Banglish / Bengali object marker: "karim ke", "karim-ke", "করিমকে", "করিম কে"
        for (i in lowTokens.indices) {
            val t = lowTokens[i]
            if ((t == "ke" || t == "k" || t == "কে") && i > 0) {
                if (lowTokens[i - 1] in PRONOUNS) return null
                return collectBefore(i, null)
            }
            if (t in PRONOUNS) continue
            val bnAttached = t.endsWith("কে") && t.length >= 3 && !t.startsWith("কে")
            val latinAttached = (t.endsWith("-ke") || (t.endsWith("ke") && t.length >= 5)) &&
                t.all { it.isLetter() || it == '-' } && t.first() in 'a'..'z'
            if (bnAttached || latinAttached) {
                val stem = tokens[i].trim(',', '.', '!', '?', '।', ':').let {
                    when {
                        it.lowercase().endsWith("-ke") -> it.dropLast(3)
                        it.endsWith("কে") -> it.dropLast(2).trimEnd('-')
                        else -> it.dropLast(2)
                    }
                }
                if (stem.isNotEmpty() && !isStop(stem.lowercase())) return collectBefore(i, stem)
            }
        }

        // Genitive right before the call word / "kache": "ammuker call dao", "abbur kache call dao", "আম্মুর কাছে কল দাও"
        val kache = setOf("kache", "kase", "kachhe", "কাছে")
        for (i in 1 until lowTokens.size) {
            val t = lowTokens[i]
            if (t !in kache && !CALL.containsMatchIn(t) && !VIDEO.containsMatchIn(t)) continue
            val raw = tokens[i - 1].trim(',', '.', '!', '?', '।', ':')
            val low = raw.lowercase()
            if (low in PRONOUNS || isStop(low) || low in setOf("ekbar", "abar", "arekbar", "akbar", "একবার", "আবার")) continue
            val latin = low.isNotEmpty() && low.all { it in 'a'..'z' }
            val stem = when {
                latin && low.endsWith("ker") && low.length >= 6 -> raw.dropLast(3)
                latin && low.endsWith("er") && low.length >= 5 -> raw.dropLast(2)
                latin && low.endsWith("r") && low.length >= 4 && low[low.length - 2] in "uio" -> raw.dropLast(1)
                raw.endsWith("কের") && raw.length >= 4 -> raw.dropLast(3)
                raw.endsWith("ের") && raw.length >= 3 -> raw.dropLast(2)
                raw.endsWith("র") && raw.length >= 3 && !latin -> raw.dropLast(1)
                else -> null
            } ?: continue
            if (stem.isNotEmpty() && !isStop(stem.lowercase())) return collectBefore(i - 1, stem)
        }

        // Genitive before "number": "karim er number e", "করিমের নম্বরে"
        val numberWord = rx("^(number|nombor|nambar|numbare|numbere|number-e|নম্বরে|নাম্বারে|নম্বর|নাম্বার)$")
        for (i in lowTokens.indices) {
            if (!numberWord.matches(lowTokens[i]) || i == 0) continue
            var j = i - 1
            if (lowTokens[j] == "er" || lowTokens[j] == "r" || lowTokens[j] == "এর") j--
            if (j < 0) continue
            var stem = tokens[j].trim(',', '.')
            when {
                stem.endsWith("এর") -> stem = stem.dropLast(2)
                stem.endsWith("ের") -> stem = stem.dropLast(2)
                stem.endsWith("র") && stem.length > 2 -> stem = stem.dropLast(1)
                stem.lowercase().endsWith("'s") -> stem = stem.dropLast(2)
                stem.lowercase().endsWith("er") && j == i - 1 && stem.length > 4 -> stem = stem.dropLast(2)
            }
            if (stem.isNotEmpty() && !isStop(stem.lowercase()) && stem.lowercase() !in PRONOUNS) return collectBefore(j, stem)
        }

        // English: "call karim on whatsapp", "message my mom that ...", "send a message to karim saying hi"
        val en = rx(
            "^(?:(?:please|plz|saira|hey saira)[ ,]+)?(?:send (?:a |an )?(?:whatsapp |telegram )?(?:message|msg|text|sms) to|" +
                "(?:make |place )?(?:a )?video call(?: to)?|call|ring|dial|message|msg|text|sms|tell|whatsapp)\\s+(?:to\\s+)?(.+?)" +
                "(?=\\s+(?:on|in|via|using|through|with|that|saying|and|to say|now|please|right now)\\b|\\s*[:,\"“]|$)",
            RegexOption.IGNORE_CASE
        )
        en.find(s)?.let { m ->
            var cand = m.groupValues[1].trim().trim(',', '.', '!', '?')
            cand = cand.replace(rx("^(?:my|the|a)\\s+", RegexOption.IGNORE_CASE), "")
            val parts = cand.split(' ')
            val lowCand = cand.lowercase()
            if (cand.isNotEmpty() && parts.size <= 3 && cand.any { it.isLetter() } &&
                parts.none { isStop(it.lowercase()) && it.lowercase() !in setOf("my") } &&
                lowCand !in PRONOUNS && !lowCand.startsWith("me") && lowCand !in setOf("him", "her", "them", "it", "dhoro", "receive", "back")
            ) return cand
        }
        return null
    }

    // ---- message text -------------------------------------------------------------------
    private val TRAILING_VERBS = rx(
        "\\s*(?:likhe|bole|lekhe)?\\s*(?:pathao|pathaw|pathan|dao|daw|dew|send koro|send kor|message dao|msg dao|" +
            "লিখে|বলে)?\\s*(?:পাঠাও|পাঠান|দাও|দিন|জানিয়ে দাও|জানাও)?\\s*$"
    )

    internal fun extractText(s: String): String {
        rx("[\"“”«](.+?)[\"“”»]").find(s)?.let { return clean(it.groupValues[1]) }
        rx("‘(.+?)’").find(s)?.let { return clean(it.groupValues[1]) }
        val colon = s.indexOf(':')
        if (colon in 1 until s.length - 1) {
            val after = s.substring(colon + 1).trim()
            if (after.isNotEmpty()) return clean(after)
        }
        val marker = rx(
            "(?:^|\\s)(?:je|jeno|যে|যেন|that|saying|to say|bolo|bolo je|বলো|লিখো|লেখো|লিখে দাও|likho|likhe dao|write)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        marker.find(s)?.let { m ->
            var t = m.groupValues[1]
            t = t.replace(TRAILING_VERBS, "")
            return clean(t)
        }
        return ""
    }

    private fun clean(t: String): String = t.trim().trim('"', '“', '”', '‘', '’').trim().take(1000)

    // ---- formatting -------------------------------------------------------------------
    private val COUNTRY_CODES = mapOf(
        "bd" to "880", "in" to "91", "pk" to "92", "us" to "1", "ca" to "1", "gb" to "44", "sa" to "966",
        "ae" to "971", "my" to "60", "sg" to "65", "qa" to "974", "kw" to "965", "om" to "968", "np" to "977",
        "lk" to "94", "it" to "39", "jp" to "81", "au" to "61", "bh" to "973"
    )

    /** International digits (no +) for WhatsApp / Telegram links. */
    fun internationalDigits(number: String, simCountryIso: String?): String {
        val trimmed = number.trim()
        val digits = digitsOf(trimmed)
        if (trimmed.startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.drop(2)
        val cc = COUNTRY_CODES[simCountryIso?.lowercase().orEmpty()] ?: "880"
        if (cc == "880" && digits.startsWith("880") && digits.length >= 12) return digits
        if (digits.startsWith("0")) return cc + digits.drop(1)
        if (cc == "880" && digits.length == 10 && digits.startsWith("1")) return cc + digits
        return digits
    }

    /** Spoken/visible description used in Saira's confirmation question. */
    fun describe(cmd: PhoneCommand, who: String): String {
        val via = when (cmd.app) {
            PhoneApp.PHONE, PhoneApp.SMS, PhoneApp.OTHER -> ""
            else -> " ${cmd.app.label}-এ"
        }
        return when (cmd.action) {
            PhoneAction.CALL -> "$who-কে$via কল দেব?"
            PhoneAction.VIDEO_CALL -> "$who-কে$via ভিডিও কল দেব?"
            PhoneAction.MESSAGE -> {
                val via2 = if (cmd.app == PhoneApp.SMS) " SMS-এ" else via
                "$who-কে$via2 এই মেসেজ পাঠাব: \"${cmd.text}\"?"
            }
            PhoneAction.ANSWER -> "কল ধরব?"
            PhoneAction.DECLINE -> "কল কেটে দেব?"
            PhoneAction.OPEN_APP -> "${cmd.target} খুলব?"
            PhoneAction.SCREEN_ON -> "স্ক্রিন অন করব?"
            PhoneAction.SCREEN_OFF -> "স্ক্রিন বন্ধ করব?"
        }
    }
}

/** Wake word handling for voice mode: "Saira, …" at the start of what the user said. */
object SairaWakeWord {
    private const val NAMES =
        "saira|sayra|saera|shaira|shayra|sahira|sairah|syra|cyra|sayira|saaira|shayira|" +
            "সায়রা|সাইরা|সায়েরা|শায়রা|শাইরা|সাইরাহ|সাইরা|সায়রা|সায়রা|" +
            // live transcription sometimes writes the name in Devanagari
            "सायरा|शायरा|साइरा|सैरा|शाइरा"

    private val WAKE = rx(
        "^(?:(?:hey|hi|hello|ok|okay|oi|ei|ay|ji|জি|ওই|এই|হেই|হ্যালো|ওকে|শোনো|shono|acha|আচ্ছা)[ ,!.।]+){0,2}" +
            "($NAMES|sarah)" +
            "(?:[ ,!.।:-]+|$)",
        RegexOption.IGNORE_CASE
    )

    /** The name as a word of its own anywhere ("কয়টা বাজে, সায়রা?") — not "সায়রাকে" / "সায়রার" (talking about her). */
    private val ANYWHERE = rx("(?<![\\p{L}\\p{M}\\p{N}])($NAMES)(?![\\p{L}\\p{M}\\p{N}])", RegexOption.IGNORE_CASE)
    private val GREETING_TAIL = rx("(?:^|[ ,])(?:hey|hi|hello|ok|okay|oi|ei|ji|জি|ওই|এই|হেই|হ্যালো|ওকে|আচ্ছা|শোনো)[ ,]*$", RegexOption.IGNORE_CASE)
    private val HAS_WORD = Regex("[\\p{L}\\p{N}].*[\\p{L}\\p{N}]|[\\p{L}\\p{N}]{2,}")
    private const val EDGE = " ,،!।.:;-—\"“”'"

    /**
     * Returns the command said to Saira, "" if only the name was said, or null if Saira wasn't addressed.
     * The name may come first ("Saira, call ammu"), last ("কয়টা বাজে, সায়রা?") or after someone
     * else's words ("…ওরা আসবে না। সায়রা, আম্মুকে কল দাও" → only the part from the name on).
     */
    fun extract(transcript: String): String? {
        val s = SairaPhoneCommandParser.normalize(transcript).trim().trimStart('"', '“', '\'')
        WAKE.find(s)?.let { m -> return s.substring(m.range.last + 1).trim().trimEnd('"', '”') }
        val m = ANYWHERE.find(s) ?: return null
        val after = s.substring(m.range.last + 1).trim { it in EDGE }
        if (HAS_WORD.containsMatchIn(after)) return after.trimEnd('"', '”')
        // Name at the end: the command is the sentence just before it.
        val before = s.substring(0, m.range.first).trim { it in EDGE }
        val last = before.split(Regex("[।.!?]+\\s+")).lastOrNull { it.isNotBlank() }.orEmpty()
        return last.replace(GREETING_TAIL, "").trim { it in EDGE }
    }
}
