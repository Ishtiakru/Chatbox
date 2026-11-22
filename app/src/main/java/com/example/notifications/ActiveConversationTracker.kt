package com.example.notifications

object ActiveConversationTracker {
    @Volatile
    var isLiveChatsVisible: Boolean = false

    @Volatile
    var activeConversationId: String? = null
}
