# Chatbox (Native Android — Kotlin + Jetpack Compose + Firebase)

A native Android messaging and AI companion application for **Chatbox** (`Saira / Chatbox`), built with Kotlin, Jetpack Compose (Material 3), Navigation/Adaptive Window Size Classes, Firebase Authentication (Google Sign-In via Credential Manager), Cloud Firestore, Firebase Storage, Firebase AI Logic (with App Check & automatic OpenAI-compatible backup failover), and Spark Admin Studio.

## Features

- **Persistent Google Sign-In & World Languages**: Lavender launch experience, 52 world languages (`English (US)`, `বাংলা (বাংলাদেশ)`, and more), saved preferences via DataStore, and native Google Sign-In (`CredentialManager`) backed by a permanent keystore SHA setup so login stays stable across updates.
- **WhatsApp-Style Profile & 6-Digit IDs**: Automatic unique 6-digit Chatbox ID (`shortIds/{id}`), About bio, and private phone directory linking (`phoneDirectory/{digits}`, `users/{uid}/private/profile`) so users can connect by 6-digit ID, phone number, or UID.
- **Demo Conversations**: Local sample chats (`Saira` offline guide, `Maya Rahman`, `Arif Hasan`, `Nora Ahmed`, `The good people`) persisted in DataStore Preferences with live search, emoji quick-insert, and adaptive layout.
- **Live Chats (Firestore & Storage)**: Real-time 1:1 messaging (`conversations`, `messages`, `userBlocks`) with consent requests (`pending` / `active` / `closed`), pagination (`Load earlier messages`), compressed photo uploads, m4a voice notes with duration playback, custom reactions, and user blocking.
- **Always-On Saira AI (Meta AI Inspired UI)**: Animated multi-ring gradient orb, streaming Gemini responses via `Firebase.ai` (zero client API key), automatic 20-turn session rollover with Firestore history (`users/{uid}/sairaSessions`), live absence & contact auto-reply rules, and `AiRouter` automatic failover + cooldown tracking for backup OpenAI-compatible providers (Groq, OpenRouter, or custom).
- **Spark Admin Studio & QE Coder (Admin Only)**: Accessible exclusively to the owner Gmail and active `sparkDelegations`. Includes a natural-language Dashboard (EN/BN commands or Firebase AI structured JSON plan to add custom navigation tabs, change app title/welcome/theme/menu placement) and **QE Coder**, which packages a single latest release ZIP (auto-deleting previous ZIP files) and publishes the green `Update` badge pill beside the top Chatbox logo.

## Building & Testing Locally

1. Run unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
2. Build debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
3. Run Firestore security rules tests against emulator (`port 8085`):
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.cjs
   ```
