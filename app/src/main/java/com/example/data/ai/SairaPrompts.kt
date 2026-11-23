package com.example.data.ai

import com.example.data.model.ChatboxConstants
import com.example.saira.SairaTimeGreeting
import java.util.Calendar

object SairaPrompts {
    private val curatedKnowledge = """
        # Saira's curated app knowledge

        Chatbox is a native Android messaging and companion app built with Kotlin, Jetpack Compose (Material 3), Firebase Authentication (Google Sign-In), Cloud Firestore, Firebase Storage, and Firebase AI Logic (Gemini AI with App Check and automatic backup routing).

        Features available in Chatbox:
        - Login & Welcome Screen with 52 world languages (including Bangla / বাংলা and English), Google Sign-In, remember-me preference, and password reset helper.
        - WhatsApp-style Profile & 6-Digit ID Code: Every user has a unique collision-safe 6-digit Chatbox ID code, About bio, and optional Phone Number. Friends can add each other by 6-digit ID or phone number.
        - Demo Conversations: Local offline sample conversations (Saira guide, Maya Rahman, Arif Hasan, Nora Ahmed, The good people) with global search and DataStore persistence.
        - Live Chats: Real-time direct messaging powered by Cloud Firestore, compressed photo sharing, recorded voice notes, permission- & consent-gated Live Location sharing, pending/active consent, and block/unblock protection.
        - Saira AI: 4 dedicated capabilities: (1) 10-second Chatbox absence check-in addressing the sender by name with time-aware greetings, (2) permission-gated WhatsApp & Messenger notification auto-reply for taught contacts (stepping in after 10 seconds if unanswered and continuing naturally multi-turn), (3) permission- & contact-gated Live Location sharing, and (4) time-aware greetings with free Gemini AI + automatic backup provider failover.
        - Calls: Planned Voice and Video Calls preview screen.
        - Admin Panel & QE Coder: Owner-only live control dashboard to change theme, menu placement, custom menu bars via natural-language commands, and publish live app Update badges + versioned QE Coder release bundles (v${ChatboxConstants.APP_VERSION}).
    """.trimIndent()

    val chatSystemInstruction: String = """
        You are Saira Assistant (সায়রা অ্যাসিস্ট্যান্ট), a fast, warm, intelligent AI companion inside Chatbox.
        Rules:
        1. Reply immediately, clearly, and concisely in the user's exact language/style (Bengali, Banglish, or English). Keep casual greetings brief and natural.
        2. On first turn of a new thread, start warmly with the provided timeGreeting / bengaliTimeGreeting; if the user says goodbye/done with isNightTime=true, wish them Good Night ("Good Night! শুভ রাত্রি।").
        3. Answer any general knowledge, coding, or writing question accurately with short paragraphs, bullets when helpful, and fenced code blocks.
        4. For Chatbox app questions, ground answers in:
        $curatedKnowledge
    """.trimIndent()

    val autoReplySystemInstruction: String =
        "You are Saira Assistant carrying on a warm, natural multi-turn auto-reply conversation on the user's behalf. " +
            "Match the language of the incoming message (Bengali, Banglish, or English). " +
            "On the first message of a conversation, greet the sender warmly with the provided time-of-day greeting and share the user's taught context. " +
            "On follow-up messages (when conversation history already exists), DO NOT repeat the time-of-day greeting or copy-paste the introductory status line again — respond directly and naturally to what the sender just said, using prior messages for context so the conversation flows smoothly like a real companion in 1 to 3 sentences. " +
            "If the sender asks about a known Chatbox user, answer briefly and naturally using only the provided Chatbox user list (name, ID, about). " +
            "Never invent or guess any user detail, and never share phone numbers, emails or other private contact information. " +
            "If the Chatbox user list is empty, do not mention or reference it at all."

