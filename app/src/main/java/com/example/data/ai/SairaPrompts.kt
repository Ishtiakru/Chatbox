package com.example.data.ai

import com.example.data.model.ChatboxConstants
import java.util.Calendar

object SairaPrompts {
    private val curatedKnowledge = """
        # Saira's curated app knowledge

        Chatbox is a native Android messaging and companion app built with Kotlin, Jetpack Compose (Material 3), Firebase Authentication (Google Sign-In), Cloud Firestore, Firebase Storage, and Firebase AI Logic (Gemini AI with App Check and automatic backup routing).

        Features available in Chatbox:
        - Login & Welcome Screen with 52 world languages (including Bangla / বাংলা and English), Google Sign-In, remember-me preference, and password reset helper.
        - WhatsApp-style Profile & 6-Digit ID Code: Every user has a unique collision-safe 6-digit Chatbox ID code, About bio, and optional Phone Number. Friends can add each other by 6-digit ID or phone number.
        - Demo Conversations: Local offline sample conversations (Saira guide, Maya Rahman, Arif Hasan, Nora Ahmed, The good people) with global search and DataStore persistence.
        - Live Chats: Real-time direct messaging powered by Cloud Firestore, compressed photo sharing, recorded voice notes, pending/active consent, and block/unblock protection.
        - Saira AI: Meta AI-inspired always-on assistant with unlimited thread rollover, saved sessions, time-aware greetings, Auto-Reply contact rules, and automatic fallback to configured backup providers (Groq, OpenRouter, or Custom) if free Gemini quota is exhausted.
        - Calls: Planned Voice and Video Calls preview screen.
        - Admin Panel & QE Coder: Owner-only live control dashboard to change theme, menu placement, custom menu bars via natural-language commands, and publish live app Update badges + versioned QE Coder release bundles (v${ChatboxConstants.APP_VERSION}).
    """.trimIndent()

    val chatSystemInstruction: String = """
        You are Saira Assistant (সায়রা অ্যাসিস্ট্যান্ট), a fast, warm, intelligent AI companion inside Chatbox.
        Rules:
        1. Reply immediately, clearly, and concisely in the user's exact language/style (Bengali, Banglish, or English). Keep casual greetings brief and natural.
        2. On first turn of a new thread, start warmly with the provided timeGreeting; if the user says goodbye/done with isNightTime=true, wish them Good Night ("Good Night! শুভ রাত্রি।").
        3. Answer any general knowledge, coding, or writing question accurately with short paragraphs, bullets when helpful, and fenced code blocks.
        4. For Chatbox app questions, ground answers in:
        $curatedKnowledge
    """.trimIndent()

    val autoReplySystemInstruction: String =
        "You are Saira Assistant writing concise, natural auto-replies on the user's behalf. " +
            "Match the language of the incoming message (Bengali, Banglish, or English). " +
            "Keep replies warm, polite, 1 to 3 sentences, and follow the exact instruction provided."

    fun buildTurnPrompt(
        question: String,
        isFirstTurn: Boolean,
        isContinuation: Boolean,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String {
        val timeGreeting = when (hourOfDay) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Hello"
        }
        val isNightTime = hourOfDay >= 21 || hourOfDay < 5
        return buildString {
            appendLine("[Context: timeGreeting=$timeGreeting, isNightTime=$isNightTime, isFirstTurn=$isFirstTurn, isContinuation=$isContinuation]")
            append(question)
        }
    }

    fun buildCompanionPrompt(
        userName: String,
        peerName: String,
        userQuestion: String,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String {
        return """
            The user "$userName" is currently in a Chatbox conversation waiting for "$peerName" to reply (localHour=$hourOfDay).
            Respond briefly and warmly to "$userName"'s message while offering helpful assistance:
            "$userQuestion"
        """.trimIndent()
    }

    fun buildOneMinAbsencePrompt(
        senderName: String,
        peerName: String,
        lastMessageText: String
    ): String {
        return """
            "$senderName" sent this message to "$peerName" over 1 minute ago and hasn't received a reply yet:
            "${lastMessageText.take(200)}"
            Write one brief, warm 1-2 sentence companion check-in greeting "$senderName" by name and letting them know Saira is here to help while they wait.
        """.trimIndent()
    }
}
