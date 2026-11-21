// Validation only: does not register an app, build an APK or access Firebase.
// A valid pair is necessary, but is not proof of signing, successful Google
// sign-in, live Firestore permissions or release readiness.
import { resolveFirebaseConfig } from '../src/firebase-config.js';

export const ISOLATED_ANDROID_PACKAGE = 'com.chatbox.test';

export function validateIsolatedAndroidPair(android, web, certificateSha1) {
  const { config } = resolveFirebaseConfig({
    VITE_CHATBOX_TEST_MODE: 'true',
    VITE_CHATBOX_TEST_FIREBASE_CONFIG: JSON.stringify(web),
  });
  const sha1 = typeof certificateSha1 === 'string' ? certificateSha1.replaceAll(':', '').toUpperCase() : '';
  if (!/^[0-9A-F]{40}$/.test(sha1)) throw new Error('A valid SHA-1 signing certificate fingerprint is required.');
  if (android?.project_info?.project_id !== config.projectId || String(android?.project_info?.project_number ?? '') !== config.messagingSenderId) {
    throw new Error('The Android and browser Firebase project identifiers do not match.');
  }
  const clients = android?.client;
  if (!Array.isArray(clients)) throw new Error('The Android Firebase client list is missing.');
  const matching = clients.filter(c => c?.client_info?.android_client_info?.package_name === ISOLATED_ANDROID_PACKAGE);
  if (matching.length !== 1) throw new Error(`Exactly one ${ISOLATED_ANDROID_PACKAGE} Android client is required.`);
  const client = matching[0];
  const id = client?.client_info?.mobilesdk_app_id;
  if (typeof id !== 'string' || !new RegExp(`^1:${config.messagingSenderId}:android:[a-f0-9]+$`).test(id)) {
    throw new Error('The Android app ID does not match the test project number.');
  }
  if (!Array.isArray(client.api_key) || !client.api_key.some(k => typeof k?.current_key === 'string' && k.current_key.trim())) {
    throw new Error('The Android Firebase API key is missing.');
  }
  const oauth = client.oauth_client;
  if (!Array.isArray(oauth) || !oauth.some(c => c?.client_type === 3 && typeof c.client_id === 'string' && c.client_id.trim())) {
    throw new Error('The test project web OAuth client is missing.');
  }
  if (!oauth.some(c => c?.client_type === 1 && typeof c.client_id === 'string' && c.client_id.trim()
      && c.android_info?.package_name === ISOLATED_ANDROID_PACKAGE
      && typeof c.android_info?.certificate_hash === 'string'
      && c.android_info.certificate_hash.replaceAll(':', '').toUpperCase() === sha1)) {
    throw new Error('The Android OAuth client does not match the test package and signing certificate.');
  }
  return true;
}
