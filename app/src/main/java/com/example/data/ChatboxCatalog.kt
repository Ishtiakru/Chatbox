package com.example.data

import com.example.data.model.AvatarStyle
import com.example.data.model.ChatboxCallEntry
import com.example.data.model.ChatboxConstants
import com.example.data.model.ChatboxMessage
import com.example.data.model.ChatboxMessageKind
import com.example.data.model.ChatboxThread
import kotlin.math.abs

object ChatboxCatalog {
    val defaultThreads: List<ChatboxThread> = listOf(
        ChatboxThread(
            id = "sazzad",
            name = "Sazzad Jony",
            shortId = "739204",
            phoneNumber = "+8801711002233",
            role = "Active now",
            connectionLabel = "You're connected on Chatbox",
            locationText = "Lives in Dhaka, Bangladesh",
            workBio = "Works at Marino Royal Hotel, Royal Park Hotel Dhaka",
            avatarLabel = "SJ",
            style = AvatarStyle.BLUE,
            online = true,
            readReceiptsEnabled = true,
            historyLocked = true
        ),
        ChatboxThread(
            id = "saira",
            name = "Saira",
            shortId = "100001",
            role = "Your app companion",
            connectionLabel = "Your personal Chatbox AI assistant",
            locationText = "Always available",
            workBio = "Instant AI help, Bengali & English greetings, and auto-reply rules",
            avatarLabel = "✳",
            style = AvatarStyle.SAIRA,
            label = "AI GUIDE",
            online = true,
            readReceiptsEnabled = true
        ),
        ChatboxThread(
            id = "maya",
            name = "Maya Rahman",
            shortId = "528419",
            phoneNumber = "+8801812445566",
            role = "Active now",
            connectionLabel = "You're connected on Chatbox",
            locationText = "Lives in Chattogram, Bangladesh",
            workBio = "Product Designer · Coffee lover",
            avatarLabel = "MR",
            style = AvatarStyle.PEACH,
            online = true,
            unreadCount = 1,
            readReceiptsEnabled = true
        ),
        ChatboxThread(
            id = "arif",
            name = "Arif Hasan",
            shortId = "641930",
            phoneNumber = "+8801913778899",
            role = "Active now",
            connectionLabel = "You're connected on Chatbox",
            locationText = "Lives in Sylhet, Bangladesh",
            workBio = "Android Engineer & Photography",
            avatarLabel = "AH",
            style = AvatarStyle.LAVENDER,
            online = true,
            readReceiptsEnabled = true
        ),
        ChatboxThread(
            id = "nora",
            name = "Nora Ahmed",
            shortId = "319842",
            role = "Recently active",
            connectionLabel = "You're connected on Chatbox",
            locationText = "Lives in Dhaka, Bangladesh",
            workBio = "Content Creator",
            avatarLabel = "NA",
            style = AvatarStyle.YELLOW,
            online = false,
            readReceiptsEnabled = true
        ),
        ChatboxThread(
            id = "together",
            name = "The good people",
            shortId = "884120",
            role = "Group · 4 members",
            connectionLabel = "Group conversation on Chatbox",
            locationText = "Dhaka & Sylhet",
            workBio = "Friends hangout & weekend plans",
            avatarLabel = "TG",
            style = AvatarStyle.BLUE,
            label = "GROUP",
            online = true,
            isGroup = true,
            members = listOf("Sazzad Jony", "Maya Rahman", "Arif Hasan"),
            readReceiptsEnabled = true
        )
    )

    val defaultSeeds: Map<String, List<ChatboxMessage>> = mapOf(
        "sazzad" to listOf(
            ChatboxMessage(
                id = "sz_1",
                from = "them",
                senderName = "Sazzad Jony",
                text = "kire asoc",
                time = "10:52 AM",
                kind = ChatboxMessageKind.TEXT
            ),
            ChatboxMessage(
                id = "sz_2",
                from = "them",
                senderName = "Sazzad Jony",
                text = "Missed audio call",
                time = "06/11/2026",
                kind = ChatboxMessageKind.MISSED_AUDIO_CALL
            )
        ),
        "saira" to listOf(
            ChatboxMessage(
                id = "sr_1",
                from = "them",
                senderName = "Saira",
                text = "হ্যালো! আমি Saira ✨ এই অ্যাপ সম্পর্কে কিছু জানতে চাইলে বা যেকোনো সাহায্য চাইলে আমাকে জিজ্ঞেস করুন।",
                time = "এখন",
                kind = ChatboxMessageKind.TEXT
            )
        ),
        "maya" to listOf(
            ChatboxMessage(id = "my_1", from = "them", senderName = "Maya Rahman", text = "Hey! How has your week been going? ☀️", time = "10:24 AM"),
            ChatboxMessage(id = "my_2", from = "me", senderName = "You", text = "Pretty good, actually! Just taking things one day at a time.", time = "10:26 AM"),
            ChatboxMessage(id = "my_3", from = "them", senderName = "Maya Rahman", text = "Love that for you. We should catch up soon!", time = "10:28 AM"),
            ChatboxMessage(id = "my_4", from = "them", senderName = "Maya Rahman", text = "Maybe a little coffee date this weekend? ☕", time = "10:28 AM")
        ),
        "arif" to listOf(
            ChatboxMessage(id = "ar_1", from = "them", senderName = "Arif Hasan", text = "Let’s build something great together ✨", time = "Yesterday")
        ),
        "nora" to listOf(
            ChatboxMessage(id = "nr_1", from = "them", senderName = "Nora Ahmed", text = "Sending you a little sunshine today!", time = "Yesterday")
        ),
        "together" to listOf(
            ChatboxMessage(id = "tg_1", from = "them", senderName = "Maya Rahman", text = "A little space for our favorite people 💛", time = "Monday")
        )
    )

    val archivedHistorySeeds: Map<String, List<ChatboxMessage>> = mapOf(
        "sazzad" to listOf(
            ChatboxMessage(
                id = "sz_arch_1",
                from = "them",
                senderName = "Sazzad Jony",
                text = "Bhai ajke free achos?",
                time = "04/11/2026",
                isRestoredHistory = true
            ),
            ChatboxMessage(
                id = "sz_arch_2",
                from = "me",
                senderName = "You",
                text = "Ha bhai, evening a kotha hobe!",
                time = "04/11/2026",
                isRestoredHistory = true
            )
        )
    )

    val defaultCallLogs: List<ChatboxCallEntry> = listOf(
        ChatboxCallEntry(
            id = "call_seed_1",
            threadId = "sazzad",
            peerName = "Sazzad Jony",
            isVideo = false,
            isMissed = true,
            timeLabel = "06/11/2026",
            durationLabel = "Missed"
        )
    )

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
