// LOCAL emulator only; generated rules for an isolated two-account lab.
import {readFileSync} from 'node:fs';
import {after,before,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,collection,getDoc,getDocs,setDoc,updateDoc,serverTimestamp} from 'firebase/firestore';
import {restrictedTestRules} from '../scripts/generate-isolated-rules.mjs';
let env;
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local emulator required.');
  const rules = restrictedTestRules(readFileSync('firestore.rules','utf8'),'lab-alice','lab-bob');
  env = await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules}});
  await env.clearFirestore();
});
after(async () => { if (env) await env.cleanup(); });
test('only the two test identities can use the base chat candidate', async () => {
  const alice=env.authenticatedContext('lab-alice').firestore();
  const bob=env.authenticatedContext('lab-bob').firestore();
  const outsider=env.authenticatedContext('lab-outsider').firestore();
  const anonymous=env.unauthenticatedContext().firestore();
  await assertSucceeds(setDoc(doc(alice,'profiles','lab-alice'),{uid:'lab-alice',displayName:'Alice'}));
  await assertSucceeds(setDoc(doc(bob,'profiles','lab-bob'),{uid:'lab-bob',displayName:'Bob'}));
  await assertFails(setDoc(doc(outsider,'profiles','lab-outsider'),{uid:'lab-outsider',displayName:'Outsider'}));
  await assertFails(getDoc(doc(outsider,'profiles','lab-alice')));
  await assertFails(getDoc(doc(anonymous,'profiles','lab-alice')));
  await assertFails(getDocs(collection(outsider,'conversations')));
  const conversation = {participants:['lab-alice','lab-bob'],createdBy:'lab-alice',status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await assertSucceeds(setDoc(doc(alice,'conversations','lab'),conversation));
  // The initiator must not accept their own request or write before acceptance.
  await assertFails(updateDoc(doc(alice,'conversations','lab'),{status:'active',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(alice,'conversations','lab','messages','early'),{senderId:'lab-alice',body:'Too early',sentAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(bob,'conversations','lab'),{status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(alice,'conversations','lab','messages','hello'),{senderId:'lab-alice',body:'Hello',sentAt:serverTimestamp()}));
  await assertSucceeds(getDoc(doc(bob,'conversations','lab','messages','hello')));
  await assertSucceeds(setDoc(doc(bob,'conversations','lab','messages','reply'),{senderId:'lab-bob',body:'Hi',sentAt:serverTimestamp()}));
  await assertSucceeds(getDoc(doc(alice,'conversations','lab','messages','reply')));
  // An outsider cannot read or send, even if they claim an allowed sender ID.
  await assertFails(getDoc(doc(outsider,'conversations','lab','messages','hello')));
  await assertFails(setDoc(doc(outsider,'conversations','lab','messages','no'),{senderId:'lab-outsider',body:'No',sentAt:serverTimestamp()}));
  await assertFails(setDoc(doc(outsider,'conversations','lab','messages','forged'),{senderId:'lab-alice',body:'No',sentAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(bob,'conversations','lab','messages','hello'),{body:'Altered'}));
  await assertSucceeds(updateDoc(doc(bob,'conversations','lab'),{status:'closed',updatedAt:serverTimestamp()}));
  await assertSucceeds(getDoc(doc(alice,'conversations','lab','messages','reply')));
  await assertFails(setDoc(doc(alice,'conversations','lab','messages','late'),{senderId:'lab-alice',body:'Too late',sentAt:serverTimestamp()}));
});
