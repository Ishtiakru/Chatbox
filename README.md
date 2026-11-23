# Chatbox (Android — Kotlin & Jetpack Compose)

A native Android rewrite of **Chatbox** (`Saira / Chatbox`) built with Kotlin, Jetpack Compose (Material 3), Firebase Auth (Credential Manager Google Sign-In), Cloud Firestore, Firebase AI Logic (`gemini-3.8-flash`) with App Check, and Jetpack DataStore.

## Features

- **Lavender Login & Bilingual Greeting**: `বাংলা (BD)` / `English (US)` toggle and Google Sign-In via Jetpack Credential Manager.
- **Live Chats (Firestore)**: Real-time 1:1 conversations (`profiles`, `conversations`, `messages`, `userBlocks`) with Chatbox ID sharing, consent-based message requests (`pending` / `active` / `closed`), server pagination (`Load earlier messages`), per-conversation in-memory drafts, and peer blocking.
- **Push Notifications (FCM)**: Android client registers device FCM tokens in owner-private `userFcmTokens/{ownerId}/devices/{deviceId}`, requests `POST_NOTIFICATIONS` on Android 13+, suppresses foreground banners for the active open thread, and deep-links tapped notifications into the conversation.
- **Saira AI (Firebase AI Logic)**: Streaming Q&A grounded in curated Chatbox app knowledge using `gemini-3.8-flash` and Firebase App Check. When the free Gemini quota runs out, Saira automatically switches to a user-configured backup provider (Groq / OpenRouter / any OpenAI-compatible endpoint) and cools the exhausted provider down for 5 minutes.
- **Signed release builds**: the release APK is signed with the committed `release.keystore` (`keystore.properties`), so it is installable and not debuggable.
- **Calls (Planned)**: Orbital calling preview screen linking back to active conversations.
- **Spark Admin Studio**: Owner/delegate controls secured by Firestore security rules and recent Google re-authentication (`welcome:`, `theme: lavender/default`, `menu: top/bottom`, and Gmail delegation).

## Firebase Authentication setup

Phone and Google sign-in need two one-time Firebase Console steps:

1. **Enable Phone sign-in:** Firebase Console → Build → Authentication → Sign-in method → **Phone** → Enable. Optionally add a test phone number with a fixed code.
2. **Add the debug signing certificate SHA-1** to the Android app `com.aistudio.chatbox.kxmpzq`: Firebase Console → Project settings → Your apps → Android app → **Add fingerprint**.
   ```
   F9:9C:68:B7:8F:28:34:54:39:71:48:83:59:25:DD:B3:0A:5C:07:20
   ```
   This is the SHA-1 of the committed `debug.keystore` used by CI debug builds. Without it, Google Sign-In fails with `DEVELOPER_ERROR`.

3. **Add the release signing certificate SHA-1** as well — release APKs are signed with `release.keystore`:
   ```
   E2:F4:A8:6C:4D:29:F3:03:7F:49:16:49:20:53:C8:2C:93:32:58:2A
   ```
   Add both fingerprints to the same Android app; debug and release builds then both pass Google Sign-In.

After changing either setting, install a freshly built APK.

## Backup AI setup (optional, recommended)

Gemini's free tier is rate limited, so Saira can automatically fail over to a second free provider:

1. Create a free API key — Groq: `console.groq.com/keys` · OpenRouter: `openrouter.ai/keys`.
2. In the app open **Saira → Auto-Reply → Backup AI**, pick the provider, paste the key, then **Save Backup AI**.
3. Nothing else to do: when Gemini answers with a quota error, the next message goes to the backup provider automatically. Exhausted providers are skipped for 5 minutes.

Provider defaults: Groq `llama-3.3-70b-versatile`, OpenRouter `meta-llama/llama-3.3-70b-instruct:free`. Model names change over time — edit the **Model** field if the provider retires a default. `Custom` accepts any OpenAI-compatible base URL (for example a self-hosted gateway).

Notes:

- The key is stored only in the app's private DataStore on the device. Anyone with the APK can still extract it, so use a free-tier key — never a paid one.
- Streaming is Gemini-only; backup answers arrive as one block.

## Release builds

`release-apk.yml` publishes `Chatbox-release.apk` signed with the committed `release.keystore` (alias `chatbox`, passwords in `keystore.properties`). Release builds are not debuggable, which also keeps Google Play Protect's install-time scan calm. If `keystore.properties` is missing, the workflow falls back to the `ANDROID_KEYSTORE_*` secrets, then to an unsigned APK.

Because the keystore is in the repository, anyone with write access can sign an update as this app. Rotate the key before publishing to Google Play.

## Notification access (opt-in)

The notification listener ships disabled in the manifest. It is enabled only when the user taps **Open Android Notification Access** in Saira → Auto-Reply, and Android still asks for explicit permission.

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
