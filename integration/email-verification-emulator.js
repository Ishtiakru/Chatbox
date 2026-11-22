// Synthetic local-only end-to-end experiment: no real email is sent.
// Never run this against a real Firebase project or personal account.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, sendEmailVerification, applyActionCode, reload, signOut, signInWithEmailAndPassword } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, writeBatch, getDoc, terminate } from 'firebase/firestore';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { profileIdentityRules } from '../scripts/generate-profile-identity-rules.mjs';

const project = 'demo-chatbox';
if (process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9099' || process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') {
  throw new Error('Both demo emulators must be running on local fixed ports.');
}

test('email link verifies a synthetic account before identity reservation', async () => {
  const rules = await initializeTestEnvironment({projectId:project, firestore:{rules:profileIdentityRules(readFileSync('firestore.combined-experimental.rules','utf8'))}});
  const app = initializeApp({apiKey:'demo-key', authDomain:'demo-chatbox.firebaseapp.com', projectId:project}, `verification-${Date.now()}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', {disableWarnings:true});
  const db = getFirestore(app);
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  try {
    const email = `test-${Date.now()}-${Math.random().toString(36).slice(2)}@example.invalid`;
    const password = 'local-test-password-not-a-real-secret';
    const {user} = await createUserWithEmailAndPassword(auth,email,password);
    assert.equal(user.emailVerified,false);
    const uid = user.uid;
    const pair = () => {
      const batch = writeBatch(db);
      batch.set(doc(db,'identityProfiles',uid),{uid,code:'123456'});
      batch.set(doc(db,'identityCodes','123456'),{uid,code:'123456'});
      return batch.commit();
    };
    await assertFails(pair());
    await sendEmailVerification(user);
    // Emulator API exposes synthetic verification codes only; no email is delivered.
    const response = await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${project}/oobCodes`);
    assert.equal(response.ok,true);
    const body = await response.json();
    const message = body.oobCodes?.find(item => item.email === email && item.requestType === 'VERIFY_EMAIL');
    assert.ok(message?.oobCode);
    await applyActionCode(auth,message.oobCode);
    await assert.rejects(applyActionCode(auth,message.oobCode));
    await reload(user);
    assert.equal(user.emailVerified,true);
    await user.getIdToken(true);
    await assertSucceeds(pair());
    assert.equal((await getDoc(doc(db,'identityProfiles',uid))).data().code,'123456');
    await signOut(auth);
    await assertFails(getDoc(doc(db,'identityCodes','123456')));
    const again = await signInWithEmailAndPassword(auth,email,password);
    assert.equal(again.user.uid,uid);
    assert.equal(again.user.emailVerified,true);
  } finally {
    await signOut(auth).catch(()=>{});
    await terminate(db);
    await deleteApp(app);
    await rules.cleanup();
  }
});
