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
        val text = input.trim().lowercase()
        return when {
            Regex("^(hi+|hello|hey|salam|assalam|হ্যালো|হাই|সালাম|কেমন\\s*আছো?)").containsMatchIn(text) ->
                "হ্যালো! আমি Saira ✨ আপনার সব কাজের জন্য পূর্ণ Saira AI ট্যাবটি খুলুন—সেখানে ফ্রি AI উত্তর, ১ মিনিটের চ্যাটবক্স অপেক্ষা, WhatsApp/Messenger অটো-রিপ্লাই ও অনুমতিসাপেক্ষ লোকেশন শেয়ার কাজ করে।"
            Regex("কমান্ড|command|help|auto.?reply|location|লোকেশন|অটো").containsMatchIn(text) ->
                "Saira AI-তে ৪টি প্রধান সুবিধা সক্রিয় আছে:\n১) চ্যাটবক্সে ১ মিনিটের মধ্যে সাড়া না পেলে প্রেরকের নাম ও সময় অনুযায়ী সম্ভাষণ\n২) অনুমতিসাপেক্ষে WhatsApp ও Messenger অটো-রিপ্লাই\n৩) অনুমতি ও নির্ধারিত ব্যক্তির ক্ষেত্রে লাইভ লোকেশন শেয়ারিং\n৪) দিন-রাতের সময় বুঝে সম্ভাষণ ও ফ্রি AI উত্তর।"
            Regex("6.?digit|short.?id|\\bid\\b|আইডি|প্রোফাইল|profile").containsMatchIn(text) ->
                "Live ট্যাবের উপরের Profile আইকন থেকে আপনার ৬-ডিজিট ইউজার আইডি ও ফোন নম্বর সেট করে বন্ধুদের অ্যাড করতে পারবেন।"
            Regex("admin|অ্যাডমিন|এডমিন|theme|menu|থিম|মেনু").containsMatchIn(text) ->
                "অ্যাডমিন প্যানেল লক করা আছে। অনুমোদিত অ্যাডমিন জিমেইল দিয়ে Admin Panel থেকে থিম, মেনুবার ও আপডেট কন্ট্রোল করতে পারবেন।"
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
                "আমি এখন ডেমো মোডের একটি সীমিত অফলাইন গাইড। পূর্ণ Saira AI ও অটো-রিপ্লাই সুবিধার জন্য নিচের Saira ট্যাব ব্যবহার করুন।"
        }
    }
}
