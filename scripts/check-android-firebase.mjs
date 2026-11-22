import { readFileSync } from 'node:fs';
import { loadEnv } from 'vite';
import { validateAndroidFirebaseConfig } from './android-config.mjs';
try {
  // Standard sync builds the production wrapper, not the isolated test variant.
  // Check both Vite env files and process variables to prevent configuration mixing.
  const env = loadEnv('production', process.cwd(), 'VITE_');
  if (env.VITE_CHATBOX_TEST_MODE === 'true' || env.VITE_CHATBOX_TEST_FIREBASE_CONFIG || env.VITE_CHATBOX_NATIVE_TEST_MODE === 'true') throw new Error('Test Firebase configuration cannot be used by the standard Android sync.');
  const config = JSON.parse(readFileSync('android/app/google-services.json', 'utf8'));
  validateAndroidFirebaseConfig(config);
  console.log('Android Firebase project, package and web OAuth client are present. SHA fingerprints and device sign-in still need verification.');
} catch (error) {
  console.error('Android Firebase setup incomplete:', error.message);
  process.exitCode = 1;
}
