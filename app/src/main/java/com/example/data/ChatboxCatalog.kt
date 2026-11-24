package com.example.data

import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxMessageKind
import com.example.data.model.ChatboxThread
import kotlin.math.abs

object ChatboxCatalog {
    val DEMO_THREAD_IDS: Set<String> = setOf(
        "sazzad",
        "saira",
        "maya",
        "arif",
        "nora",
        "together"
    )

    val DEMO_CALL_LOG_IDS: Set<String> = setOf("call_seed_1")

    val defaultThreads: List<ChatboxThread> = emptyList()

    val defaultSeeds: Map<String, List<ChatboxMessage>> = emptyMap()

    val archivedHistorySeeds: Map<String, List<ChatboxMessage>> = emptyMap()

    val defaultCallLogs: List<ChatboxCallEntry> = emptyList()

    fun buildSafetyNumberBlocks(threadId: String, peerName: String): List<String> {
        val seedInput = "$threadId:$peerName:chatbox-e2ee"
        var h = 0x811c9dc5.toInt()
        val blocks = mutableListOf<String>()
        for (i in 0 until 12) {
            for (ch in seedInput) {
                h = h xor (ch.code + i * 31)
                h *= 0x01000193
            }
            val positive = if (h == Int.MIN_VALUE) 0 else abs(h)
            blocks += "%05d".format(positive % 100000)
        }
        return blocks
    }

    fun getSairaOfflineGuideReply(input: String): String {
        val text = input.trim().lowercase()
        return when {
            Regex("^(hi+|hello|hey|salam|assalam|হ্যালো|হাই|সালাম|কেমন\\s*আছো?)").containsMatchIn(text) ->
                "হ্যালো! আমি Saira ✨ আপনার সব কাজের জন্য নিচের Saira AI ট্যাবটি খুলুন—সেখানে ফ্রি AI উত্তর, ১০-সেকেন্ড ও ১-মিনিট চ্যাটবক্স অপেক্ষা, WhatsApp/Messenger অটো-রিপ্লাই ও অনুমতিসাপেক্ষ লোকেশন শেয়ার কাজ করে।"
            Regex("কমান্ড|command|help|auto.?reply|location|লোকেশন|অটো").containsMatchIn(text) ->
                "Saira AI-তে ৪টি প্রধান সুবিধা সক্রিয় আছে:\n১) চ্যাটবক্সে ১ মিনিটের মধ্যে সাড়া না পেলে প্রেরকের নাম ও সময় অনুযায়ী সম্ভাষণ\n২) অনুমতিসাপেক্ষে WhatsApp ও Messenger অটো-রিপ্লাই\n৩) অনুমতি ও নির্ধারিত ব্যক্তির ক্ষেত্রে লাইভ লোকেশন শেয়ারিং\n৪) দিন-রাতের সময় বুঝে সম্ভাষণ ও ফ্রি AI উত্তর।"
            Regex("6.?digit|short.?id|\\bid\\b|আইডি|প্রোফাইল|profile").containsMatchIn(text) ->
                "Profile ট্যাব থেকে আপনার ৬-ডিজিট ইউজার আইডি, নাম, প্রোফাইল ছবি ও ফোন নম্বর সেট করতে এবং বন্ধুদের সাথে শেয়ার করতে পারবেন।"
            Regex("pin|restore|encrypt|e2ee|পিন|এনক্রিপশন").containsMatchIn(text) ->
                "যেকোনো DM-এর উপরে \"Enter PIN\" চাপলে ডিভাইস PIN (ডিফল্ট 123456) দিয়ে পুরনো মেসেজ রিস্টোর করতে পারবেন এবং Settings থেকে E2EE যাচাই করতে পারবেন।"
            Regex("call|video|voice|কল|ভিডিও|কথা").containsMatchIn(text) ->
                "যেকোনো DM বা Details স্ক্রিন থেকে Voice ও Video কল শুরু করতে পারবেন এবং Calls ট্যাবে সব কলের তালিকা দেখতে পারবেন।"
            Regex("update|version|আপডেট|ভার্সন").containsMatchIn(text) ->
                "এই অ্যাপের ভার্সন v${ChatboxConstants.APP_VERSION}। নতুন আপডেট থাকলে লোগোর পাশে সবুজ Update ব্যাজ দেখাবে।"
            Regex("firebase|ফায়ারবেস").containsMatchIn(text) ->
                "Chatbox-এ Firebase AI Logic (App Check সহ) সংযুক্ত আছে যাতে লগইন ছাড়াই Saira AI দ্রুত কাজ করে।"
            Regex("feature|chat|message|ফিচার|চ্যাট|মেসেজ|কি আছে").containsMatchIn(text) ->
                "Chatbox ট্যাবে সব ইউজারের DM, গ্রুপ তৈরি, ছবি ও ভয়েস নোট, কুইক রিঅ্যাকশন, নিকনেম, রিড রিসিট, ডিসঅ্যাপিয়ারিং মেসেজ, ব্লক ও রিপোর্ট সুবিধা রয়েছে।"
            Regex("develop|built|made|বানানো|তৈরি|কিভাবে").containsMatchIn(text) ->
                "এই নেটিভ Android অ্যাপটি Kotlin ও Jetpack Compose (Material 3) দিয়ে তৈরি।"
            else ->
                "আমি এখন Chatbox-এর অফলাইন গাইড মোডে আছি। পূর্ণ AI উত্তরের জন্য ইন্টারনেট চালু রেখে নিচের Saira ট্যাব ব্যবহার করুন।"
        }
    }
}
