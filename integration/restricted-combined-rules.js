// Synthetic accounts, local emulator only; not a live-project security review.
import {readFileSync} from 'node:fs';
import {after,before,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,serverTimestamp} from 'firebase/firestore';
import {restrictedCombinedTestRules} from '../scripts/generate-isolated-combined-rules.mjs';
let env;
const auth = (uid,email) => env.authenticatedContext(uid,{email,email_verified:true,auth_time:Math.floor(Date.now()/1000),firebase:{sign_in_provider:'google.com'}}).firestore();
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Local emulator required.');
  const rules=restrictedCombinedTestRules(readFileSync('firestore.combined-experimental.rules','utf8'),'alice','bob');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules}});
  await env.clearFirestore();
});
after(async()=>{if(env)await env.cleanup();});
test('two allowed accounts can exercise admin, chat and signaling; outsiders cannot',async()=>{
  const owner=auth('alice','ishtiakahmedru@gmail.com'), bob=auth('bob','bob@gmail.com');
  const eve=auth('eve','ishtiakahmedru@gmail.com'), anonymous=env.unauthenticatedContext().firestore();
  const admin=db=>doc(db,'sparkAdmin','current');
  const ownerRecord={uid:'alice',email:'ishtiakahmedru@gmail.com',pendingEmail:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  const outsiderRecord={...ownerRecord,uid:'eve'};
  await assertFails(setDoc(admin(eve),outsiderRecord));
  await assertFails(setDoc(admin(anonymous),ownerRecord));
  await assertSucceeds(setDoc(admin(owner),ownerRecord));
  await assertFails(getDoc(admin(bob))); await assertFails(getDoc(admin(eve)));
  await assertSucceeds(setDoc(doc(owner,'settings','sparkTheme'),{value:'lavender',updatedBy:'alice',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(bob,'settings','sparkTheme'),{value:'default',updatedBy:'bob',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(eve,'settings','sparkTheme'),{value:'default',updatedBy:'eve',updatedAt:serverTimestamp()}));
  // Public settings are an explicit exception; the UID gate does not hide them.
  await assertSucceeds(getDoc(doc(anonymous,'settings','sparkTheme')));
  for (const [db,uid] of [[owner,'alice'],[bob,'bob']]) await assertSucceeds(setDoc(doc(db,'profiles',uid),{uid,displayName:uid}));
  await assertFails(setDoc(doc(eve,'profiles','eve'),{uid:'eve',displayName:'Eve'}));
  const conversation=db=>doc(db,'conversations','pair');
  await assertSucceeds(setDoc(conversation(owner),{participants:['alice','bob'],createdBy:'alice',status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(conversation(bob),{status:'active',updatedAt:serverTimestamp()}));
  const message=db=>doc(db,'conversations','pair','messages','one');
  await assertSucceeds(setDoc(message(owner),{senderId:'alice',body:'hello',sentAt:serverTimestamp()}));
  await assertSucceeds(getDoc(message(bob))); await assertFails(getDoc(message(eve)));
  const call=db=>doc(db,'conversations','pair','calls','one');
  await assertSucceeds(setDoc(call(owner),{callerId:'alice',receiverId:'bob',media:'audio',offer:{type:'offer',sdp:'synthetic'},status:'ringing',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(getDoc(call(bob))); await assertFails(getDoc(call(eve)));
  await assertSucceeds(updateDoc(call(bob),{status:'accepted',answer:{type:'answer',sdp:'synthetic'},updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(call(eve),{status:'ended',updatedAt:serverTimestamp()}));
});
