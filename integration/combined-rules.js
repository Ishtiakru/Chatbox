// Test combined experimental policies together. Never connects to production.
import {readFileSync} from 'node:fs';
import {after,before,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc,serverTimestamp} from 'firebase/firestore';
let env;
before(async()=>{
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use a Firestore emulator.');
  env=await initializeTestEnvironment({projectId:'demo-chatbox',firestore:{rules:readFileSync('firestore.combined-experimental.rules','utf8')}});
  await env.clearFirestore();
});
after(async()=>{if(env)await env.cleanup();});
test('admin is not a chat or call participant and participants do not inherit admin privileges',async()=>{
  const admin=env.authenticatedContext('admin',{email:'ishtiakahmedru@gmail.com',email_verified:true,auth_time:Math.floor(Date.now()/1000),firebase:{sign_in_provider:'google.com'}}).firestore();
  const alice=env.authenticatedContext('alice').firestore(),bob=env.authenticatedContext('bob').firestore();
  const delegate=env.authenticatedContext('delegate',{email:'delegate@gmail.com',email_verified:true,auth_time:Math.floor(Date.now()/1000),firebase:{sign_in_provider:'google.com'}}).firestore();
  await assertSucceeds(setDoc(doc(admin,'sparkAdmin','current'),{uid:'admin',email:'ishtiakahmedru@gmail.com',pendingEmail:'',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(admin,'sparkDelegations','delegate@gmail.com'),{email:'delegate@gmail.com',uid:'',invitedBy:'admin',status:'invited',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(delegate,'sparkDelegations','delegate@gmail.com'),{uid:'delegate',status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(delegate,'settings','sparkPublic'),{welcome:'Delegate',updatedBy:'delegate',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(admin,'settings','sparkPublic'),{welcome:'Hello',updatedBy:'admin',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(alice,'profiles','alice'),{uid:'alice',displayName:'Alice'}));
  await assertSucceeds(setDoc(doc(bob,'profiles','bob'),{uid:'bob',displayName:'Bob'}));
  await assertSucceeds(setDoc(doc(alice,'conversations','pair'),{participants:['alice','bob'],createdBy:'alice',status:'pending',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(bob,'conversations','pair'),{status:'active',updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(alice,'conversations','pair','messages','one'),{senderId:'alice',body:'hello',sentAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(alice,'conversations','pair','calls','one'),{callerId:'alice',receiverId:'bob',media:'audio',offer:{type:'offer',sdp:'test'},status:'ringing',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
  await assertSucceeds(getDoc(doc(bob,'conversations','pair','messages','one')));
  await assertSucceeds(getDoc(doc(bob,'conversations','pair','calls','one')));
  await assertSucceeds(setDoc(doc(alice,'userBlocks','alice','blocked','bob'),{createdAt:serverTimestamp()}));
  await assertFails(getDoc(doc(admin,'userBlocks','alice','blocked','bob')));
  await assertFails(getDoc(doc(delegate,'userBlocks','alice','blocked','bob')));
  await assertFails(getDoc(doc(admin,'conversations','pair','messages','one')));
  await assertFails(getDoc(doc(admin,'conversations','pair','calls','one')));
  await assertFails(getDoc(doc(delegate,'conversations','pair','messages','one')));
  await assertFails(getDoc(doc(delegate,'conversations','pair','calls','one')));
  await assertFails(getDoc(doc(alice,'sparkAdmin','current')));
  await assertFails(setDoc(doc(alice,'settings','sparkPublic'),{welcome:'Injected',updatedBy:'alice',updatedAt:serverTimestamp()}));
});
