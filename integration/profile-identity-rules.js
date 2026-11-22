// Local emulator only: synthetic identities, no deployed services.
import {readFileSync} from 'node:fs';
import {before, after, beforeEach, test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,collection,getDoc,getDocs,setDoc,writeBatch,runTransaction} from 'firebase/firestore';
import {profileIdentityRules} from '../scripts/generate-profile-identity-rules.mjs';
import {claimContactCode} from '../src/profile-identity.js';
let env;
const as=(uid,verified=true)=>env.authenticatedContext(uid,{email_verified:verified}).firestore();
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Emulator required.');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:profileIdentityRules(readFileSync('firestore.combined-experimental.rules','utf8'))}});
});
beforeEach(async()=>env.clearFirestore());
after(async()=>{if(env)await env.cleanup();});
const refs=(db,uid,code)=>({profile:doc(db,'identityProfiles',uid),code:doc(db,'identityCodes',code)});
const claim=(db,uid,code)=>claimContactCode({uid,verified:true,guard:{run:f=>f()},transact:f=>runTransaction(db,f),profileRef:id=>doc(db,'identityProfiles',id),codeRef:id=>doc(db,'identityCodes',id),newCode:()=>code,attempts:1});
test('only verified users can atomically claim one unique six-digit contact code',async()=>{
  const a=as('alice'),b=as('bob'),anon=env.unauthenticatedContext().firestore(),unverified=as('eve',false);
  await assertSucceeds(claim(a,'alice','000123'));
  await assertSucceeds(getDoc(refs(a,'alice','000123').profile));
  await assertFails(getDoc(refs(b,'alice','000123').profile));
  await assertSucceeds(getDoc(refs(b,'alice','000123').code)); // exact lookups reveal an existing code; enumeration risk
  await assertFails(getDocs(collection(b,'identityCodes')));
  await assertFails(getDoc(refs(anon,'alice','000123').code));
  await assertFails(getDoc(refs(unverified,'alice','000123').code));
  const unverifiedBatch=writeBatch(unverified); unverifiedBatch.set(refs(unverified,'eve','555555').profile,{uid:'eve',code:'555555'}); unverifiedBatch.set(refs(unverified,'eve','555555').code,{uid:'eve',code:'555555'}); await assertFails(unverifiedBatch.commit());
  const newline=writeBatch(b); newline.set(refs(b,'bob','123456\n').profile,{uid:'bob',code:'123456\n'}); newline.set(refs(b,'bob','123456\n').code,{uid:'bob',code:'123456\n'}); await assertFails(newline.commit());
  await assertFails(setDoc(refs(b,'alice','000123').code,{uid:'bob',code:'000123'}));
  await assertFails(setDoc(refs(a,'alice','000123').profile,{uid:'alice',code:'111111'}));
  const bad=writeBatch(b); bad.set(refs(b,'bob','12345').profile,{uid:'bob',code:'12345'}); bad.set(refs(b,'bob','12345').code,{uid:'bob',code:'12345'}); await assertFails(bad.commit());
  const mismatched=writeBatch(b); mismatched.set(refs(b,'bob','222222').profile,{uid:'bob',code:'222222'}); mismatched.set(refs(b,'bob','333333').code,{uid:'bob',code:'333333'}); await assertFails(mismatched.commit());
  await assertFails(setDoc(refs(b,'bob','222222').profile,{uid:'bob',code:'222222'}));
  await assertSucceeds(claim(b,'bob','222222'));
});
