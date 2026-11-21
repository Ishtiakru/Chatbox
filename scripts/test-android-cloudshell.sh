#!/usr/bin/env bash
# Diagnostic debug build only. It does not deploy or produce a release-ready app.
set -euo pipefail
cd "$(dirname "$0")/.."
trap 'echo "Android diagnostic build failed. See the error above." >&2' ERR
export ANDROID_HOME="$HOME/chatbox-android-sdk" ANDROID_SDK_ROOT="$HOME/chatbox-android-sdk"
[[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]] || { echo 'Android SDK was not found.' >&2; exit 1; }
[[ -f "$HOME/chatbox-firebase/google-services.json" ]] || { echo 'Firebase Android configuration is missing.' >&2; exit 1; }
[[ -f "$HOME/.android/debug.keystore" ]] || { echo 'Debug keystore is missing.' >&2; exit 1; }
command -v java >/dev/null && command -v node >/dev/null && command -v npm >/dev/null
cp "$HOME/chatbox-firebase/google-services.json" android/app/google-services.json
# Never accidentally enable undeployed or unreviewed features in this build.
export VITE_ENABLE_FIRESTORE_CHAT=false VITE_ENABLE_EXPERIMENTAL_CALLS=false VITE_ENABLE_EXPERIMENTAL_BLOCKING=false VITE_ENABLE_EXPERIMENTAL_MESSAGE_COOLDOWN=false VITE_ENABLE_EXPERIMENTAL_REQUEST_COOLDOWN=false VITE_ENABLE_SAIRA_AI=false
export VITE_ENABLE_SPARK_ADMIN=false VITE_ENABLE_ADMIN_BACKEND=false
export CI=true
printf 'Building an incomplete diagnostic prototype; this is not a release.\n'
df -h "$HOME"
npm ci --no-audit --no-fund
npm run android:sync
(cd android && ./gradlew --no-daemon --max-workers=2 assembleDebug)
APK=android/app/build/outputs/apk/debug/app-debug.apk
[[ -f "$APK" ]] || { echo 'Gradle reported success, but no APK was found.' >&2; exit 1; }
printf '\nDiagnostic APK (not a release): %s/%s\n' "$PWD" "$APK"
sha256sum "$APK"
printf 'Build only; no deployment or device test was performed.\n'
