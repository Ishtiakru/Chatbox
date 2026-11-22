// Synthetic accounts only. This does not deploy rules or verify Drive permissions.
import { readFileSync } from 'node:fs';
import { after, before, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';
import { restrictedCombinedSourceTestRules } from '../scripts/generate-isolated-combined-source-rules.mjs';
let env;
const as = (uid, email, extra={}) => env.authenticatedContext(uid, {email, email_verified:true, firebase:{sign_in_provider:'google.com'}, auth_time:Math.floor(Date.now()/1000), ...extra}).firestore();
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local emulator required.');
  const rules=restrictedCombinedSourceTestRules(readFileSync('firestore.combined-experimental.rules','utf8'),'alice','bob');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules}});
  await env.clearFirestore();
});
after(async () => { if (env) await env.cleanup(); });
test('source handover keeps two-account gate, owner/delegate authorization and denies client writes', async () => {
  const owner=as('alice','ishtiakahmedru@gmail.com'), delegate=as('bob','bob@gmail.com');
  const outsider=as('eve','ishtiakahmedru@gmail.com'), anon=env.unauthenticatedContext().firestore();
  const handover=db=>doc(db,'sparkPrivate','sourceHandover');
  await assertSucceeds(setDoc(doc(owner,'sparkAdmin','current'),{uid:'alice',email:'ishtiakahmedru@gmail.com',pendingEmail:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await env.withSecurityRulesDisabled(async context => setDoc(handover(context.firestore()),{url:'https://drive.google.com/file/d/abcdefghij/view',commit:'a'.repeat(40)}));
  await assertSucceeds(getDoc(handover(owner)));
  await assertFails(getDoc(handover(delegate))); await assertFails(getDoc(handover(outsider))); await assertFails(getDoc(handover(anon)));
  await assertFails(getDoc(handover(as('alice','ishtiakahmedru@gmail.com',{email_verified:false}))));
  await assertFails(getDocs(collection(owner,'sparkPrivate')));
  for (const db of [owner,delegate,outsider]) {
    await assertFails(setDoc(handover(db),{url:'https://evil.invalid'}));
    await assertFails(updateDoc(handover(db),{url:'https://evil.invalid'}));
    await assertFails(deleteDoc(handover(db)));
  }
  const delegation=db=>doc(db,'sparkDelegations','bob@gmail.com');
  await assertSucceeds(setDoc(delegation(owner),{email:'bob@gmail.com',uid:'',invitedBy:'alice',status:'invited',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertFails(getDoc(handover(delegate)));
  await assertSucceeds(updateDoc(delegation(delegate),{uid:'bob',status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(getDoc(handover(delegate)));
  await assertFails(getDoc(handover(as('eve','bob@gmail.com'))));
  await assertSucceeds(updateDoc(delegation(owner),{status:'revoked',updatedAt:serverTimestamp()}));
  await assertFails(getDoc(handover(delegate)));
  await assertSucceeds(setDoc(doc(owner,'profiles','alice'),{uid:'alice',displayName:'Alice'}));
  await assertSucceeds(setDoc(doc(delegate,'profiles','bob'),{uid:'bob',displayName:'Bob'}));
  await assertFails(setDoc(doc(outsider,'profiles','eve'),{uid:'eve',displayName:'Eve'}));
});
