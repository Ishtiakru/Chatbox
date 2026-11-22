package com.example.data.model

import kotlinx.serialization.Serializable

enum class AvatarStyle {
    SAIRA, PEACH, LAVENDER, YELLOW, BLUE
}

data class DemoContact(
    val id: String,
    val name: String,
    val role: String,
    val avatar: String,
    val style: AvatarStyle,
    val label: String?,
    val online: Boolean
)

@Serializable
data class DemoMessage(
    val from: String,
    val text: String,
    val time: String
)

object DemoCatalog {
    const val APP_VERSION = "0.1.0"

    val contacts = listOf(
        DemoContact("saira", "Saira", "Your app companion", "✳", AvatarStyle.SAIRA, "AI GUIDE", true),
        DemoContact("maya", "Maya Rahman", "Demo conversation", "MR", AvatarStyle.PEACH, null, true),
        DemoContact("arif", "Arif Hasan", "Demo conversation", "AH", AvatarStyle.LAVENDER, null, false),
        DemoContact("nora", "Nora Ahmed", "Demo conversation", "NA", AvatarStyle.YELLOW, null, false),
        DemoContact("together", "The good people", "Demo group", "✳", AvatarStyle.BLUE, null, false)
    )

    val seeds: Map<String, List<DemoMessage>> = mapOf(
        "saira" to listOf(
            DemoMessage(
                from = "them",
                text = "হ্যালো! আমি Saira ✨ এই অ্যাপ সম্পর্কে কিছু জানতে চাইলে আমাকে জিজ্ঞেস করুন। আমি এখন একটি অফলাইন গাইড।",
                time = "এখন"
            )
        ),
        "maya" to listOf(
            DemoMessage("them", "Hey! How has your week been going? ☀️", "10:24 AM"),
            DemoMessage("me", "Pretty good, actually! Just taking things one day at a time.", "10:26 AM"),
            DemoMessage("them", "Love that for you. We should catch up soon!", "10:28 AM"),
            DemoMessage("them", "Maybe a little coffee date this weekend? ☕", "10:28 AM"),
            DemoMessage("me", "That sounds like the perfect plan 🤍", "10:30 AM")
        ),
        "arif" to listOf(
            DemoMessage("them", "Let’s make something great together ✨", "Yesterday")
        ),
        "nora" to listOf(
            DemoMessage("them", "Sending you a little sunshine today!", "Yesterday")
        ),
        "together" to listOf(
            DemoMessage("them", "A little space for our favorite people 💛", "Monday")
        )
    )
}

object SairaOfflineGuide {
    private val adminRegex = Regex("admin|অ্যাডমিন|এডমিন", RegexOption.IGNORE_CASE)
    private val callRegex = Regex("call|video|voice|কল|ভিডিও|কথা", RegexOption.IGNORE_CASE)
    private val updateRegex = Regex("update|version|আপডেট|ভার্সন", RegexOption.IGNORE_CASE)
    private val firebaseRegex = Regex("firebase|ফায়ারবেস", RegexOption.IGNORE_CASE)
    private val featureRegex = Regex("feature|chat|message|ফিচার|চ্যাট|মেসেজ|কি আছে", RegexOption.IGNORE_CASE)
    private val developRegex = Regex("develop|built|made|বানানো|তৈরি|কিভাবে", RegexOption.IGNORE_CASE)

    fun reply(input: String): String {
        val text = input.lowercase()
        return when {
            adminRegex.containsMatchIn(text) ->
                "অ্যাডমিন প্যানেল লক করা আছে। server-side security rules অনুমোদিত হলে শুধু অনুমোদিত অ্যাডমিন প্রবেশ করতে পারবেন।"
            callRegex.containsMatchIn(text) ->
                "ভয়েস ও ভিডিও কলের ডিজাইন প্রস্তুত, কিন্তু কল এখনো সংযুক্ত নয়। এর জন্য নিরাপদ signaling ও calling service প্রয়োজন।"
            updateRegex.containsMatchIn(text) ->
                "এই অ্যাপের ভার্সন ${DemoCatalog.APP_VERSION}। ভবিষ্যতের আপডেট স্বয়ংক্রিয়ভাবে জানার সুবিধা এখনো যুক্ত হয়নি।"
            firebaseRegex.containsMatchIn(text) ->
                "Firebase, Google Sign-In, Live Firestore chat এবং Saira AI (Firebase AI Logic) Android অ্যাপে সংযুক্ত করা হয়েছে।"
            featureRegex.containsMatchIn(text) ->
                "এখন আপনি ডেমো চ্যাট দেখতে ও খুঁজতে পারেন, এবং Google দিয়ে সাইন ইন করলে Live chats, Saira AI ও Spark Admin ব্যবহার করতে পারবেন।"
            developRegex.containsMatchIn(text) ->
                "এই অ্যান্ড্রয়েড অ্যাপটি Kotlin এবং Jetpack Compose দিয়ে তৈরি, যা Firebase Auth, Firestore এবং Firebase AI Logic ব্যবহার করে।"
            else ->
                "আমি এখন ডেমো মোডের একটি সীমিত অফলাইন গাইড। পূর্ণ AI-এর জন্য Saira AI ট্যাব ব্যবহার করুন অথবা চ্যাট, কল, অ্যাডমিন, Firebase বা অ্যাপের ফিচার সম্পর্কে জিজ্ঞেস করুন।"
        }
    }
}
