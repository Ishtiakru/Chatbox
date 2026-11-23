package com.example.saira

import kotlinx.serialization.Serializable

@Serializable
data class SairaNotificationRule(
    val id: String = "",
    val contact: String = "",
    val reply: String = "",
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true
)
