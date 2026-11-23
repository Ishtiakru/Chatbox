# Chatbox (Android — Kotlin & Jetpack Compose)

A native Android rewrite of **Chatbox** (`Saira / Chatbox`) built with Kotlin, Jetpack Compose (Material 3), Firebase Auth (Credential Manager Google Sign-In), Cloud Firestore, Firebase AI Logic (`gemini-3.8-flash`) with App Check, and Jetpack DataStore.

## Features

- **Lavender Login & Bilingual Greeting**: `বাংলা (BD)` / `English (US)` toggle and Google Sign-In via Jetpack Credential Manager.
- **Live Chats (Firestore)**: Real-time 1:1 conversations (`profiles`, `conversations`, `messages`, `userBlocks`) with Chatbox ID sharing, consent-based message requests (`pending` / `active` / `closed`), server pagination (`Load earlier messages`), per-conversation in-memory drafts, and peer blocking.
- **Push Notifications (FCM)**: Android client registers device FCM tokens in owner-private `userFcmTokens/{ownerId}/devices/{deviceId}`, requests `POST_NOTIFICATIONS` on Android 13+, suppresses foreground banners for the active open thread, and deep-links tapped notifications into the conversation.
- **Saira AI (Firebase AI Logic)**: Streaming Q&A grounded in curated Chatbox app knowledge using `gemini-3.8-flash` and Firebase App Check.
- **Calls (Planned)**: Orbital calling preview screen linking back to active conversations.
- **Spark Admin Studio**: Owner/delegate controls secured by Firestore security rules and recent Google re-authentication (`welcome:`, `theme: lavender/default`, `menu: top/bottom`, and Gmail delegation).

## Backend Push Dispatch Setup (Firebase Console)

Because Cloud Functions / FCM server triggers cannot be provisioned automatically from this environment, deploy a Cloud Function (using Firebase Admin SDK) in your Firebase project triggered on new documents in `conversations/{conversationId}/messages/{messageId}` (on the named Firestore database) to look up recipient device tokens in `userFcmTokens/{recipientUid}/devices` and send FCM messages containing `conversationId`, `senderId`, `senderName`, and `body`.

## Firebase Configuration

`app/google-services.json` is committed so CI can build without extra setup. It targets the AI Studio Firebase project (`gen-lang-client-0251556445`) and the `com.aistudio.chatbox.kxmpzq` application ID. The release workflow prefers the `GOOGLE_SERVICES_JSON` (or `GOOGLE_SERVICES_JSON_BASE64`) repository secret when present, and falls back to the committed file. If AI Studio regenerates the Android app, download the fresh `google-services.json` from the AI Studio project and either replace the committed file or add it as the secret.

## Security Rules & Testing

- `firestore.rules` enforces zero-trust access control across all collections with a catch-all deny.
- Verify rules locally against the Firestore emulator:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" NODE_PATH=/usr/local/lib/node_modules node --test firestore.test.cjs
  ```
