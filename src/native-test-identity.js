import {resolveFirebaseConfig} from './firebase-config.js';

// The native auth gate calls this validator before starting auth in isolated
// test builds. It is not an authorization boundary or a substitute for
// signing checks, device validation, or reviewed Firebase Security Rules.
export function verifyNativeTestIdentity(web, nativeIdentity) {
  const {config} = resolveFirebaseConfig({
    VITE_CHATBOX_TEST_MODE: 'true',
    VITE_CHATBOX_TEST_FIREBASE_CONFIG: JSON.stringify(web),
  });
  if (!nativeIdentity || nativeIdentity.debuggable !== true || nativeIdentity.packageName !== 'com.chatbox.test') {
    throw new Error('A separate debuggable Android test package is required.');
  }
  if (nativeIdentity.projectId !== config.projectId || nativeIdentity.senderId !== config.messagingSenderId) {
    throw new Error('Native and web Firebase project identities do not match.');
  }
  if (typeof nativeIdentity.androidAppId !== 'string'
      || !new RegExp(`^1:${config.messagingSenderId}:android:[a-f0-9]+$`).test(nativeIdentity.androidAppId)) {
    throw new Error('The native Firebase app ID does not match the test project.');
  }
  return true;
}
