package com.example.data.model

data class WorldLanguageOption(
    val code: String,
    val nativeLabel: String,
    val englishName: String,
    val countryName: String
) {
    val displayTitle: String
        get() = "$nativeLabel ($countryName)"
}

object WorldLanguages {
    val all: List<WorldLanguageOption> = listOf(
        WorldLanguageOption("en-US", "English (US)", "English", "United States"),
        WorldLanguageOption("en-GB", "English (UK)", "English", "United Kingdom"),
        WorldLanguageOption("bn-BD", "বাংলা (বাংলাদেশ)", "Bengali", "Bangladesh"),
        WorldLanguageOption("bn-IN", "বাংলা (ভারত)", "Bengali", "India"),
        WorldLanguageOption("hi-IN", "हिन्दी (भारत)", "Hindi", "India"),
        WorldLanguageOption("ar-SA", "العربية (السعودية)", "Arabic", "Saudi Arabia"),
        WorldLanguageOption("ar-AE", "العربية (الإمارات)", "Arabic", "United Arab Emirates"),
        WorldLanguageOption("ar-EG", "العربية (مصر)", "Arabic", "Egypt"),
        WorldLanguageOption("ur-PK", "اردو (پاکستان)", "Urdu", "Pakistan"),
        WorldLanguageOption("es-ES", "Español (España)", "Spanish", "Spain"),
        WorldLanguageOption("es-MX", "Español (México)", "Spanish", "Mexico"),
        WorldLanguageOption("fr-FR", "Français (France)", "French", "France"),
        WorldLanguageOption("fr-CA", "Français (Canada)", "French", "Canada"),
        WorldLanguageOption("de-DE", "Deutsch (Deutschland)", "German", "Germany"),
        WorldLanguageOption("pt-BR", "Português (Brasil)", "Portuguese", "Brazil"),
        WorldLanguageOption("pt-PT", "Português (Portugal)", "Portuguese", "Portugal"),
        WorldLanguageOption("it-IT", "Italiano (Italia)", "Italian", "Italy"),
        WorldLanguageOption("ru-RU", "Русский (Россия)", "Russian", "Russia"),
        WorldLanguageOption("zh-CN", "中文 (简体 · 中国)", "Chinese Simplified", "China"),
        WorldLanguageOption("zh-TW", "中文 (繁體 · 台灣)", "Chinese Traditional", "Taiwan"),
        WorldLanguageOption("ja-JP", "日本語 (日本)", "Japanese", "Japan"),
        WorldLanguageOption("ko-KR", "한국어 (대한민국)", "Korean", "South Korea"),
        WorldLanguageOption("id-ID", "Bahasa Indonesia", "Indonesian", "Indonesia"),
        WorldLanguageOption("ms-MY", "Bahasa Melayu", "Malay", "Malaysia"),
        WorldLanguageOption("tr-TR", "Türkçe (Türkiye)", "Turkish", "Turkey"),
        WorldLanguageOption("vi-VN", "Tiếng Việt (Việt Nam)", "Vietnamese", "Vietnam"),
        WorldLanguageOption("th-TH", "ไทย (ประเทศไทย)", "Thai", "Thailand"),
        WorldLanguageOption("fil-PH", "Filipino (Pilipinas)", "Filipino", "Philippines"),
        WorldLanguageOption("fa-IR", "فارسی (ایران)", "Persian", "Iran"),
        WorldLanguageOption("ne-NP", "नेपाली (नेपाल)", "Nepali", "Nepal"),
        WorldLanguageOption("si-LK", "සිංහල (ශ්‍රී ලංකා)", "Sinhala", "Sri Lanka"),
        WorldLanguageOption("ta-IN", "தமிழ் (இந்தியா)", "Tamil", "India"),
        WorldLanguageOption("te-IN", "తెలుగు (భారతదేశం)", "Telugu", "India"),
        WorldLanguageOption("mr-IN", "मराठी (भारत)", "Marathi", "India"),
        WorldLanguageOption("pa-IN", "ਪੰਜਾਬੀ (ਭਾਰਤ)", "Punjabi", "India"),
        WorldLanguageOption("gu-IN", "ગુજરાતી (ભારત)", "Gujarati", "India"),
        WorldLanguageOption("sw-KE", "Kiswahili (Kenya)", "Swahili", "Kenya"),
        WorldLanguageOption("nl-NL", "Nederlands (Nederland)", "Dutch", "Netherlands"),
        WorldLanguageOption("pl-PL", "Polski (Polska)", "Polish", "Poland"),
        WorldLanguageOption("uk-UA", "Українська (Україна)", "Ukrainian", "Ukraine"),
        WorldLanguageOption("el-GR", "Ελληνικά (Ελλάδα)", "Greek", "Greece"),
        WorldLanguageOption("sv-SE", "Svenska (Sverige)", "Swedish", "Sweden"),
        WorldLanguageOption("nb-NO", "Norsk (Norge)", "Norwegian", "Norway"),
        WorldLanguageOption("da-DK", "Dansk (Danmark)", "Danish", "Denmark"),
        WorldLanguageOption("fi-FI", "Suomi (Suomi)", "Finnish", "Finland"),
        WorldLanguageOption("cs-CZ", "Čeština (Česko)", "Czech", "Czechia"),
        WorldLanguageOption("ro-RO", "Română (România)", "Romanian", "Romania"),
        WorldLanguageOption("hu-HU", "Magyar (Magyarország)", "Hungarian", "Hungary"),
        WorldLanguageOption("he-IL", "עברית (ישראל)", "Hebrew", "Israel"),
        WorldLanguageOption("am-ET", "አማርኛ (ኢትዮጵያ)", "Amharic", "Ethiopia"),
        WorldLanguageOption("ha-NG", "Hausa (Nigeria)", "Hausa", "Nigeria"),
        WorldLanguageOption("yo-NG", "Yorùbá (Nigeria)", "Yoruba", "Nigeria")
    )

    fun normalizeCode(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return when (trimmed) {
            "bn" -> "bn-BD"
            "en", "" -> "en-US"
            else -> all.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }?.code ?: "en-US"
        }
    }

    fun findByCode(code: String?): WorldLanguageOption {
        val normalized = normalizeCode(code)
        return all.firstOrNull { it.code == normalized } ?: all.first()
    }

    fun isBengali(code: String?): Boolean = normalizeCode(code).startsWith("bn", ignoreCase = true)
}
