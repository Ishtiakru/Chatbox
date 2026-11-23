package com.example.data.model

data class WorldLanguage(
    val code: String,
    val label: String,
    val region: String
)

object WorldLanguages {
    val all: List<WorldLanguage> = listOf(
        WorldLanguage("en-US", "English (US)", "United States"),
        WorldLanguage("en-GB", "English (UK)", "United Kingdom"),
        WorldLanguage("bn-BD", "বাংলা (বাংলাদেশ)", "Bangladesh"),
        WorldLanguage("bn-IN", "বাংলা (ভারত)", "India"),
        WorldLanguage("ar-SA", "العربية (السعودية)", "Saudi Arabia"),
        WorldLanguage("ar-EG", "العربية (مصر)", "Egypt"),
        WorldLanguage("hi-IN", "हिन्दी (भारत)", "India"),
        WorldLanguage("ur-PK", "اردو (پاکستان)", "Pakistan"),
        WorldLanguage("es-ES", "Español (España)", "Spain"),
        WorldLanguage("es-MX", "Español (México)", "Mexico"),
        WorldLanguage("fr-FR", "Français (France)", "France"),
        WorldLanguage("fr-CA", "Français (Canada)", "Canada"),
        WorldLanguage("de-DE", "Deutsch (Deutschland)", "Germany"),
        WorldLanguage("it-IT", "Italiano (Italia)", "Italy"),
        WorldLanguage("pt-BR", "Português (Brasil)", "Brazil"),
        WorldLanguage("pt-PT", "Português (Portugal)", "Portugal"),
        WorldLanguage("ru-RU", "Русский (Россия)", "Russia"),
        WorldLanguage("tr-TR", "Türkçe (Türkiye)", "Turkey"),
        WorldLanguage("id-ID", "Bahasa Indonesia", "Indonesia"),
        WorldLanguage("ms-MY", "Bahasa Melayu", "Malaysia"),
        WorldLanguage("vi-VN", "Tiếng Việt", "Vietnam"),
        WorldLanguage("th-TH", "ไทย (ประเทศไทย)", "Thailand"),
        WorldLanguage("ja-JP", "日本語 (日本)", "Japan"),
        WorldLanguage("ko-KR", "한국어 (대한민국)", "South Korea"),
        WorldLanguage("zh-CN", "中文 (简体)", "China"),
        WorldLanguage("zh-TW", "中文 (繁體)", "Taiwan"),
        WorldLanguage("nl-NL", "Nederlands", "Netherlands"),
        WorldLanguage("pl-PL", "Polski", "Poland"),
        WorldLanguage("uk-UA", "Українська", "Ukraine"),
        WorldLanguage("ro-RO", "Română", "Romania"),
        WorldLanguage("el-GR", "Ελληνικά", "Greece"),
        WorldLanguage("cs-CZ", "Čeština", "Czechia"),
        WorldLanguage("sv-SE", "Svenska", "Sweden"),
        WorldLanguage("da-DK", "Dansk", "Denmark"),
        WorldLanguage("fi-FI", "Suomi", "Finland"),
        WorldLanguage("nb-NO", "Norsk Bokmål", "Norway"),
        WorldLanguage("hu-HU", "Magyar", "Hungary"),
        WorldLanguage("he-IL", "עברית", "Israel"),
        WorldLanguage("fa-IR", "فارسی", "Iran"),
        WorldLanguage("sw-KE", "Kiswahili", "Kenya / Tanzania"),
        WorldLanguage("am-ET", "አማርኛ", "Ethiopia"),
        WorldLanguage("ta-IN", "தமிழ்", "India / Sri Lanka"),
        WorldLanguage("te-IN", "తెలుగు", "India"),
        WorldLanguage("mr-IN", "मराठी", "India"),
        WorldLanguage("gu-IN", "ગુજરાતી", "India"),
        WorldLanguage("kn-IN", "ಕನ್ನಡ", "India"),
        WorldLanguage("ml-IN", "മലയാളം", "India"),
        WorldLanguage("pa-IN", "ਪੰਜਾਬੀ", "India"),
        WorldLanguage("ne-NP", "नेपाली", "Nepal"),
        WorldLanguage("si-LK", "සිංහල", "Sri Lanka"),
        WorldLanguage("my-MM", "မြန်မာဘာသာ", "Myanmar"),
        WorldLanguage("fil-PH", "Filipino", "Philippines")
    )

    fun normalizeCode(code: String?): String {
        val raw = code?.trim().orEmpty()
        val exact = all.firstOrNull { it.code.equals(raw, ignoreCase = true) }
        if (exact != null) return exact.code
        val base = raw.split("-").firstOrNull().orEmpty().lowercase()
        return all.firstOrNull { it.code.lowercase().startsWith("$base-") }?.code ?: "en-US"
    }

    fun isBengali(code: String?): Boolean = normalizeCode(code).lowercase().startsWith("bn")

    fun displayLabel(code: String?): String {
        val norm = normalizeCode(code)
        return all.firstOrNull { it.code == norm }?.label ?: "English (US)"
    }
}
