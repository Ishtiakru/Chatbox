import { readFileSync } from 'node:fs';
import { loadEnv } from 'vite';
import { validateAndroidFirebaseConfig } from './android-config.mjs';
try {
  // The sync command builds with Vite's production mode. Check both env files
  // and process variables so a browser-only test config cannot enter an APK.
  const env = loadEnv('production', process.cwd(), 'VITE_');
  if (env.VITE_CHATBOX_TEST_MODE === 'true' || env.VITE_CHATBOX_TEST_FIREBASE_CONFIG) throw new Error('Test Firebase builds are browser-only; Android sync is disabled.');
  const config = JSON.parse(readFileSync('android/app/google-services.json', 'utf8'));
  validateAndroidFirebaseConfig(config);
  console.log('Android Firebase project, package and web OAuth client are present. SHA fingerprints and device sign-in still need verification.');
} catch (error) {
  console.error('Android Firebase setup incomplete:', error.message);
  process.exitCode = 1;
}
