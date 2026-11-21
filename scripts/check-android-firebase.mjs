import { readFileSync } from 'node:fs';
import { validateAndroidFirebaseConfig } from './android-config.mjs';
try {
  const config = JSON.parse(readFileSync('android/app/google-services.json', 'utf8'));
  validateAndroidFirebaseConfig(config);
  console.log('Android Firebase project, package and web OAuth client are present. SHA fingerprints and device sign-in still need verification.');
} catch (error) {
  console.error('Android Firebase setup incomplete:', error.message);
  process.exitCode = 1;
}
