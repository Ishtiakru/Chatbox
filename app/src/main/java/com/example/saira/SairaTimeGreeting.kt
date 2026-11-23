package com.example.saira

import java.util.Calendar

data class TimeGreetingInfo(
    val bengaliGreeting: String,
    val englishGreeting: String,
    val isNightTime: Boolean
)

object SairaTimeGreeting {

    fun infoForHour(
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): TimeGreetingInfo {
        val normalized = ((hourOfDay % 24) + 24) % 24
        return when (normalized) {
            in 5..11 -> TimeGreetingInfo(
                bengaliGreeting = "সুপ্রভাত",
                englishGreeting = "Good Morning",
                isNightTime = false
            )
            in 12..16 -> TimeGreetingInfo(
                bengaliGreeting = "শুভ দুপুর",
                englishGreeting = "Good Afternoon",
                isNightTime = false
            )
            in 17..20 -> TimeGreetingInfo(
                bengaliGreeting = "শুভ সন্ধ্যা",
                englishGreeting = "Good Evening",
                isNightTime = false
            )
            else -> TimeGreetingInfo(
                bengaliGreeting = "শুভ রাত্রি",
                englishGreeting = "Good Night",
                isNightTime = true
            )
        }
    }

    /**
     * Builds the 1-minute Chatbox waiting greeting addressing the sender by name
     * with the current time-of-day greeting, e.g.:
     * "হ্যালো সজীব! শুভ সন্ধ্যা ✨ আপনি ১ মিনিট আগে মেসেজ দিয়েছিলেন..."
     */
    fun buildOneMinuteAbsenceFallback(
        senderName: String,
        peerName: String,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String {
        val cleanSender = senderName.trim().ifEmpty { "বন্ধু" }
        val cleanPeer = peerName.trim().ifEmpty { "আপনার বন্ধু" }
        val info = infoForHour(hourOfDay)
        return "হ্যালো $cleanSender! ${info.bengaliGreeting} ✨ $cleanPeer এখনো সাড়া দেননি, ততক্ষণ আমি সায়রা আপনার সাথে আছি—কিছু জানতে বা কথা বলতে চাইলে আমাকে বলতে পারেন।"
    }

    fun isLocationRequest(messageText: String): Boolean {
        val lower = messageText.trim().lowercase()
        if (lower.isEmpty()) return false
        return LOCATION_KEYWORDS.any { lower.contains(it) }
    }

    private val LOCATION_KEYWORDS = listOf(
        "location",
        "live location",
        "where are you",
        "kothay",
        "koi aco",
        "koi acho",
        "কোথায়",
        "কোথায়",
        "লোকেশন",
        "কই আছো",
        "কই আছেন"
    )
}
