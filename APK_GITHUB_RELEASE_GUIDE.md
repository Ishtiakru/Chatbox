# Chatbox — Fixed-SHA Android APK & GitHub Release Guide

## Why did SHA / Google Login break on updates before?
1. When an Android project is rebuilt without a **permanent release keystore**, Gradle or a new machine generates a brand-new `debug.keystore` every time, which **changes the SHA-1 and SHA-256**.
2. Once the SHA changes, Firebase Google Sign-In rejects the login and drops back to the login screen.
3. This native Android app (`com.aistudio.chatbox.kxmpzq`) uses Jetpack `CredentialManager` Google Sign-In and keeps your debug & release keystores fixed forever.

## One-Time Setup (SHA Will NEVER Change Again)
1. **Generate ONE permanent release keystore** (once only) via GitHub Actions (`Actions -> Generate Permanent Keystore Once -> Run workflow`) or locally with JDK 21:
   ```bash
   bash scripts/setup-fixed-keystore.sh chatbox-release.jks chatbox
   ```
2. **Register the permanent SHA-1 & SHA-256 ONCE**:
   - Add SHA-1 and SHA-256 in **Firebase Console → Project Settings → Your Android App (`com.aistudio.chatbox.kxmpzq`)**.
   - As long as every release APK is signed with this same keystore, SHA-1 and SHA-256 never change across unlimited app updates.
3. **Add GitHub Repository Secrets** (`Settings → Secrets and variables → Actions`):
   - `ANDROID_KEYSTORE_BASE64` — base64 output of your `.jks` keystore
   - `ANDROID_KEYSTORE_PASSWORD` — keystore password
   - `ANDROID_KEY_ALIAS` — `chatbox`
   - `ANDROID_KEY_PASSWORD` — key password
4. **Release Signed APK on GitHub Automatically**:
   - Push this repository to GitHub (via AI Studio's **Push to GitHub** menu or `git push`).
   - Pushing to `main`, pushing a version tag (`v*`), or running **Actions → Build Signed Chatbox APK & Publish GitHub Release → Run workflow** automatically runs `:app:testDebugUnitTest`, compiles `:app:assembleRelease` signed with the fixed keystore, and publishes `chatbox-v<version>.apk` to **GitHub Releases**.
