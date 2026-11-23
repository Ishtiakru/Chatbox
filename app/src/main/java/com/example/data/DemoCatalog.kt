package com.example.data

import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxConstants
import com.example.data.model.DemoContact
import com.example.data.model.DemoMessage

object DemoCatalog {
    val contacts: List<DemoContact> = listOf(
        DemoContact(
            id = "saira",
            name = "Saira",
            role = "Your app companion",
            avatar = "✳",
            style = AvatarStyle.SAIRA,
            label = "AI GUIDE",
            online = true
        ),
        DemoContact(
            id = "maya",
            name = "Maya Rahman",
            role = "Demo conversation",
            avatar = "MR",
            style = AvatarStyle.PEACH,
            online = true
        ),
        DemoContact(
            id = "arif",
            name = "Arif Hasan",
            role = "Demo conversation",
            avatar = "AH",
            style = AvatarStyle.LAVENDER,
            online = false
        ),
        DemoContact(
            id = "nora",
            name = "Nora Ahmed",
            role = "Demo conversation",
            avatar = "NA",
            style = AvatarStyle.YELLOW,
            online = false
        ),
        DemoContact(
            id = "together",
            name = "The good people",
            role = "Demo group",
            avatar = "✳",
            style = AvatarStyle.BLUE,
            online = false
        )
    )

    val defaultSeeds: Map<String, List<DemoMessage>> = mapOf(
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

    fun getSairaOfflineGuideReply(input: String): String {
        val text = input.lowercase()
        return when {
            Regex("admin|অ্যাডমিন|এডমিন").containsMatchIn(text) ->
                "অ্যাডমিন প্যানেল লক করা আছে। Firebase security rules অনুযায়ী শুধু অনুমোদিত অ্যাডমিন প্রবেশ করতে পারবেন।"
            Regex("call|video|voice|কল|ভিডিও|কথা").containsMatchIn(text) ->
                "ভয়েস ও ভিডিও কলের ডিজাইন প্রস্তুত, কিন্তু কল এখনো সংযুক্ত নয়। এর জন্য নিরাপদ signaling ও calling service প্রয়োজন।"
            Regex("update|version|আপডেট|ভার্সন").containsMatchIn(text) ->
                "এই অ্যাপের ভার্সন v${ChatboxConstants.APP_VERSION}। Admin Panel থেকে নতুন আপডেট প্রকাশ করলে লোগোর পাশে সবুজ Update ব্যাজ দেখাবে।"
            Regex("firebase|ফায়ারবেস").containsMatchIn(text) ->
                "Google Sign-In, Cloud Firestore, Firebase Storage এবং Firebase AI Logic (App Check সহ) Chatbox-এ সংযুক্ত আছে।"
            Regex("feature|chat|message|ফিচার|চ্যাট|মেসেজ|কি আছে").containsMatchIn(text) ->
                "এখন আপনি ডেমো চ্যাট দেখতে ও খুঁজতে পারেন, এবং Google দিয়ে সাইন ইন করলে Live chats, Saira AI ও Admin Panel ব্যবহার করতে পারবেন।"
            Regex("develop|built|made|বানানো|তৈরি|কিভাবে").containsMatchIn(text) ->
                "এই নেটিভ Android অ্যাপটি Kotlin ও Jetpack Compose (Material 3) দিয়ে তৈরি, যা Firebase Auth, Firestore, Storage ও Gemini AI ব্যবহার করে।"
            else ->
                "আমি এখন ডেমো মোডের একটি সীমিত অফলাইন গাইড। পূর্ণ AI-এর জন্য Saira AI ট্যাব ব্যবহার করুন অথবা চ্যাট, কল, অ্যাডমিন, Firebase বা অ্যাপের ফিচার সম্পর্কে জিজ্ঞেস করুন।"
        }
    }
}
