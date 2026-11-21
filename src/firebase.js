import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signInWithCredential, reauthenticateWithPopup, reauthenticateWithCredential, signOut } from 'firebase/auth';
import { Capacitor } from '@capacitor/core';
import { createGoogleReauth } from './google-reauth.js';

// Firebase web configuration identifies the project; it is not an admin credential.
// Authorization must be enforced by Firebase rules and trusted backend code.
const firebaseConfig = {
  apiKey: 'AIzaSyAFc4L8Mo8oI8yN2jgQsVSti2xiYJbDTYU',
  authDomain: 'chatbox-afb8c.firebaseapp.com',
  projectId: 'chatbox-afb8c',
  storageBucket: 'chatbox-afb8c.firebasestorage.app',
  messagingSenderId: '1030482946047',
  appId: '1:1030482946047:web:4685bcc689904a658229ca',
  measurementId: 'G-F4661XK99F',
};

export const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();
export const watchAuth = (callback, onError) => onAuthStateChanged(auth, callback, onError);
export const isCurrentAuthUser = user => !!user && auth.currentUser === user;
async function nativeGoogleCredential() {
  const { FirebaseAuthentication } = await import('@capacitor-firebase/authentication');
  const result = await FirebaseAuthentication.signInWithGoogle({ skipNativeAuth: true });
  const idToken = result.credential?.idToken;
  if (!idToken) throw new Error('Native Google sign-in did not return an ID token.');
  return GoogleAuthProvider.credential(idToken, result.credential?.accessToken);
}
export async function googleSignIn() {
  if (!Capacitor.isNativePlatform()) return signInWithPopup(auth, provider);
  return signInWithCredential(auth, await nativeGoogleCredential());
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
