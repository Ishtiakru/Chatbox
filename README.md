# Chatbox (Android — Kotlin & Jetpack Compose)

A native Android rewrite of **Chatbox** (`Saira / Chatbox`) built with Kotlin, Jetpack Compose (Material 3), Firebase Auth (Credential Manager Google Sign-In), Cloud Firestore, Firebase AI Logic (`gemini-3.8-flash`) with App Check, and Jetpack DataStore.

## Features

- **Lavender Login & Bilingual Greeting**: `বাংলা (BD)` / `English (US)` toggle, Google Sign-In via Jetpack Credential Manager, and Guest Demo mode.
- **Demo Conversations**: Local sample chats (`Saira` offline Bengali/English guide, `Maya Rahman`, `Arif Hasan`, `Nora Ahmed`, `The good people`) persisted in DataStore, with search, emoji insertion, and adaptive 1/2/3-pane layouts.
- **Live Chats (Firestore)**: Real-time 1:1 conversations (`profiles`, `conversations`, `messages`, `userBlocks`) with Chatbox ID sharing, consent-based message requests (`pending` / `active` / `closed`), server pagination (`Load earlier messages`), per-conversation in-memory drafts, and peer blocking.
- **Saira AI (Firebase AI Logic)**: Streaming Q&A grounded in curated Chatbox app knowledge using `gemini-3.8-flash` and Firebase App Check.
- **Calls (Planned)**: Orbital calling preview screen linking back to active conversations.
- **Spark Admin Studio**: Owner/delegate controls secured by Firestore security rules and recent Google re-authentication (`welcome:`, `theme: lavender/default`, `menu: top/bottom`, and Gmail delegation).

## Security Rules & Testing

- `firestore.rules` enforces zero-trust access control across all collections with a catch-all deny.
- Verify rules locally against the Firestore emulator:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" NODE_PATH=/usr/local/lib/node_modules node --test firestore.test.cjs
  ```
