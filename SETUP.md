# Chatbox — Setup & Build Guide

This document exists so the project **never breaks again** for setup reasons.
Follow it once, and every future clone/build will work.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **21** | Android Studio Hedgehog+ bundles it; or install Temurin 21 |
| Android SDK | API **36**, build-tools **36.0.0** | Install via Android Studio SDK Manager or `sdkmanager` |
| Gradle | **8.13** (via wrapper) | No manual install needed — `./gradlew` downloads it |

> ⚠️ The repo ships a **Gradle wrapper** (`gradlew` + `gradle/wrapper/`).
> Always build with `./gradlew`, never with a system `gradle` — this pins the
> exact Gradle version the project was tested with.

---

## 2. Firebase — `app/google-services.json` (required)

The app uses Firebase (Auth, Firestore, AI Logic for Saira). The config file
**is intentionally NOT in git** (it contains project secrets — see `.gitignore`).

**One-time setup:**

> Chatbox-এর canonical Firebase project: **`gen-lang-client-0251556445`**
> (Project Number `631361942963`) — বিস্তারিত: [`FIREBASE_PROJECTS.md`](FIREBASE_PROJECTS.md)

1. Go to [Firebase Console](https://console.firebase.google.com) → select project **`gen-lang-client-0251556445`**.
2. Add an Android app with package name **`com.aistudio.chatbox.kxmpzq`**
   (this is the app's `applicationId` in `app/build.gradle.kts` — **not** `com.example`, which is only the code namespace).
3. Download **`google-services.json`** and place it at:
   ```
   app/google-services.json
   ```
4. Enable in Firebase Console:
   - **Authentication** → Google Sign-In (the app uses Credential Manager / Google)
   - **Firestore Database** → named database **`ai-studio-android-chatbox-f5f16960-82e9-4756-8ee4-5c3fe55bdd70`** (hardcoded in `firebase_applet_config.xml` — must exist with this exact ID)
   - **AI Logic** (for Saira's Gemini — free tier is enough)
   - **App Check**
5. Add the release SHA-1 fingerprint to the Android app
   (`e2:f4:a8:6c:4d:29:f3:83:7f:40:16:40:20:53:c8:2c:93:32:58:2a`) —
   required for Google Sign-In with the signed release APK.

**CI note:** GitHub Actions restores this file from the
`GOOGLE_SERVICES_JSON` (or `GOOGLE_SERVICES_JSON_BASE64`) repository secret —
see `.github/workflows/release-apk.yml`. If the secret is missing, CI fails
with a clear `::error::` message telling you exactly what to add.

---

## 3. Saira AI — how it never errors

Saira's AI layer (`app/src/main/java/com/example/data/ai/`) is designed so the
user **never sees a raw error**:

```
Gemini (primary, free via Firebase AI Logic)
   └─ quota/limit exhausted → cooldown 5 min
        └─ Backup API (Groq / OpenRouter / Custom) — auto-shift
             └─ still failing → friendly Bengali message (no error UI)
```

- **Primary:** Gemini through Firebase AI Logic — no API key needed, free tier.
- **Backup (recommended):** add a free API key in the app under
  **Saira → Auto-Reply settings** (Groq or OpenRouter). When Gemini's free
  quota runs out, Saira **automatically shifts** to the backup API — no restart,
  no user action.
- **Safety net:** if every provider is down/exhausted, `AiRouter` returns a
  friendly Bengali message instead of throwing (`neverThrow = true` in
  `SairaAiClient`). Transient network blips are retried once automatically.

---

## 4. Build & test (local)

```bash
# Debug APK
./gradlew :app:assembleDebug

# Run unit tests (incl. AiRouter failover tests)
./gradlew :app:testDebugUnitTest

# Release APK (signed — see §5)
./gradlew :app:assembleRelease
```

First run downloads Gradle 8.13 + dependencies automatically (needs internet).

---

## 5. Release signing

The repo contains a fixed release keystore:

```
release.keystore        (binary keystore)
keystore.properties     (storeFile / passwords / alias)
```

`app/build.gradle.kts` reads `keystore.properties` automatically when present,
so `./gradlew :app:assembleRelease` produces a **signed** APK out of the box.

> 🔒 **Security warning:** `keystore.properties` currently holds plaintext
> passwords in git. For a production release, move signing to GitHub secrets
> (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
> `ANDROID_KEY_PASSWORD`) — the CI workflow already supports both paths.
> Anyone with this keystore can sign updates as you — keep it safe.

---

## 6. CI / automation

| Workflow | Trigger | What it does |
|---|---|---|
| `release-apk.yml` | push to `main`, tags `v*`, manual | Runs unit tests, builds **signed** release APK, publishes a GitHub Release |
| `build-check.yml` | every pull request | Compiles + runs unit tests (debug) so breakage is caught **before merge** |
| `generate-fixed-keystore.yml` | manual | (Re)generates the fixed release keystore |

PR builds use a **stub** `google-services.json` when the real secret isn't
available (e.g. forks) — enough to compile and run unit tests, never shipped.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `google-services.json file not found` | Place the file at `app/google-services.json` (§2) |
| `SDK location not found` | Set `ANDROID_HOME` / `sdk.dir` in `local.properties` (Android Studio does this automatically) |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| Saira shows "limit শেষ" message | Gemini free quota exhausted → add a Backup API key in Saira settings (§3), or wait ~5 min cooldown |
| CI fails: `app/google-services.json is missing` | Add the `GOOGLE_SERVICES_JSON` secret (§2) |
| APK SHA-1 mismatch in CI | The release keystore changed — CI expects the fixed fingerprint; restore the original `release.keystore` |

---

## 8. Project layout (quick map)

```
app/src/main/java/com/example/
├── data/ai/          # Saira AI: AiRouter (failover), providers, safety net
├── saira/            # Notification listener + auto-reply
├── ui/screens/       # Compose screens (SairaAiScreen, etc.)
└── ui/viewmodel/     # ChatboxAppViewModel
app/src/test/         # Unit tests (AiRouterTest, …)
.github/workflows/    # CI pipelines
gradle/wrapper/       # Gradle 8.13 wrapper (pinned)
```
