# Chatbox (React + TypeScript + Express + Firebase)

A responsive messaging and companion web application for **Chatbox** (`Saira / Chatbox`), rewritten from the Android Kotlin/Compose codebase into React, TypeScript, Tailwind CSS, Vite, Express, Firebase Auth, Cloud Firestore, Firebase Storage, and server-side Gemini AI (`@google/genai`).

## Features

- **Lavender Auth & World Languages**: Launch splash screen, 52 world languages selector (`English (US)`, `বাংলা (বাংলাদেশ)`, and more), phone/password form preferences with "Remember me" & password reset helper, and Google Sign-In (`signInWithPopup`).
- **Demo Conversations**: Local sample chats (`Saira` offline Bengali/English guide, `Maya Rahman`, `Arif Hasan`, `Nora Ahmed`, `The good people`) persisted in `localStorage`, with live search highlighting, emoji insertion, and adaptive multi-pane layout.
- **Live Chats (Firestore & Storage)**: Real-time 1:1 conversations (`profiles`, `conversations`, `messages`, `userBlocks`) with Chatbox ID sharing, consent-based message requests (`pending` / `active` / `closed`), server pagination (`Load earlier messages`), browser-compressed image attachments, browser `MediaRecorder` voice notes with waveform playback, customizable quick reactions, thread search, and peer blocking.
- **Saira AI (`@google/genai`)**: Server-side streaming assistant (`/api/saira/chat`) using `gemini-4-hydrogen`, grounded in curated Chatbox knowledge with time-aware greetings (`Good Morning`/`Afternoon`/`Evening`/`Good Night`), plus Auto-Reply settings (`1-Min Absence Auto-Reply` in Live chats and taught contact rules).
- **Calls (Planned)**: Orbital calling preview screen linking back to active conversations.
- **Spark Admin Studio**: Owner and Gmail delegate controls protected by `firestore.rules` and recent Google re-authentication (`theme: lavender/default`, `menu: top/bottom`, `welcome:`, `update:` green badge notices, and Gmail delegation management).

## Running Locally

1. Copy `.env.example` to `.env` and set `GEMINI_API_KEY`.
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the full-stack development server (port `3000`):
   ```bash
   npm run dev
   ```
4. Build and lint:
   ```bash
   npm run lint
   npm run build
   ```