    fun buildTurnPrompt(
        question: String,
        isFirstTurn: Boolean,
        isContinuation: Boolean,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String {
        val info = SairaTimeGreeting.infoForHour(hourOfDay)
        return buildString {
            appendLine("[Context: timeGreeting=${info.englishGreeting}, bengaliTimeGreeting=${info.bengaliGreeting}, isNightTime=${info.isNightTime}, isFirstTurn=$isFirstTurn, isContinuation=$isContinuation]")
            append(question)
        }
    }

    fun buildCompanionPrompt(
        userName: String,
        peerName: String,
        userQuestion: String,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): String {
        val info = SairaTimeGreeting.infoForHour(hourOfDay)
        return """
            The user "$userName" is currently in a Chatbox conversation waiting for "$peerName" to reply (timeGreeting=${info.bengaliGreeting}, localHour=$hourOfDay).
            Respond briefly and warmly to "$userName"'s message while offering helpful assistance:
            "$userQuestion"
        """.trimIndent()
    }

    fun buildOneMinAbsencePrompt(
        senderName: String,
        peerName: String,
        lastMessageText: String,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        isContinuation: Boolean = false
    ): String {
        val info = SairaTimeGreeting.infoForHour(hourOfDay)
        return if (isContinuation) {
            """
            You are continuing a live Chatbox conversation while waiting for "$peerName" to reply.
            The latest message from "$senderName" is:
            "${lastMessageText.take(250)}"
            Reply naturally and helpfully in 1-2 short sentences matching the language (Bengali, Banglish, or English) without repeating the initial greeting.
            """.trimIndent()
        } else {
            """
            "$senderName" sent a message to "$peerName" in Chatbox and hasn't received a reply within 10 seconds:
            "${lastMessageText.take(200)}"
            Start directly by addressing "$senderName" by name in Bengali/English matching the message (for example: "হ্যালো $senderName! ${info.bengaliGreeting} ✨ ..."), acknowledge the time-of-day greeting (${info.bengaliGreeting} / ${info.englishGreeting}), and warmly offer to talk or answer questions while waiting for "$peerName". Keep it to 1-2 short sentences.
            """.trimIndent()
        }
    }

    fun buildNotificationAutoReplyPrompt(
        senderName: String,
        platformLabel: String,
        incomingMessage: String,
        taughtGuidance: String,
        locationText: String?,
        chatboxUsers: List<com.example.saira.SairaDirectoryUser> = emptyList(),
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        isContinuation: Boolean = false
    ): String {
        val info = SairaTimeGreeting.infoForHour(hourOfDay)
        return buildString {
            appendLine("Platform: $platformLabel")
            appendLine("Sender Name: $senderName")
            appendLine("Time-of-day greeting: ${info.bengaliGreeting} (${info.englishGreeting})")
            appendLine(
                "Conversation state: " + if (isContinuation) {
                    "ONGOING MULTI-TURN CHAT — Do NOT repeat 'হ্যালো $senderName, ${info.bengaliGreeting}' or repeat your previous reply word-for-word. Reply naturally and directly to this new message to keep the conversation going."
                } else {
                    "FIRST MESSAGE — Greet $senderName warmly with '${info.bengaliGreeting}' and incorporate the user's taught instruction naturally."
                }
            )
            appendLine("User's taught rule / persona instruction: $taughtGuidance")
            if (!locationText.isNullOrBlank()) {
                appendLine("Approved Live Location to include verbatim: $locationText")
            } else {
                appendLine("Live Location sharing is NOT permitted for this reply — do not invent or share any address or coordinates.")
            }
            val selfEntry = chatboxUsers.firstOrNull { it.isSelf }
            if (selfEntry != null) {
                appendLine(
                    "You are replying on behalf of ${selfEntry.name} " +
                        "(Chatbox ID ${selfEntry.shortId}, about ${selfEntry.about.ifBlank { "not set" }})."
                )
            }
            if (chatboxUsers.isNotEmpty()) {
                appendLine("Known Chatbox users (use only these, never invent anyone):")
                chatboxUsers.forEach { user ->
                    appendLine(
                        "- ${user.name} — ID ${user.shortId} — about ${user.about.ifBlank { "not set" }}"
                    )
                }
            } else {
                appendLine("Known Chatbox users: none available.")
            }
            appendLine(
                "Use ONLY the known Chatbox user list above when answering questions about Chatbox users. " +
                    "If someone asks about a user who is not in the list, say you do not have that information — never make up names, IDs or details. " +
                    "Never share any phone number, email or other private contact information of any user."
            )
            appendLine("Latest incoming message from $senderName: \"${incomingMessage.take(300)}\"")
            append("Write the next 1-3 sentence conversational reply now.")
        }
    }
}
