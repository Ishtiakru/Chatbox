// Local read-only preflight for a future isolated Android test build.
import {readFileSync} from 'node:fs';
import {validateIsolatedAndroidPair} from './isolated-android-config.mjs';

try {
  if (process.argv.length !== 5) throw new Error('Usage: node scripts/check-isolated-android-pair.mjs ANDROID_JSON WEB_JSON SIGNING_CERT_SHA1');
  const android = JSON.parse(readFileSync(process.argv[2], 'utf8'));
  const web = JSON.parse(readFileSync(process.argv[3], 'utf8'));
  validateIsolatedAndroidPair(android, web, process.argv[4]);
  console.log('Isolated Android/web configuration preflight passed. Verify the APK signing certificate and Google sign-in separately. No APK was built.');
} catch (error) {
  console.error('Isolated Android configuration preflight failed:', error instanceof SyntaxError ? 'Invalid JSON.' : error.message);
  process.exitCode = 1;
}
