// Additional candidate; no production rule or resource is modified.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
import assert from 'node:assert/strict';
import { createSparkAdminService } from '../src/spark-admin-data.js';
let env;
const claims = email => ({email, email_verified: true, firebase: {sign_in_provider: 'google.com'}, auth_time: Math.floor(Date.now()/1000)});
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use an emulator.');
  env = await initializeTestEnvironment({projectId:'demo-chatbox', firestore:{rules:readFileSync(process.env.CHATBOX_TEST_RULES_FILE || 'firestore.spark-source-experimental.rules','utf8')}});
});
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { if (env) await env.cleanup(); });
const as = (uid,email,opts={}) => env.authenticatedContext(uid,{...claims(email),...('verified' in opts ? {email_verified:opts.verified}:{}),...('provider' in opts ? {firebase:{sign_in_provider:opts.provider}}:{})}).firestore();
const bootstrap = (db,uid,email) => setDoc(doc(db,'sparkAdmin','current'),{uid,email,pendingEmail:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
const invite = (db,email) => setDoc(doc(db,'sparkDelegations',email),{email,uid:'',invitedBy:'owner',status:'invited',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
const accept = (db,email,uid) => updateDoc(doc(db,'sparkDelegations',email),{uid,status:'active',updatedAt:serverTimestamp()});
const revoke = (db,email) => updateDoc(doc(db,'sparkDelegations',email),{status:'revoked',updatedAt:serverTimestamp()});

test('private source handover is readable only by owner and active delegates; all client writes are denied', async () => {
  const owner=as('owner','ishtiakahmedru@gmail.com'), target=as('target','target@gmail.com'), outsider=as('outsider','other@gmail.com');
  const handover = db => doc(db,'sparkPrivate','sourceHandover');
  await bootstrap(owner,'owner','ishtiakahmedru@gmail.com');
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(handover(context.firestore()),{url:'https://drive.google.com/file/d/abcdefghij/view',commit:'a'.repeat(40)});
  });
  await assertSucceeds(getDoc(handover(owner)));
  assert.equal((await createSparkAdminService(owner).getSparkSourceHandover()).commit,'a'.repeat(40));
  await assertFails(getDoc(handover(env.unauthenticatedContext().firestore())));
  await assertFails(getDoc(handover(outsider)));
  await assertFails(getDoc(handover(as('owner','ishtiakahmedru@gmail.com',{verified:false}))));
  await assertFails(getDoc(handover(as('owner','ishtiakahmedru@gmail.com',{provider:'password'}))));
  await assertFails(getDocs(collection(owner,'sparkPrivate')));
  await assertFails(setDoc(handover(owner),{url:'https://evil.invalid'}));
  await assertFails(updateDoc(handover(owner),{url:'https://evil.invalid'}));
  await assertFails(deleteDoc(handover(owner)));
  await invite(owner,'target@gmail.com');
  await assertFails(getDoc(handover(target)));
  await accept(target,'target@gmail.com','target');
  await assertSucceeds(getDoc(handover(target)));
  await assertFails(setDoc(handover(target),{url:'https://evil.invalid'}));
  await assertFails(getDoc(handover(as('imposter','target@gmail.com'))));
  await revoke(owner,'target@gmail.com');
  await assertFails(getDoc(handover(target)));
  await env.withSecurityRulesDisabled(async context => deleteDoc(handover(context.firestore())));
  await assertFails(setDoc(handover(owner),{url:'https://evil.invalid'}));
});
