package com.example.data.repository

object SairaKnowledge {
    const val CURATED_TEXT = """
# Saira's curated app knowledge

Chatbox is a mobile-first Android application built with Kotlin, Jetpack Compose, Material 3, Firebase Authentication (Google Sign-In), Cloud Firestore, and Firebase AI Logic.

Features available in this Android app:
- Lavender Login & Welcome Screen with Bangla (বাংলা) and English (US) language switching, Google Sign-In via Credential Manager, and an offline Demo explorer mode.
- Demo Conversations: Local sample conversations (Saira offline guide, Maya Rahman, Arif Hasan, Nora Ahmed, The good people) with local-only search and persistent DataStore message history on this device.
- Live Chats: Real-time direct messaging powered by Cloud Firestore. Users share their Chatbox ID (Firebase UID) privately to send conversation requests, accept or decline incoming requests, exchange real-time messages with pagination for earlier messages, close conversations, and block/unblock peers. Display names are unverified and messages are not end-to-end encrypted.
- Saira AI: Conversational assistant powered by Firebase AI Logic (Gemini) and Firebase App Check, grounded in this curated app knowledge. Only the submitted question is sent; chat history from other conversations is never shared.
- Calls: Shows the planned Voice and Video Calls screen. Real-time audio/video calling is not connected in this build; it requires a signaling and media relay (TURN) backend.
- Spark Admin Studio: Owner and delegate console protected by Firestore security rules and recent Google account verification. Supports deterministic commands to preview and update the public welcome banner, bounded home appearance theme (default or lavender), and mobile navigation menu position (bottom or top), plus managing Gmail delegate invitations.

Important notes:
- Live conversations and Saira AI require signing in with Google.
- Do not share sensitive or private information in experimental chats or AI queries.
"""
}
