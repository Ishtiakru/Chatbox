// Offline release-certificate preflight only. It does not contact Firebase,
// prove key custody or validate rules, features, device sign-in or release readiness.
import { resolveFirebaseConfig } from '../src/firebase-config.js';

export function validateProductionAndroidPair(android, certificateSha1) {
  const {config} = resolveFirebaseConfig();
  const sha1 = typeof certificateSha1 === 'string' ? certificateSha1.replaceAll(':', '').toUpperCase() : '';
  if (!/^[0-9A-F]{40}$/.test(sha1)) throw new Error('A valid SHA-1 signing certificate fingerprint is required.');
  if (android?.project_info?.project_id !== config.projectId || String(android?.project_info?.project_number ?? '') !== config.messagingSenderId) {
    throw new Error('The Android Firebase project ID and sender number must match the production web config.');
  }
  const matches = Array.isArray(android.client) ? android.client.filter(c => c?.client_info?.android_client_info?.package_name === 'com.chatbox.apk') : [];
  if (matches.length !== 1) throw new Error('Exactly one com.chatbox.apk Android client is required.');
  const client = matches[0];
  const appId = client?.client_info?.mobilesdk_app_id;
  if (typeof appId !== 'string' || !new RegExp(`^1:${config.messagingSenderId}:android:[a-f0-9]+$`).test(appId)) throw new Error('The Android Firebase app ID does not match the production sender number.');
  if (!Array.isArray(client.api_key) || !client.api_key.some(k => typeof k?.current_key === 'string' && k.current_key.trim())) throw new Error('The Android Firebase API key is missing.');
  const oauth = client.oauth_client;
  if (!Array.isArray(oauth) || !oauth.some(c => c?.client_type === 3 && typeof c.client_id === 'string' && c.client_id.trim())) throw new Error('The Google web OAuth client is missing.');
  if (!oauth.some(c => c?.client_type === 1 && typeof c.client_id === 'string' && c.client_id.trim()
    && c.android_info?.package_name === 'com.chatbox.apk'
    && typeof c.android_info?.certificate_hash === 'string'
    && c.android_info.certificate_hash.replaceAll(':', '').toUpperCase() === sha1)) throw new Error('No Android OAuth client matches the release certificate and package. Register the release SHA-1 and download a fresh config.');
  return true;
}
