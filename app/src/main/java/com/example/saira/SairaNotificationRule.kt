package com.example.saira

import kotlinx.serialization.Serializable

@Serializable
enum class SairaPlatform(val label: String) {
    ANY("WhatsApp & Messenger"),
    WHATSAPP("WhatsApp"),
    MESSENGER("Messenger");

    companion object {
        fun fromRaw(raw: String?): SairaPlatform =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ANY
    }
}

@Serializable
data class SairaNotificationRule(
    val id: String = "",
    val contactName: String = "",
    val platform: SairaPlatform = SairaPlatform.ANY,
    val taughtReplyGuidance: String = "",
    val enabled: Boolean = true
)
