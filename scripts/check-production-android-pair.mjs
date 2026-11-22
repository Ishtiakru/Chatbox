// Only public config and a public certificate fingerprint are supplied here;
// never pass a keystore or password as command arguments.
import {readFileSync} from 'node:fs';
import {validateProductionAndroidPair} from './production-android-pair.mjs';
try {
  if (process.argv.length !== 4) throw new Error('Usage: node scripts/check-production-android-pair.mjs ANDROID_JSON RELEASE_CERT_SHA1');
  const config = JSON.parse(readFileSync(process.argv[2], 'utf8'));
  validateProductionAndroidPair(config, process.argv[3]);
  console.log('Production Firebase config and release certificate fingerprint match locally. This is not a release, deployment, security review, or proof of device sign-in.');
} catch (error) {
  console.error('Production Android preflight failed:', error instanceof SyntaxError ? 'Invalid JSON.' : error.message);
  process.exitCode = 1;
}
