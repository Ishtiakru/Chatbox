#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_FILE="${1:-chatbox-release.jks}"
KEY_ALIAS="${2:-chatbox}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Error: Java keytool is required. Install JDK 17+ and re-run."
  exit 1
fi

if [ -f "$KEYSTORE_FILE" ]; then
  echo "Reusing existing keystore: $KEYSTORE_FILE"
else
  echo "Generating ONE permanent release keystore: $KEYSTORE_FILE"
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000
fi

echo ""
echo "=== PERMANENT FINGERPRINTS (Add these ONCE to Firebase Console & .env ANDROID_SHA256_FINGERPRINT) ==="
keytool -list -v -keystore "$KEYSTORE_FILE" -alias "$KEY_ALIAS" | grep -E "SHA1:|SHA256:"

echo ""
echo "=== Base64 for GitHub Secret ANDROID_KEYSTORE_BASE64 ==="
if base64 --help 2>&1 | grep -q -- "-w"; then
  base64 -w 0 "$KEYSTORE_FILE"
else
  base64 -i "$KEYSTORE_FILE" | tr -d '\n'
fi
echo ""
