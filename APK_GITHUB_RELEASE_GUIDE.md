# Chatbox — Fixed-SHA Android APK & GitHub Release Guide

## Why did SHA / Google Login break on updates before?
1. When an Android project is rebuilt without a **permanent release keystore**, Gradle or a new machine generates a brand-new `debug.keystore` every time, which **changes the SHA-1 and SHA-256**.
2. Once the SHA changes, Firebase Google Sign-In rejects the login and drops back to the login screen.
3. Also, embedded Android `WebView` blocks Google OAuth (`403 disallowed_useragent`), whereas **Trusted Web Activity (TWA) / Chrome Custom Tabs** uses real Chrome auth + `/.well-known/assetlinks.json`.

## One-Time Setup (SHA Will NEVER Change Again)
1. **Generate ONE permanent keystore** (once only) on a machine with Java JDK installed:
   ```bash
   bash scripts/setup-fixed-keystore.sh chatbox-release.jks chatbox
   ```
2. **Copy the printed SHA-1 & SHA-256** (once only):
   - Add SHA-1 and SHA-256 in **Firebase Console → Project Settings → Your Android App (`com.chatbox.saira`)**.
   - Set `ANDROID_SHA256_FINGERPRINT=<your-SHA256>` in the Chatbox server environment (served automatically at `/.well-known/assetlinks.json`).
   - Keep `chatbox-release.jks` backed up safely — **as long as you sign with this same keystore, SHA-1 and SHA-256 never change across unlimited app updates.**
3. **Add GitHub Repository Secrets** in your GitHub repo (`Settings → Secrets and variables → Actions`):
   - `ANDROID_KEYSTORE_BASE64` — base64 output from step 1
   - `ANDROID_KEYSTORE_PASSWORD` — your keystore password
   - `ANDROID_KEY_ALIAS` — `chatbox`
   - `ANDROID_KEY_PASSWORD` — your key password
4. **Release APK on GitHub automatically**:
   - Push this codebase (or upload the ZIP from **Admin → QE Coder**) to your GitHub repository.
   - Push a tag like `git tag v1.0.1 && git push origin v1.0.1` OR trigger **Actions → Build Signed Chatbox APK & Publish GitHub Release → Run workflow**.
   - GitHub Actions will automatically compile, sign with the fixed keystore, and publish `chatbox-v1.0.1.apk` under **GitHub Releases**.
