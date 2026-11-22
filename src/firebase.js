import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signInWithCredential, reauthenticateWithPopup, reauthenticateWithCredential, signOut } from 'firebase/auth';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { createGoogleReauth } from './google-reauth.js';
import { getNativeGoogleCredential, signInNative } from './native-sign-in.js';
import { createNativeAuthGate, watchAfterNativeGate } from './native-auth-gate.js';

import { resolveFirebaseConfig } from './firebase-config.js';

// A test build never silently falls back to the real project on bad input.
const {config: firebaseConfig, isTestProject} = resolveFirebaseConfig(import.meta.env);
const native = Capacitor.isNativePlatform();
const nativeIdentity = registerPlugin('NativeFirebaseIdentity');
const nativeReady = createNativeAuthGate({
  isNative: native,
  isTestProject,
  nativeTestOptIn: import.meta.env.VITE_CHATBOX_NATIVE_TEST_MODE === 'true',
  webConfig: firebaseConfig,
  getIdentity: () => nativeIdentity.getIdentity(),
});
export { isTestProject };
export const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();
export const watchAuth = (callback, onError) => native
  ? watchAfterNativeGate(nativeReady, (cb, err) => onAuthStateChanged(auth, cb, err), callback, onError)
  : onAuthStateChanged(auth, callback, onError);
export const isCurrentAuthUser = user => !!user && auth.currentUser === user;
async function nativeGoogleCredential(legacy = false) {
  return getNativeGoogleCredential({
    ready: nativeReady,
    getToken: async options => {
      const { FirebaseAuthentication } = await import('@capacitor-firebase/authentication');
      return FirebaseAuthentication.signInWithGoogle(options);
    },
    makeCredential: (idToken, accessToken) => GoogleAuthProvider.credential(idToken, accessToken),
    legacy,
  });
}
export async function googleSignIn({legacy = false} = {}) {
  if (!Capacitor.isNativePlatform()) return signInWithPopup(auth, provider);
  return signInNative({getCredential: () => nativeGoogleCredential(legacy), signIn: credential => signInWithCredential(auth, credential)});
}
// An explicit user gesture starts reauthentication; it does not grant a role.
// Firebase's reauthenticate methods reject credentials for another account.
export const googleReauthenticate = createGoogleReauth({
  getUser:()=>auth.currentUser,
  isNative:()=>Capacitor.isNativePlatform(),
  reauthPopup:user=>{
    const reauthProvider=new GoogleAuthProvider();
    reauthProvider.setCustomParameters({prompt:'select_account'});
    return reauthenticateWithPopup(user,reauthProvider);
  },
  getNativeCredential:nativeGoogleCredential,
  reauthCredential:reauthenticateWithCredential,
  refreshToken:user=>user.getIdToken(true),
});
export async function googleSignOut() {
  await signOut(auth);
  if (Capacitor.isNativePlatform()) {
    const { FirebaseAuthentication } = await import('@capacitor-firebase/authentication');
    await FirebaseAuthentication.signOut();
  }
}
