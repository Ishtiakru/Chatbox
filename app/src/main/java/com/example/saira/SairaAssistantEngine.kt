package com.example.saira

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.repository.SairaKnowledge
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

@Serializable
enum class SairaPlatform(val label: String) {
    ANY("WhatsApp & Messenger"),
    WHATSAPP("WhatsApp"),
    MESSENGER("Messenger")
}

@Serializable
data class SairaNotificationRule(
    val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val platform: SairaPlatform = SairaPlatform.ANY,
    val taughtReplyGuidance: String,
    val enabled: Boolean = true
)

data class SairaCompanionMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

object SairaAssistantEngine {
    private const val TAG = "SairaAssistant"
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val QUICK_AI_TIMEOUT_MS = 18_000L

    val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    val MESSENGER_PACKAGES = setOf("com.facebook.orca", "com.facebook.mlite")

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(
                    """
                    You are Saira Assistant (সায়রা অ্যাসিস্ট্যান্ট), a friendly, intelligent, free and unlimited personal AI companion inside Chatbox.

                    Core Behavior Rules:
                    1. Never initiate a conversation before the user messages first. Only reply to messages sent to you.
                    2. Always respond naturally in the exact language/style the user writes in (Bengali, Banglish, or English).
                    3. Greeting Rule:
                       - When the prompt marks this as the first message in a conversation, begin warmly with the provided time-of-day greeting (Good Morning, Good Afternoon, or Good Evening — or a natural bilingual greeting like "Good Evening!" alongside Bengali if the user writes in Bengali).
                       - When the user is ending the conversation or saying goodbye/thank you/done at night (night flag = true), gracefully wish them "Good Night" (e.g., "Good Night! শুভ রাত্রি।") as a warm closing.
                    4. Unlimited General Knowledge:
                       - Answer ANY question or request the user brings (everyday help, study, technology, coding, writing, advice, general facts, or Chatbox app guidance) clearly, accurately, and warmly.
                    5. Ground Chatbox-specific questions in this app summary when asked about Chatbox:
                    ${SairaKnowledge.CURATED_TEXT}
                    """.trimIndent()
                )
            }
        )
    }

    private val replyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                maxOutputTokens = 256
            },
            systemInstruction = content {
                text(
                    "You are Saira Assistant writing concise, natural auto-replies on the user's behalf. " +
                        "Match the language of the incoming message (Bengali, Banglish, or English). " +
                        "Keep replies warm, polite, 1 to 3 sentences, and follow the exact instruction/context provided."
                )
            }
        )
    }

    @Volatile
    private var activeChatSession: Chat? = null

    fun resetSession() {
        activeChatSession = null
    }

    fun currentTimeGreeting(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        return when (hourOfDay) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    fun isNightTime(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean {
        return hourOfDay >= 21 || hourOfDay < 5
    }

    fun askStream(question: String, isFirstTurn: Boolean): Flow<String> {
        val trimmed = question.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 2000) {
            "Message must be between 1 and 2000 characters."
        }
        val session = activeChatSession ?: chatModel.startChat().also { activeChatSession = it }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = currentTimeGreeting(hour)
        val isNight = isNightTime(hour)

        val contextualPrompt = buildString {
            append("[Context: localTimeGreeting=\"$greeting\", isNightTime=$isNight, isFirstTurn=$isFirstTurn]")
            if (isFirstTurn) {
                append(" Start your reply warmly with \"$greeting\" suited to the user's language.")
            }
            if (isNight) {
                append(" If the user is ending the conversation or saying goodbye/thanks/goodnight, include a warm Good Night farewell.")
            }
            append("\nUser message: ")
            append(trimmed)
        }

        return session.sendMessageStream(contextualPrompt)
            .mapNotNull { chunk -> chunk.text }
    }

    suspend fun askCompanionInThread(
        userName: String,
        peerName: String,
        userQuestion: String
    ): String {
        val cleanUser = userName.trim().ifEmpty { "Friend" }
        val cleanPeer = peerName.trim().ifEmpty { "your friend" }
        val greeting = currentTimeGreeting()
        val isNight = isNightTime()
        val prompt = """
            User "$cleanUser" is currently waiting for "$cleanPeer" in Chatbox and just wrote to you (Saira Assistant):
            "$userQuestion"
            Current greeting: "$greeting", isNightTime=$isNight.
            Reply directly and helpfully to "$cleanUser" in the same language they wrote in.
        """.trimIndent()
        return runCatching {
            withTimeout(QUICK_AI_TIMEOUT_MS) {
                replyModel.generateContent(prompt).text?.trim().orEmpty()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Thread companion reply failed", error)
        }.getOrNull().orEmpty().ifBlank {
            if (containsBengali(userQuestion)) {
                "$greeting $cleanUser! আমি সায়রা অ্যাসিস্ট্যান্ট। \"$userQuestion\" সম্পর্কে এখনই পূর্ণ উত্তর দিতে পারিনি, একটু পরে আবার চেষ্টা করুন।"
            } else {
                "$greeting $cleanUser! I'm Saira Assistant. Please try asking again in a moment."
            }
        }
    }

    suspend fun generateChatboxOneMinuteReply(
        senderName: String,
        peerName: String,
        lastMessageText: String,
        isSenderWaitingOnPeer: Boolean
    ): String {
        val cleanSender = senderName.trim().ifEmpty { "Friend" }
        val cleanPeer = peerName.trim().ifEmpty { "your friend" }
        val greeting = currentTimeGreeting()
        val isNight = isNightTime()
        val useBengali = containsBengali(lastMessageText) || containsBengali(cleanSender)

        val prompt = if (isSenderWaitingOnPeer) {
            """
            In Chatbox, "$cleanSender" sent "$lastMessageText" to "$cleanPeer", and "$cleanPeer" has not replied for 1 minute (likely offline or busy).
            Write a friendly 1-2 sentence message as Saira Assistant directly to "$cleanSender":
            - Start with "$greeting $cleanSender"
            - Kindly let $cleanSender know that $cleanPeer hasn't replied for 1 minute / may be offline right now, and ask how you (Saira Assistant) can help in the meantime.
            - Match language: ${if (useBengali) "Bengali (natural conversational Bangla)" else "English or user's message language"}.
            - isNightTime=$isNight
            """.trimIndent()
        } else {
            """
            In Chatbox, "$cleanSender" sent "$lastMessageText" to "$cleanPeer", and "$cleanPeer" has been away for 1 minute.
            Write a polite 1-2 sentence auto-reply as Saira Assistant on $cleanPeer's behalf:
            - Start with "$greeting $cleanSender"
            - Let $cleanSender know $cleanPeer is away right now and will reply as soon as possible.
            - Match language: ${if (useBengali) "Bengali (natural conversational Bangla)" else "English or user's message language"}.
            """.trimIndent()
        }

        val generated = runCatching {
            withTimeout(QUICK_AI_TIMEOUT_MS) {
                replyModel.generateContent(prompt).text?.trim().orEmpty()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "1-minute auto-reply fallback triggered", error)
        }.getOrNull().orEmpty()

        if (generated.isNotBlank()) return generated

        return if (isSenderWaitingOnPeer) {
            if (useBengali) {
                "$greeting $cleanSender, কেমন আছো? $cleanPeer ১ মিনিট ধরে রিপ্লাই দেয়নি বা এখন অফলাইনে আছে। অপেক্ষা করার সময় আমি সায়রা অ্যাসিস্ট্যান্ট তোমাকে যেকোনো তথ্যে সাহায্য করতে পারি!"
            } else {
                "$greeting $cleanSender, how are you? $cleanPeer hasn't replied for 1 minute and may be offline right now. I'm Saira Assistant — feel free to ask me anything while you wait!"
            }
        } else {
            if (useBengali) {
                "[Saira Assistant] $greeting $cleanSender! $cleanPeer এই মুহূর্তে ব্যস্ত বা দূরে আছেন। তিনি ফিরে এলেই উত্তর দেবেন।"
            } else {
                "[Saira Assistant] $greeting $cleanSender! $cleanPeer is currently away and will reply as soon as they return."
            }
        }
    }

    fun detectPlatform(packageName: String): SairaPlatform? = when (packageName) {
        in WHATSAPP_PACKAGES -> SairaPlatform.WHATSAPP
        in MESSENGER_PACKAGES -> SairaPlatform.MESSENGER
        else -> null
    }

    fun matchNotificationRule(
        packageName: String,
        senderTitle: String,
        conversationTitle: String?,
        rules: List<SairaNotificationRule>
    ): SairaNotificationRule? {
        val platform = detectPlatform(packageName) ?: return null
        val haystack = buildString {
            append(senderTitle.trim())
            if (!conversationTitle.isNullOrBlank()) {
                append(' ')
                append(conversationTitle.trim())
            }
        }.lowercase()
        if (haystack.isBlank()) return null

        return rules.firstOrNull { rule ->
            rule.enabled &&
                rule.contactName.trim().isNotEmpty() &&
                (rule.platform == SairaPlatform.ANY || rule.platform == platform) &&
                haystack.contains(rule.contactName.trim().lowercase())
        }
    }

    suspend fun generateTargetedNotificationReply(
        rule: SairaNotificationRule,
        senderName: String,
        incomingMessage: String
    ): String {
        val greeting = currentTimeGreeting()
        val isNight = isNightTime()
        val guidance = rule.taughtReplyGuidance.trim()
        val prompt = """
            You are Saira Assistant replying to an incoming ${rule.platform.label} notification from "$senderName".
            Incoming message: "$incomingMessage"
            Owner's exact taught instruction for "$senderName": "$guidance"
            Time greeting: "$greeting", isNightTime=$isNight.
            Write a polite, natural 1-2 sentence reply strictly following the owner's taught instruction, starting with a brief greeting ($greeting) if appropriate.
            Match the language of the incoming message or the owner's instruction. Output ONLY the reply message text.
        """.trimIndent()

        val generated = runCatching {
            withTimeout(QUICK_AI_TIMEOUT_MS) {
                replyModel.generateContent(prompt).text?.trim().orEmpty()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Notification reply AI fallback for ${rule.contactName}", error)
        }.getOrNull().orEmpty()

        return generated.ifBlank {
            if (guidance.isNotBlank()) "$greeting! $guidance" else "$greeting!"
        }
    }

    fun dispatchNotificationReply(
        context: Context,
        sbn: StatusBarNotification,
        replyText: String
    ): Boolean {
        val actions = sbn.notification.actions ?: return false
        val replyAction = actions.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true && action.actionIntent != null
        } ?: return false
        val remoteInputs = replyAction.remoteInputs ?: return false

        return runCatching {
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput: RemoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            replyAction.actionIntent.send(context, 0, intent)
            true
        }.onFailure { error ->
            Log.w(TAG, "Failed dispatching RemoteInput reply for ${sbn.packageName}", error)
        }.getOrDefault(false)
    }

    private fun containsBengali(text: String): Boolean {
        return text.any { ch -> ch in '\u0980'..'\u09FF' }
    }
}
