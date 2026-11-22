#!/usr/bin/env bash
# Installs Android's command-line SDK in an isolated Cloud Shell directory.
# SDK license acceptance is interactive; never auto-accept on behalf of the user.
set -euo pipefail
SDK_DIR="$HOME/chatbox-android-sdk"
TOOLS="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
VERSION=15859902
EXPECTED=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
command -v java >/dev/null || { echo 'Java is required.' >&2; exit 1; }
command -v curl >/dev/null || { echo 'curl is required.' >&2; exit 1; }
command -v unzip >/dev/null || { echo 'unzip is required.' >&2; exit 1; }
mkdir -p "$SDK_DIR/cmdline-tools"
if [ ! -x "$TOOLS" ]; then
  if [ -e "$SDK_DIR/cmdline-tools/latest" ]; then
    echo "The SDK directory already exists but has no sdkmanager: $SDK_DIR/cmdline-tools/latest" >&2
    exit 1
  fi
  TMP_DIR=$(mktemp -d)
  trap 'rm -rf "$TMP_DIR"' EXIT
  curl --fail --location --retry 2 "https://dl.google.com/android/repository/commandlinetools-linux-${VERSION}_latest.zip" -o "$TMP_DIR/tools.zip"
  printf '%s  %s\n' "$EXPECTED" "$TMP_DIR/tools.zip" | sha256sum --check
  unzip -q "$TMP_DIR/tools.zip" -d "$TMP_DIR/unpacked"
  mv "$TMP_DIR/unpacked/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi
export ANDROID_HOME="$SDK_DIR" ANDROID_SDK_ROOT="$SDK_DIR"
echo 'Please read the Android SDK licenses and accept only if you agree.'
"$TOOLS" --sdk_root="$SDK_DIR" --licenses
"$TOOLS" --sdk_root="$SDK_DIR" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
printf '\nAndroid SDK installed at %s\n' "$SDK_DIR"
echo 'Installation complete. The app has not been built or deployed.'
