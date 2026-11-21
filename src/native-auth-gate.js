import {verifyNativeTestIdentity} from './native-test-identity.js';

// The native resources and the web Firebase identity must agree before native
// auth listeners or Google sign-in are started. This is not an authorization
// boundary: Firestore rules and signing checks remain mandatory.
export function createNativeAuthGate({isNative, isTestProject, nativeTestOptIn, webConfig, getIdentity}) {
  let pending;
  return function ready() {
    if (!isNative) return Promise.resolve();
    if (!pending) pending = Promise.resolve().then(async () => {
      if (isTestProject !== nativeTestOptIn) throw new Error('Native test mode and Firebase project do not match.');
      const native = await getIdentity();
      if (isTestProject) {
        verifyNativeTestIdentity(webConfig, native);
      } else {
        if (!native || native.packageName !== 'com.chatbox.apk' || native.projectId !== webConfig.projectId
            || native.senderId !== webConfig.messagingSenderId || typeof native.androidAppId !== 'string'
            || !new RegExp(`^1:${webConfig.messagingSenderId}:android:[a-f0-9]+$`).test(native.androidAppId)) {
          throw new Error('Native Firebase configuration does not match this app.');
        }
      }
    });
    return pending;
  };
}

// Preserves the synchronous unsubscribe contract even if the bridge responds
// later. The listener is never created after unsubscribe or a failed check.
export function watchAfterNativeGate(ready, subscribe, callback, onError) {
  let cancelled = false, unsubscribe;
  Promise.resolve().then(ready).then(() => {
    if (!cancelled) unsubscribe = subscribe(callback, onError);
  }).catch(error => {
    if (!cancelled && onError) onError(error);
  });
  return () => {
    cancelled = true;
    if (unsubscribe) unsubscribe();
  };
}
